import { Fragment, useEffect, useMemo, useState } from "react";
import { Focusable, Tooltip, TooltipTrigger } from "react-aria-components";
import { getContentRange, getRawRecordRange, getTraceRecords } from "../api/client";
import type { TraceRange, TraceSource } from "../api/contracts";
import type { TraceFailure, TraceFrame, TraceRecord } from "../api/contracts";
import { TraceEvidenceDetail } from "./TraceEvidenceDetail";
import { TraceAttemptDiagnostics } from "./TraceAttemptDiagnostics";
import { formatDuration } from "../duration";
import { recordContext } from "./recordContext";

type Props = { traceId?: string; source?: TraceSource; scopeGeneration?: number; verifyScope?: (response: TraceRange) => Promise<TraceRange>; records: TraceRecord[]; frames?: TraceFrame[]; failures: TraceFailure[]; selectedRecordSequence?: number; selectedFailureId?: string; activeContentRecordSequence?: number; contentRange?: TraceRange; contentPending?: boolean; contentError?: string; onSelectRecord: (record: TraceRecord) => void; onSelectFailure: (failureId: string) => void; onSelectPlan?: (planId: string, transitionSequence?: number) => void; onContent: (contentRef: string, recordSequence: number) => void; onNextContent?: () => void; onClearContent?: () => void; onArtifactUnavailable?: (error: unknown) => void };

type ModelDetail =
  | { kind: "request"; messages: { role: string; text: string }[] }
  | { kind: "response"; content: string };

type ModelCacheEntry = { loading: boolean; error?: string; detail?: ModelDetail };
type RawCacheEntry = { loading: boolean; error?: string; json?: string };
type StepDetail = { stepNumber: number; readyTasks: number; planStatus: string; skillName: string };
type StepCacheEntry = { loading: boolean; error?: string; detail?: StepDetail };
type StepActionKind = "proposed" | "validated" | "rejected";
type RecordSeverity = "normal" | "warning" | "error";

const warningRecordTypes = new Set([
  "MODEL_ATTEMPT_FAILED",
  "PLAN_VALIDATION_FAILED",
  "PLAN_RETRY_REQUESTED",
  "EVIDENCE_VALIDATION_FAILED",
  "STEP_ACTION_REJECTED",
]);

const errorRecordTypes = new Set(["ERROR_RECORDED", "TOOL_CALL_FAILED", "STEP_FAILED"]);

const frameDurationRecordTypes = new Set([
  "FRAME_CLOSED",
  "MODEL_RESPONSE_RECEIVED",
  "PLAN_CREATED",
  "TOOL_CALL_COMPLETED",
  "TOOL_CALL_FAILED",
  "STEP_COMPLETED",
  "STEP_FAILED",
]);

const stepTerminalRecordTypes = new Set(["STEP_COMPLETED", "STEP_FAILED"]);

type RecordFrame = Pick<TraceFrame, "frameId" | "parentFrameId" | "frameType" | "route">;

function enclosingStep(record: TraceRecord | undefined, framesById: ReadonlyMap<string, RecordFrame>): string | undefined {
  const visited = new Set<string>();
  let frame: RecordFrame | undefined = record;
  while (frame && !visited.has(frame.frameId)) {
    visited.add(frame.frameId);
    if (frame.frameType === "STEP_EXECUTION") return frame.frameId || undefined;
    frame = frame.parentFrameId ? framesById.get(frame.parentFrameId) : undefined;
  }
  return undefined;
}

function recordBelongsToStep(record: TraceRecord, stepFrameId: string, framesById: ReadonlyMap<string, RecordFrame>): boolean {
  if (record.frameId === stepFrameId) return true;
  const visited = new Set<string>();
  let parentFrameId: string | null = framesById.get(record.frameId)?.parentFrameId ?? record.parentFrameId;
  while (parentFrameId && !visited.has(parentFrameId)) {
    if (parentFrameId === stepFrameId) return true;
    visited.add(parentFrameId);
    parentFrameId = framesById.get(parentFrameId)?.parentFrameId ?? null;
  }
  return false;
}

function recordSeverity(record: TraceRecord, linkedFailure?: TraceFailure): RecordSeverity {
  if (linkedFailure || errorRecordTypes.has(record.type)) return "error";
  return record.validationStatus === "retrying" || record.validationStatus === "exhausted" || warningRecordTypes.has(record.type) ? "warning" : "normal";
}
type StepActionDetail = {
  kind: StepActionKind;
  skillName: string;
  stepNumber: number;
  actionType?: string;
  taskId?: string;
  toolName?: string;
  reason?: string;
  earlierRejectedAttempts?: number;
  exhausted?: boolean;
  rawResponse?: string;
  proposedSequence?: number;
};
type StepActionCacheEntry = { loading: boolean; error?: string; detail?: StepActionDetail };
type ToolResultDetail = {
  kind: "tool-result";
  capabilityName: string;
  taskId?: string;
  eventId: string;
  note?: string;
  result: string;
};
type ToolInputDetail = {
  kind: "tool-input";
  capabilityName: string;
  taskId?: string;
  unplanned: boolean;
  eventId: string;
  note?: string;
  arguments: string;
};
type StructuredOutputIssue = { path: string; message: string; canonicalField?: string };
type StructuredOutputDetail = {
  kind: "structured-output";
  skillName: string;
  status: string;
  attempt: number;
  retryCount: number;
  maxRetries: number;
  failureMode?: string;
  issues: StructuredOutputIssue[];
};
type StepCompletedDetail = {
  kind: "step-completed";
  skillName: string;
  stepNumber: number;
  actionType: string;
  status: "completed" | "failed";
  taskId?: string;
  toolName?: string;
  resultPreview?: string;
  error?: string;
  relatedRecord?: TraceRecord;
};
type EvidenceDetail = {
  kind: "evidence";
  skillName: string;
  taskId?: string;
  unplanned: boolean;
  availableSources: string[];
  sourceResult?: TraceRecord;
};
type CompletionUsage = {
  skillInvocations: number;
  toolInvocations: number;
  linterRetries: number;
  modelCalls: number;
  providerAttempts: number;
  promptUnits: number;
  completionUnits: number;
  totalUnits: number;
  exactModelResponses: number;
  heuristicModelResponses: number;
  unavailableModelResponses: number;
};
type CompletionDetail = {
  kind: "completion";
  outcome: "SUCCEEDED" | "FAILED" | "ABORTED";
  skillName?: string;
  objective?: string;
  entryPoint?: string;
  remainingFrames: number;
  persistencePolicy: "NEVER" | "ONERROR" | "ALWAYS";
  errored: boolean;
  terminalFailureId?: string;
  usage: CompletionUsage;
};
type RecordDetail = ToolInputDetail | ToolResultDetail | StructuredOutputDetail | StepCompletedDetail | EvidenceDetail | CompletionDetail;
type RecordDetailCacheEntry = { loading: boolean; error?: string; detail?: RecordDetail };

function decodeBytes(range: TraceRange): Uint8Array {
  if (range.encoding === "BASE64") {
    try {
      const binary = atob(range.content);
      return Uint8Array.from(binary, (character) => character.charCodeAt(0));
    } catch {
      throw new Error("Content contained invalid base64 data.");
    }
  }
  return new TextEncoder().encode(range.content);
}

function joinBytes(parts: Uint8Array[]): Uint8Array {
  const result = new Uint8Array(parts.reduce((length, part) => length + part.length, 0));
  let offset = 0;
  for (const part of parts) {
    result.set(part, offset);
    offset += part.length;
  }
  return result;
}

async function readCompleteRecord(traceId: string, sequence: number, source: TraceSource): Promise<string> {
  const parts: Uint8Array[] = [];
  let cursor: string | undefined;
  do {
    const range = await getRawRecordRange(traceId, sequence, cursor, source);
    parts.push(decodeBytes(range));
    if (!range.hasMore) break;
    if (!range.nextCursor || range.nextCursor === cursor) throw new Error("Content continuation was invalid.");
    cursor = range.nextCursor;
  } while (true);
  return new TextDecoder("utf-8", { fatal: true }).decode(joinBytes(parts)).trim();
}

async function readCompleteContent(traceId: string, contentRef: string, source: TraceSource): Promise<string> {
  const parts: Uint8Array[] = [];
  let cursor: string | undefined;
  do {
    const range = await getContentRange(traceId, contentRef, cursor, source);
    parts.push(decodeBytes(range));
    if (!range.hasMore) break;
    if (!range.nextCursor || range.nextCursor === cursor) throw new Error("Content continuation was invalid.");
    cursor = range.nextCursor;
  } while (true);
  return new TextDecoder("utf-8", { fatal: true }).decode(joinBytes(parts)).trim();
}

function parseJsonObject(raw: string, label: string): Record<string, unknown> {
  const value: unknown = JSON.parse(raw);
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`${label} did not contain a JSON object.`);
  }
  return value as Record<string, unknown>;
}

function recordData(rawRecord: string): unknown {
  const envelope = parseJsonObject(rawRecord, "Model record");
  if (!("data" in envelope)) throw new Error("Model record did not contain data.");
  return envelope.data;
}

function parseModelDetail(kind: "request" | "response", value: unknown): ModelDetail {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`Model ${kind} data was not a JSON object.`);
  }
  const fields = value as Record<string, unknown>;
  if (kind === "request") {
    if (!Array.isArray(fields.messages)) {
      throw new Error("Model request data did not contain messages.");
    }
    const messages = fields.messages.map((message, index) => {
      if (!message || typeof message !== "object" || Array.isArray(message)) {
        throw new Error(`Model request message ${index + 1} was not a JSON object.`);
      }
      const messageFields = message as Record<string, unknown>;
      if (typeof messageFields.messageType !== "string" || typeof messageFields.text !== "string") {
        throw new Error(`Model request message ${index + 1} did not contain messageType and text.`);
      }
      return { role: messageFields.messageType, text: messageFields.text };
    });
    return { kind, messages };
  }

  if (typeof fields.content !== "string") {
    throw new Error("Model response data did not contain text content.");
  }
  const trimmed = fields.content.trim();
  try {
    return { kind, content: JSON.stringify(JSON.parse(trimmed), null, 2) };
  } catch {
    return { kind, content: fields.content };
  }
}

function parseStepStartedDetail(rawRecord: string, route: string): StepDetail {
  const envelope = parseJsonObject(rawRecord, "Step record");
  if (!envelope.metadata || typeof envelope.metadata !== "object" || Array.isArray(envelope.metadata)) {
    throw new Error("Step record did not contain metadata.");
  }
  if (!envelope.data || typeof envelope.data !== "object" || Array.isArray(envelope.data)) {
    throw new Error("Step record did not contain data.");
  }
  const metadata = envelope.metadata as Record<string, unknown>;
  const data = envelope.data as Record<string, unknown>;
  if (!Number.isSafeInteger(metadata.stepNumber) || !Number.isSafeInteger(metadata.readyTasks) ||
      typeof metadata.stepNumber !== "number" || metadata.stepNumber < 1 ||
      typeof metadata.readyTasks !== "number" || metadata.readyTasks < 0 ||
      typeof data.planStatus !== "string" || data.planStatus.length === 0) {
    throw new Error("Step record contained invalid step facts.");
  }
  const separator = route.indexOf("#step-");
  if (separator <= 0) throw new Error("Step record route did not identify its owning skill.");
  return {
    stepNumber: metadata.stepNumber,
    readyTasks: metadata.readyTasks,
    planStatus: data.planStatus,
    skillName: route.slice(0, separator),
  };
}

function parseStepRoute(route: string): { skillName: string; stepNumber: number } {
  const match = /^(.*)#step-(\d+)$/.exec(route);
  if (!match || match[1].length === 0) throw new Error("Step action route did not identify its owning skill and step.");
  const stepNumber = Number(match[2]);
  if (!Number.isSafeInteger(stepNumber) || stepNumber < 1) throw new Error("Step action route contained an invalid step number.");
  return { skillName: match[1], stepNumber };
}

function optionalNonemptyString(value: unknown): string | undefined {
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

function requiredNonnegativeInteger(value: unknown, label: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0) throw new Error(`${label} was invalid.`);
  return value;
}

function prettyValue(value: unknown): string {
  if (typeof value === "string") {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
  return JSON.stringify(value, null, 2);
}

function recordParts(rawRecord: string, label: string): { metadata: Record<string, unknown>; data: Record<string, unknown> } {
  const envelope = parseJsonObject(rawRecord, label);
  if (!envelope.metadata || typeof envelope.metadata !== "object" || Array.isArray(envelope.metadata)) {
    throw new Error(`${label} did not contain metadata.`);
  }
  if (!envelope.data || typeof envelope.data !== "object" || Array.isArray(envelope.data)) {
    throw new Error(`${label} did not contain data.`);
  }
  return {
    metadata: envelope.metadata as Record<string, unknown>,
    data: envelope.data as Record<string, unknown>,
  };
}

function parseStepActionDetail(rawRecord: string, route: string, kind: StepActionKind): StepActionDetail {
  const envelope = parseJsonObject(rawRecord, "Step action record");
  if (!envelope.metadata || typeof envelope.metadata !== "object" || Array.isArray(envelope.metadata)) {
    throw new Error("Step action record did not contain metadata.");
  }
  if (!envelope.data || typeof envelope.data !== "object" || Array.isArray(envelope.data)) {
    throw new Error("Step action record did not contain data.");
  }
  const metadata = envelope.metadata as Record<string, unknown>;
  const data = envelope.data as Record<string, unknown>;
  const routeDetail = parseStepRoute(route);

  if (kind === "proposed") {
    if (typeof metadata.stepAction !== "string" || metadata.stepAction.length === 0 ||
        typeof metadata.taskId !== "string" || typeof metadata.toolName !== "string") {
      throw new Error("Proposed action record contained invalid action facts.");
    }
    return {
      kind,
      ...routeDetail,
      actionType: metadata.stepAction,
      taskId: optionalNonemptyString(metadata.taskId),
      toolName: optionalNonemptyString(metadata.toolName),
    };
  }

  if (kind === "validated") {
    if (typeof metadata.stepAction !== "string" || metadata.stepAction.length === 0) {
      throw new Error("Validated action record did not identify the accepted action type.");
    }
    return { kind, ...routeDetail, actionType: metadata.stepAction };
  }

  if (typeof metadata.reason !== "string" || metadata.reason.length === 0) {
    throw new Error("Rejected action record did not contain a rejection reason.");
  }
  if (metadata.retry !== undefined && (!Number.isSafeInteger(metadata.retry) || typeof metadata.retry !== "number" || metadata.retry < 0)) {
    throw new Error("Rejected action record contained an invalid retry count.");
  }
  if (metadata.exhausted !== undefined && typeof metadata.exhausted !== "boolean") {
    throw new Error("Rejected action record contained an invalid exhausted value.");
  }
  if (data.stepAction !== undefined && (typeof data.stepAction !== "string" || data.stepAction.length === 0)) {
    throw new Error("Rejected action record contained an invalid action type.");
  }
  if (data.rawResponse !== undefined && typeof data.rawResponse !== "string") {
    throw new Error("Rejected action record contained an invalid response excerpt.");
  }
  return {
    kind,
    ...routeDetail,
    actionType: optionalNonemptyString(data.stepAction),
    reason: metadata.reason,
    earlierRejectedAttempts: metadata.retry as number | undefined,
    exhausted: metadata.exhausted as boolean | undefined,
    rawResponse: optionalNonemptyString(data.rawResponse),
  };
}

function ModelDetailView({ detail }: { detail: ModelDetail }) {
  if (detail.kind === "response") return <pre>{detail.content}</pre>;
  return <div className="trace-model-messages">
    {detail.messages.map((message, index) => <section className="trace-model-message" key={`${message.role}-${index}`}>
      <h5>{message.role.toLowerCase().replaceAll("_", " ")}</h5>
      <pre>{message.text}</pre>
    </section>)}
  </div>;
}

async function findProposedAction(traceId: string, record: TraceRecord, source: TraceSource): Promise<{ sequence: number; detail: StepActionDetail } | undefined> {
  if (!record.frameId || record.sequence <= 1) return undefined;
  const candidates: TraceRecord[] = [];
  let cursor: string | undefined;
  do {
    const page = await getTraceRecords(traceId, cursor, {
      types: ["STEP_ACTION_PROPOSED"],
      frameId: record.frameId,
      maxSequence: record.sequence - 1,
    }, source);
    candidates.push(...page.items);
    if (!page.hasMore) break;
    if (!page.nextCursor || page.nextCursor === cursor) throw new Error("Proposed action continuation was invalid.");
    cursor = page.nextCursor;
  } while (true);
  const candidate = candidates.sort((left, right) => right.sequence - left.sequence)[0];
  if (!candidate) return undefined;
  const detail = parseStepActionDetail(await readCompleteRecord(traceId, candidate.sequence, source), candidate.route, "proposed");
  return { sequence: candidate.sequence, detail };
}

async function readStepActionDetail(traceId: string, record: TraceRecord, kind: StepActionKind, source: TraceSource): Promise<StepActionDetail> {
  let detail = parseStepActionDetail(await readCompleteRecord(traceId, record.sequence, source), record.route, kind);
  if (kind !== "proposed") {
    const proposed = await findProposedAction(traceId, record, source);
    if (proposed && proposed.detail.actionType === detail.actionType) {
      detail = {
        ...detail,
        taskId: proposed.detail.taskId,
        toolName: proposed.detail.toolName,
        proposedSequence: proposed.sequence,
      };
    }
  }
  return detail;
}

async function readToolResultDetail(traceId: string, record: TraceRecord, source: TraceSource): Promise<ToolResultDetail> {
  const { metadata, data } = recordParts(await readCompleteRecord(traceId, record.sequence, source), "Tool result record");
  const capabilityName = optionalNonemptyString(metadata.capabilityName) ?? optionalNonemptyString(data.capabilityName);
  const taskId = optionalNonemptyString(metadata.linkedTaskId) ?? optionalNonemptyString(data.linkedTaskId);
  const eventId = optionalNonemptyString(data.eventId);
  if (!capabilityName || !eventId) throw new Error("Tool result record contained invalid identifying facts.");
  if (!data.details || typeof data.details !== "object" || Array.isArray(data.details)) {
    throw new Error("Tool result record did not contain result details.");
  }
  const details = data.details as Record<string, unknown>;
  if (!("result" in details)) throw new Error("Tool result details did not contain a result.");
  const result = prettyValue(details.result);
  return {
    kind: "tool-result",
    capabilityName,
    taskId,
    eventId,
    note: optionalNonemptyString(data.note),
    result,
  };
}

async function readToolInputDetail(traceId: string, record: TraceRecord, source: TraceSource): Promise<ToolInputDetail> {
  const { metadata, data } = recordParts(await readCompleteRecord(traceId, record.sequence, source), "Tool input record");
  const capabilityName = optionalNonemptyString(metadata.capabilityName) ?? optionalNonemptyString(data.capabilityName);
  const taskId = optionalNonemptyString(metadata.linkedTaskId) ?? optionalNonemptyString(data.linkedTaskId);
  const eventId = optionalNonemptyString(data.eventId);
  if (!capabilityName || !eventId) throw new Error("Tool input record contained invalid identifying facts.");
  if (metadata.unplanned !== undefined && metadata.unplanned !== true) {
    throw new Error("Tool input record contained an invalid unplanned marker.");
  }
  const unplanned = metadata.unplanned === true;
  if (unplanned && taskId) throw new Error("Unplanned tool input unexpectedly identified a plan task.");
  if (!unplanned && !taskId) throw new Error("Planned tool input did not identify a plan task.");
  if (!data.details || typeof data.details !== "object" || Array.isArray(data.details)) {
    throw new Error("Tool input record did not contain input details.");
  }
  const details = data.details as Record<string, unknown>;
  if (!("arguments" in details)) throw new Error("Tool input details did not contain arguments.");
  return {
    kind: "tool-input",
    capabilityName,
    taskId,
    unplanned,
    eventId,
    note: optionalNonemptyString(data.note),
    arguments: prettyValue(details.arguments),
  };
}

function parseStructuredOutputDetail(rawRecord: string): StructuredOutputDetail {
  const { metadata, data } = recordParts(rawRecord, "Structured output record");
  const skillName = optionalNonemptyString(data.skillName) ?? optionalNonemptyString(metadata.skillName);
  const status = optionalNonemptyString(data.status) ?? optionalNonemptyString(metadata.status);
  if (!skillName || !status || !["PASSED", "RETRYING", "EXHAUSTED"].includes(status)) {
    throw new Error("Structured output record contained invalid validation facts.");
  }
  if (!Array.isArray(data.issues)) throw new Error("Structured output record did not contain validation issues.");
  const issues = data.issues.map((candidate, index): StructuredOutputIssue => {
    if (!candidate || typeof candidate !== "object" || Array.isArray(candidate)) {
      throw new Error(`Validation issue ${index + 1} was not a JSON object.`);
    }
    const issue = candidate as Record<string, unknown>;
    if (typeof issue.path !== "string" || typeof issue.message !== "string") {
      throw new Error(`Validation issue ${index + 1} did not contain a path and message.`);
    }
    if (issue.canonicalField !== undefined && issue.canonicalField !== null && typeof issue.canonicalField !== "string") {
      throw new Error(`Validation issue ${index + 1} contained an invalid canonical field.`);
    }
    return { path: issue.path, message: issue.message, canonicalField: optionalNonemptyString(issue.canonicalField) };
  });
  if (status === "PASSED" && issues.length > 0) throw new Error("Passed output validation unexpectedly contained issues.");
  const attempt = requiredNonnegativeInteger(data.attempt, "Validation attempt");
  if (attempt < 1) throw new Error("Validation attempt was invalid.");
  return {
    kind: "structured-output",
    skillName,
    status,
    attempt,
    retryCount: requiredNonnegativeInteger(data.retryCount, "Validation retry count"),
    maxRetries: requiredNonnegativeInteger(data.maxRetries, "Maximum validation retries"),
    failureMode: optionalNonemptyString(data.failureMode),
    issues,
  };
}

async function readStepCompletedDetail(traceId: string, record: TraceRecord, source: TraceSource): Promise<StepCompletedDetail> {
  const { metadata, data } = recordParts(await readCompleteRecord(traceId, record.sequence, source), "Completed step record");
  const route = parseStepRoute(record.route);
  const stepNumber = requiredNonnegativeInteger(metadata.stepNumber, "Completed step number");
  const actionType = optionalNonemptyString(metadata.stepAction);
  if (stepNumber < 1 || stepNumber !== route.stepNumber || !actionType) {
    throw new Error("Completed step record contained invalid step facts.");
  }
  const recordedStatus = metadata.status === undefined ? "completed" : metadata.status;
  if (recordedStatus !== "completed" && recordedStatus !== "failed") throw new Error("Completed step record contained an invalid status.");
  const taskId = optionalNonemptyString(metadata.taskId);
  const toolName = optionalNonemptyString(metadata.toolName);
  const resultPreview = optionalNonemptyString(data.resultPreview);
  const error = optionalNonemptyString(data.error);
  let relatedRecord: TraceRecord | undefined;
  if (actionType === "CALL_TOOL") {
    const candidates: TraceRecord[] = [];
    let cursor: string | undefined;
    do {
      const page = await getTraceRecords(traceId, cursor, {
        types: ["TOOL_CALL_COMPLETED", "TOOL_CALL_FAILED"],
        maxSequence: record.sequence - 1,
      });
      candidates.push(...page.items.filter((candidate) => candidate.parentFrameId === record.frameId));
      if (!page.hasMore) break;
      if (!page.nextCursor || page.nextCursor === cursor) throw new Error("Related tool result continuation was invalid.");
      cursor = page.nextCursor;
    } while (true);
    relatedRecord = candidates.sort((left, right) => right.sequence - left.sequence)[0];
  } else if (actionType === "FINAL_RESPONSE") {
    const page = await getTraceRecords(traceId, undefined, {
      types: ["MODEL_RESPONSE_RECEIVED"],
      route: `${record.route}-model`,
      maxSequence: record.sequence - 1,
    }, source);
    relatedRecord = [...page.items].sort((left, right) => right.sequence - left.sequence)[0];
  }
  return {
    kind: "step-completed",
    skillName: route.skillName,
    stepNumber,
    actionType,
    status: recordedStatus,
    taskId,
    toolName,
    resultPreview,
    error,
    relatedRecord,
  };
}

async function readEvidenceDetail(traceId: string, record: TraceRecord, source: TraceSource): Promise<EvidenceDetail> {
  const { metadata, data } = recordParts(await readCompleteRecord(traceId, record.sequence, source), "Evidence record");
  const skillName = optionalNonemptyString(data.successfulSkill);
  const capabilityName = optionalNonemptyString(metadata.capabilityName);
  const taskId = optionalNonemptyString(metadata.linkedTaskId);
  if (!skillName || !capabilityName || skillName !== capabilityName || typeof metadata.unplanned !== "boolean") {
    throw new Error("Evidence record contained invalid source facts.");
  }
  if (!Array.isArray(data.successfulDirectSkills)) {
    throw new Error("Evidence record did not contain available evidence sources.");
  }
  const availableSources = data.successfulDirectSkills.map((source, index) => {
    if (typeof source !== "string" || source.length === 0) throw new Error(`Evidence source ${index + 1} was invalid.`);
    return source;
  });
  if (!availableSources.includes(skillName)) {
    throw new Error("Evidence record's successful source was missing from the available sources.");
  }
  if (new Set(availableSources).size !== availableSources.length) {
    throw new Error("Evidence record contained duplicate available sources.");
  }
  if (metadata.unplanned && taskId) throw new Error("Unplanned evidence unexpectedly identified a plan task.");
  if (!metadata.unplanned && !taskId) throw new Error("Planned evidence did not identify a plan task.");

  const candidates: TraceRecord[] = [];
  let cursor: string | undefined;
  do {
    const page = await getTraceRecords(traceId, cursor, {
      types: ["TOOL_CALL_COMPLETED"],
      maxSequence: record.sequence - 1,
    }, source);
    candidates.push(...page.items.filter((candidate) => candidate.parentFrameId === record.frameId && candidate.route === capabilityName));
    if (!page.hasMore) break;
    if (!page.nextCursor || page.nextCursor === cursor) throw new Error("Source result continuation was invalid.");
    cursor = page.nextCursor;
  } while (true);

  return {
    kind: "evidence",
    skillName,
    taskId,
    unplanned: metadata.unplanned,
    availableSources,
    sourceResult: candidates.sort((left, right) => right.sequence - left.sequence)[0],
  };
}

function parseCompletionDetail(rawRecord: string): CompletionDetail {
  const envelope = parseJsonObject(rawRecord, "Completion record");
  if (!envelope.metadata || typeof envelope.metadata !== "object" || Array.isArray(envelope.metadata)) {
    throw new Error("Completion record did not contain metadata.");
  }
  if (envelope.data !== null) throw new Error("Completion record unexpectedly contained data.");
  const metadata = envelope.metadata as Record<string, unknown>;
  const outcome = metadata.outcome;
  const persistencePolicy = metadata.persistencePolicy;
  if (outcome !== "SUCCEEDED" && outcome !== "FAILED" && outcome !== "ABORTED") {
    throw new Error("Completion record contained an invalid outcome.");
  }
  if (persistencePolicy !== "NEVER" && persistencePolicy !== "ONERROR" && persistencePolicy !== "ALWAYS") {
    throw new Error("Completion record contained an invalid persistence policy.");
  }
  if (typeof metadata.errored !== "boolean") throw new Error("Completion record contained an invalid trace error flag.");
  const remainingFrames = requiredNonnegativeInteger(metadata.remainingFrames, "Remaining frame count");
  const terminalFailureId = optionalNonemptyString(metadata.terminalFailureId);
  if (metadata.terminalFailureId !== undefined && !terminalFailureId) throw new Error("Completion record contained an invalid terminal failure ID.");
  if (outcome === "SUCCEEDED" && terminalFailureId) throw new Error("Successful completion unexpectedly identified a terminal failure.");
  if (outcome !== "SUCCEEDED" && !terminalFailureId) throw new Error("Failed or aborted completion did not identify its terminal failure.");
  if (!metadata.sessionUsageSnapshot || typeof metadata.sessionUsageSnapshot !== "object" || Array.isArray(metadata.sessionUsageSnapshot)) {
    throw new Error("Completion record did not contain a terminal usage snapshot.");
  }
  const usageFields = metadata.sessionUsageSnapshot as Record<string, unknown>;
  const usage: CompletionUsage = {
    skillInvocations: requiredNonnegativeInteger(usageFields.skillInvocations, "Skill invocation count"),
    toolInvocations: requiredNonnegativeInteger(usageFields.toolInvocations, "Tool invocation count"),
    linterRetries: requiredNonnegativeInteger(usageFields.linterRetries, "Linter retry count"),
    modelCalls: requiredNonnegativeInteger(usageFields.modelCalls, "Model call count"),
    providerAttempts: requiredNonnegativeInteger(usageFields.providerAttempts, "Provider attempt count"),
    promptUnits: requiredNonnegativeInteger(usageFields.promptUnits, "Prompt unit count"),
    completionUnits: requiredNonnegativeInteger(usageFields.completionUnits, "Completion unit count"),
    totalUnits: requiredNonnegativeInteger(usageFields.totalUnits, "Total usage unit count"),
    exactModelResponses: requiredNonnegativeInteger(usageFields.exactModelResponses, "Exact model response count"),
    heuristicModelResponses: requiredNonnegativeInteger(usageFields.heuristicModelResponses, "Heuristic model response count"),
    unavailableModelResponses: requiredNonnegativeInteger(usageFields.unavailableModelResponses, "Unavailable model response count"),
  };
  if (usage.promptUnits + usage.completionUnits !== usage.totalUnits) {
    throw new Error("Completion record's terminal usage totals did not reconcile.");
  }
  if (usage.exactModelResponses + usage.heuristicModelResponses + usage.unavailableModelResponses !== usage.modelCalls) {
    throw new Error("Completion record's model response precision counts did not reconcile.");
  }
  return {
    kind: "completion",
    outcome,
    skillName: optionalNonemptyString(metadata.skillName),
    objective: optionalNonemptyString(metadata.objective),
    entryPoint: optionalNonemptyString(metadata.entryPoint),
    remainingFrames,
    persistencePolicy,
    errored: metadata.errored,
    terminalFailureId,
    usage,
  };
}

function humanizeAction(value: string): string {
  const normalized = value.toLowerCase().replaceAll("_", " ");
  return normalized.length > 0 ? normalized[0].toUpperCase() + normalized.slice(1) : normalized;
}

function StepActionDetailView({ detail }: { detail: StepActionDetail }) {
  const status = detail.kind === "proposed" ? "Proposed" : detail.kind === "validated" ? "Accepted" : "Rejected";
  return <>
    <dl className="trace-step-facts">
      <div><dt>Skill</dt><dd>{detail.skillName}</dd></div>
      <div><dt>Step</dt><dd>{detail.stepNumber}</dd></div>
      <div><dt>Status</dt><dd>{status}</dd></div>
      {detail.actionType && <div><dt>Action</dt><dd>{humanizeAction(detail.actionType)}</dd></div>}
      {detail.taskId && <div><dt>Task ID</dt><dd>{detail.taskId}</dd></div>}
      {detail.toolName && <div><dt>Tool</dt><dd>{detail.toolName}</dd></div>}
      {detail.earlierRejectedAttempts !== undefined && <div><dt>Earlier rejected attempts</dt><dd>{detail.earlierRejectedAttempts}</dd></div>}
      {detail.exhausted !== undefined && <div><dt>Retries exhausted</dt><dd>{detail.exhausted ? "Yes" : "No"}</dd></div>}
    </dl>
    {detail.kind === "proposed" && <p className="trace-step-note">The planner proposed this action. The runtime has not accepted or executed it yet.</p>}
    {detail.kind === "validated" && <p className="trace-step-note">The runtime accepted this action for execution. This does not mean the tool ran or succeeded.{detail.proposedSequence !== undefined && <> Proposed action: record {detail.proposedSequence}.</>}</p>}
    {detail.kind === "rejected" && <div className="trace-action-rejection">
      <p><strong>Reason:</strong> {detail.reason}</p>
      <p className="trace-step-note">The runtime rejected this proposal before execution. A later record may contain the planner's corrected action.</p>
      {detail.rawResponse && <><h5>Model response excerpt</h5><pre>{detail.rawResponse}</pre></>}
    </div>}
  </>;
}

function RecordDetailView({ detail, onOpenRelated, onSelectFailure }: { detail: RecordDetail; onOpenRelated: (record: TraceRecord) => void; onSelectFailure: (failureId: string) => void }) {
  if (detail.kind === "tool-input") return <>
    <dl className="trace-step-facts">
      <div><dt>Tool</dt><dd>{detail.capabilityName}</dd></div>
      <div><dt>Execution</dt><dd>{detail.unplanned ? "Unplanned" : "Planned"}</dd></div>
      {detail.taskId && <div><dt>Task ID</dt><dd>{detail.taskId}</dd></div>}
      <div><dt>Event ID</dt><dd>{detail.eventId}</dd></div>
    </dl>
    {detail.unplanned && <p className="trace-step-note">No plan task was linked to this invocation.</p>}
    {detail.note && <p><strong>Note:</strong> {detail.note}</p>}
    <h5>Arguments</h5>
    <pre>{detail.arguments}</pre>
  </>;

  if (detail.kind === "tool-result") return <>
    <dl className="trace-step-facts">
      <div><dt>Tool</dt><dd>{detail.capabilityName}</dd></div>
      {detail.taskId && <div><dt>Task ID</dt><dd>{detail.taskId}</dd></div>}
      <div><dt>Event ID</dt><dd>{detail.eventId}</dd></div>
    </dl>
    {detail.note && <p><strong>Note:</strong> {detail.note}</p>}
    <h5>Result</h5>
    <pre>{detail.result}</pre>
  </>;

  if (detail.kind === "structured-output") return <>
    <dl className="trace-step-facts">
      <div><dt>Skill</dt><dd>{detail.skillName}</dd></div>
      <div><dt>Status</dt><dd>{humanizeAction(detail.status)}</dd></div>
      <div><dt>Attempt</dt><dd>{detail.attempt}</dd></div>
      <div><dt>Retries used</dt><dd>{detail.retryCount} of {detail.maxRetries}</dd></div>
      {detail.failureMode && <div><dt>Failure mode</dt><dd>{humanizeAction(detail.failureMode)}</dd></div>}
    </dl>
    {detail.status === "PASSED" && <p className="trace-step-note">Output schema validation passed{detail.retryCount === 0 ? " without a retry" : ` after ${detail.retryCount} ${detail.retryCount === 1 ? "retry" : "retries"}`}.</p>}
    {detail.status === "RETRYING" && <p className="trace-step-note">The output did not satisfy its schema. Loomspan requested another model response.</p>}
    {detail.status === "EXHAUSTED" && <p className="trace-step-note">The output did not satisfy its schema and no validation retries remain.</p>}
    {detail.issues.length > 0 && <section className="trace-validation-issues" aria-label="Output schema issues">
      <h5>Issues</h5>
      <ul>{detail.issues.map((issue, index) => <li key={`${issue.path}-${index}`}><code>{issue.path}</code> &mdash; {issue.message}{issue.canonicalField && <> <span>(field: <code>{issue.canonicalField}</code>)</span></>}</li>)}</ul>
    </section>}
  </>;

  if (detail.kind === "evidence") return <>
    <dl className="trace-step-facts">
      <div><dt>Evidence source</dt><dd>{detail.skillName}</dd></div>
      {detail.taskId && <div><dt>Task ID</dt><dd>{detail.taskId}</dd></div>}
      <div><dt>Execution</dt><dd>{detail.unplanned ? "Unplanned" : "Planned"}</dd></div>
    </dl>
    <section className="trace-evidence-sources" aria-label="Available evidence sources">
      <h5>Available evidence sources after this record</h5>
      <ul>{detail.availableSources.map((source) => <li key={source}><code>{source}</code></li>)}</ul>
    </section>
    <p className="trace-step-note">This successful skill became available as an evidence source. This record does not determine whether a particular final-response claim is supported.</p>
    {detail.sourceResult && <button type="button" onClick={() => onOpenRelated(detail.sourceResult!)}>View source result</button>}
  </>;

  if (detail.kind === "completion") {
    const usage = detail.usage;
    const abnormal = detail.outcome !== "SUCCEEDED" || detail.remainingFrames > 0 || detail.errored || usage.unavailableModelResponses > 0;
    return <>
      <dl className="trace-step-facts">
        <div><dt>Outcome</dt><dd>{humanizeAction(detail.outcome)}</dd></div>
        {detail.skillName && <div><dt>Entry skill</dt><dd>{detail.skillName}</dd></div>}
        {detail.entryPoint && <div><dt>Entry point</dt><dd>{detail.entryPoint}</dd></div>}
        <div><dt>Remaining open frames</dt><dd>{detail.remainingFrames}</dd></div>
        <div><dt>Persistence</dt><dd>{humanizeAction(detail.persistencePolicy)}</dd></div>
        <div><dt>Trace error recorded</dt><dd>{detail.errored ? "Yes" : "No"}</dd></div>
        {detail.terminalFailureId && <div><dt>Terminal failure ID</dt><dd>{detail.terminalFailureId}</dd></div>}
      </dl>
      {detail.objective && <p><strong>Objective:</strong> {detail.objective}</p>}
      <div className="trace-completion-tables">
        <table aria-label="Final execution counters"><caption>Final execution counters</caption><tbody>
          <tr><th scope="row">Skill invocations</th><td>{usage.skillInvocations.toLocaleString()}</td></tr>
          <tr><th scope="row">Tool invocations</th><td>{usage.toolInvocations.toLocaleString()}</td></tr>
          <tr><th scope="row">Model calls</th><td>{usage.modelCalls.toLocaleString()}</td></tr>
          <tr><th scope="row">Provider attempts</th><td>{usage.providerAttempts.toLocaleString()}</td></tr>
          <tr><th scope="row">Linter retries</th><td>{usage.linterRetries.toLocaleString()}</td></tr>
        </tbody></table>
        <table aria-label="Final usage"><caption>Final usage</caption><tbody>
          <tr><th scope="row">Prompt units</th><td>{usage.promptUnits.toLocaleString()}</td></tr>
          <tr><th scope="row">Completion units</th><td>{usage.completionUnits.toLocaleString()}</td></tr>
          <tr><th scope="row">Total units</th><td>{usage.totalUnits.toLocaleString()}</td></tr>
        </tbody></table>
        <table aria-label="Usage precision"><caption>Usage precision</caption><tbody>
          <tr><th scope="row">Exact responses</th><td>{usage.exactModelResponses.toLocaleString()}</td></tr>
          <tr><th scope="row">Heuristic responses</th><td>{usage.heuristicModelResponses.toLocaleString()}</td></tr>
          <tr><th scope="row">Unavailable responses</th><td>{usage.unavailableModelResponses.toLocaleString()}</td></tr>
        </tbody></table>
      </div>
      <p className="trace-step-note">Remaining open frames reports cleanup state when this terminal record was written; it does not make a broader claim about every cleanup operation.</p>
      {abnormal && <p className="trace-completion-warning">This completion contains one or more failure, cleanup, trace-error, or usage-availability conditions.</p>}
      {detail.terminalFailureId && <button className="trace-error-action" type="button" onClick={() => onSelectFailure(detail.terminalFailureId!)}>View terminal error</button>}
    </>;
  }

  return <>
    <dl className="trace-step-facts">
      <div><dt>Skill</dt><dd>{detail.skillName}</dd></div>
      <div><dt>Step</dt><dd>{detail.stepNumber}</dd></div>
      <div><dt>Status</dt><dd>{humanizeAction(detail.status)}</dd></div>
      <div><dt>Action</dt><dd>{humanizeAction(detail.actionType)}</dd></div>
      {detail.taskId && <div><dt>Task ID</dt><dd>{detail.taskId}</dd></div>}
      {detail.toolName && <div><dt>Tool</dt><dd>{detail.toolName}</dd></div>}
    </dl>
    {detail.error && <p className="trace-action-error"><strong>Error:</strong> {detail.error}</p>}
    {detail.resultPreview && <><h5>Result preview</h5><pre>{prettyValue(detail.resultPreview)}</pre></>}
    {detail.actionType === "CALL_TOOL" && <p className="trace-step-note">The preview may be truncated. The related tool record is authoritative for the full result.</p>}
    {detail.actionType === "FINAL_RESPONSE" && <p className="trace-step-note">This record confirms the final-response step completed; it does not contain the response body.</p>}
    {detail.relatedRecord && <button type="button" onClick={() => onOpenRelated(detail.relatedRecord!)}>{detail.actionType === "CALL_TOOL" ? (detail.relatedRecord.type === "TOOL_CALL_FAILED" ? "View tool failure record" : "View full tool result") : "View model response record"}</button>}
  </>;
}

export function TraceRecords({ traceId, source = "TARGET", scopeGeneration = 0, verifyScope, records, frames = [], failures, selectedRecordSequence, selectedFailureId, activeContentRecordSequence, contentRange, contentPending = false, contentError, onSelectRecord, onSelectFailure, onSelectPlan, onContent, onNextContent = () => {}, onClearContent = () => {}, onArtifactUnavailable }: Props) {
  const framesById = useMemo(() => new Map(frames.map((frame) => [frame.frameId, frame])), [frames]);
  const contextFrames = useMemo(() => {
    const known = new Map<string, RecordFrame>(framesById);
    for (const record of records) if (record.frameId && !known.has(record.frameId)) known.set(record.frameId, record);
    return known;
  }, [framesById, records]);
  const selectedRecord = records.find((record) => record.sequence === selectedRecordSequence);
  const selectedStepFrameId = enclosingStep(selectedRecord, contextFrames);
  const selectedStepStartSequence = selectedStepFrameId
    ? records.find((record) => record.frameId === selectedStepFrameId && record.type === "STEP_STARTED")?.sequence
    : undefined;
  const selectedStepTerminalSequence = selectedStepFrameId
    ? records.find((record) => record.frameId === selectedStepFrameId && stepTerminalRecordTypes.has(record.type))?.sequence
    : undefined;
  const [expanded, setExpanded] = useState<string | null>(null);
  useEffect(() => { setExpanded(null); }, [traceId, source, scopeGeneration]);
  useEffect(() => {
    if (expanded?.endsWith(":diff") && !records.some((record) => expanded === `${traceId}:${record.sequence}:diff`)) setExpanded(null);
  }, [expanded, records, traceId]);
  const [modelCache, setModelCache] = useState<Record<string, ModelCacheEntry>>({});
  const [rawCache, setRawCache] = useState<Record<string, RawCacheEntry>>({});
  const [stepCache, setStepCache] = useState<Record<string, StepCacheEntry>>({});
  const [stepActionCache, setStepActionCache] = useState<Record<string, StepActionCacheEntry>>({});
  const [recordDetailCache, setRecordDetailCache] = useState<Record<string, RecordDetailCacheEntry>>({});

  useEffect(() => {
    if (!selectedRecordSequence) return;
    document.getElementById(`trace-record-${selectedRecordSequence}`)?.focus();
  }, [records, selectedRecordSequence]);

  const reportArtifactUnavailable = (error: unknown) => {
    if (error instanceof Error && "code" in error && (error.code === "ARTIFACT_EXPIRED" || error.code === "NOT_FOUND")) onArtifactUnavailable?.(error);
  };

  const handleToggleModel = (record: TraceRecord) => {
    const key = `${traceId}:${record.sequence}`;
    const expansionKey = `${key}:model`;
    if (expanded === expansionKey) {
      setExpanded(null);
      return;
    }
    setExpanded(expansionKey);
    if (!traceId) return;
    const existing = modelCache[key];
    if (existing?.detail || existing?.loading) return;
    const kind = record.type === "MODEL_RESPONSE_RECEIVED" ? "response" : "request";
    setModelCache((previous) => ({ ...previous, [key]: { loading: true } }));
	const detailSource = record.content?.inlineContent
      ? Promise.resolve().then(() => JSON.parse(record.content!.inlineContent!) as unknown)
      : record.content?.contentRef
        ? readCompleteContent(traceId, record.content.contentRef, source).then((raw) => JSON.parse(raw) as unknown)
        : Promise.reject(new Error("Model content is unavailable."));
	void detailSource
      .then((value) => {
        const detail = parseModelDetail(kind, value);
        setModelCache((previous) => ({ ...previous, [key]: { loading: false, detail } }));
      })
      .catch((error: unknown) => {
        reportArtifactUnavailable(error);
        const message = error instanceof Error ? `${kind === "request" ? "Request" : "Response"} could not be displayed: ${error.message}` : `${kind === "request" ? "Request" : "Response"} could not be displayed.`;
        setModelCache((previous) => ({ ...previous, [key]: { loading: false, error: message } }));
      });
  };

  const handleToggleRaw = (record: TraceRecord) => {
    const key = `${traceId}:${record.sequence}`;
    const expansionKey = `${key}:raw`;
    if (expanded === expansionKey) {
      setExpanded(null);
      return;
    }
    setExpanded(expansionKey);
    if (!traceId) return;
    const existing = rawCache[key];
    if (existing?.json || existing?.loading) return;
    setRawCache((previous) => ({ ...previous, [key]: { loading: true } }));
    void readCompleteRecord(traceId, record.sequence, source)
      .then((raw) => {
        const value: unknown = JSON.parse(raw);
        const json = JSON.stringify(value, null, 2);
        setRawCache((previous) => ({ ...previous, [key]: { loading: false, json } }));
      })
      .catch((error: unknown) => {
        reportArtifactUnavailable(error);
        const message = error instanceof Error ? `Raw record could not be displayed: ${error.message}` : "Raw record could not be displayed.";
        setRawCache((previous) => ({ ...previous, [key]: { loading: false, error: message } }));
      });
  };

  const handleToggleStep = (record: TraceRecord) => {
    const key = `${traceId}:${record.sequence}`;
    const expansionKey = `${key}:step`;
    if (expanded === expansionKey) {
      setExpanded(null);
      return;
    }
    setExpanded(expansionKey);
    if (!traceId) return;
    const existing = stepCache[key];
    if (existing?.detail || existing?.loading) return;
    setStepCache((previous) => ({ ...previous, [key]: { loading: true } }));
    void readCompleteRecord(traceId, record.sequence, source)
      .then((raw) => {
        const detail = parseStepStartedDetail(raw, record.route);
        setStepCache((previous) => ({ ...previous, [key]: { loading: false, detail } }));
      })
      .catch((error: unknown) => {
        reportArtifactUnavailable(error);
        const message = error instanceof Error ? `Step details could not be displayed: ${error.message}` : "Step details could not be displayed.";
        setStepCache((previous) => ({ ...previous, [key]: { loading: false, error: message } }));
      });
  };

  const handleToggleStepAction = (record: TraceRecord, kind: StepActionKind) => {
    const key = `${traceId}:${record.sequence}`;
    const expansionKey = `${key}:step-action`;
    if (expanded === expansionKey) {
      setExpanded(null);
      return;
    }
    setExpanded(expansionKey);
    if (!traceId) return;
    const existing = stepActionCache[key];
    if (existing?.detail || existing?.loading) return;
    setStepActionCache((previous) => ({ ...previous, [key]: { loading: true } }));
    void readStepActionDetail(traceId, record, kind, source)
      .then((detail) => {
        setStepActionCache((previous) => ({ ...previous, [key]: { loading: false, detail } }));
      })
      .catch((error: unknown) => {
        reportArtifactUnavailable(error);
        const message = error instanceof Error ? `Action details could not be displayed: ${error.message}` : "Action details could not be displayed.";
        setStepActionCache((previous) => ({ ...previous, [key]: { loading: false, error: message } }));
      });
  };

  const handleToggleRecordDetail = (record: TraceRecord) => {
    const key = `${traceId}:${record.sequence}`;
    const expansionKey = `${key}:record-detail`;
    if (expanded === expansionKey) {
      setExpanded(null);
      return;
    }
    setExpanded(expansionKey);
    if (!traceId) return;
    const existing = recordDetailCache[key];
    if (existing?.detail || existing?.loading) return;
    setRecordDetailCache((previous) => ({ ...previous, [key]: { loading: true } }));
    const request = record.type === "TOOL_CALL_STARTED"
      ? readToolInputDetail(traceId, record, source)
      : record.type === "TOOL_CALL_COMPLETED"
        ? readToolResultDetail(traceId, record, source)
      : record.type === "STRUCTURED_OUTPUT_RECORDED"
        ? readCompleteRecord(traceId, record.sequence, source).then(parseStructuredOutputDetail)
        : record.type === "EVIDENCE_RECORDED"
          ? readEvidenceDetail(traceId, record, source)
          : record.type === "TRACE_COMPLETED"
            ? readCompleteRecord(traceId, record.sequence, source).then(parseCompletionDetail)
            : readStepCompletedDetail(traceId, record, source);
    void request
      .then((detail) => setRecordDetailCache((previous) => ({ ...previous, [key]: { loading: false, detail } })))
      .catch((error: unknown) => {
        reportArtifactUnavailable(error);
        const message = error instanceof Error ? `Details could not be displayed: ${error.message}` : "Details could not be displayed.";
        setRecordDetailCache((previous) => ({ ...previous, [key]: { loading: false, error: message } }));
      });
  };

  const openRelatedRecord = (record: TraceRecord) => {
    onSelectRecord(record);
    if (record.type === "TOOL_CALL_COMPLETED") {
      handleToggleRecordDetail(record);
    } else if (record.type === "MODEL_RESPONSE_RECEIVED") {
      handleToggleModel(record);
    }
  };

  return <div aria-label="Trace records">
    <h4>Records</h4><div className="trace-table-region" role="region" aria-label="Record list" tabIndex={0}><table className="trace-records-table"><thead><tr><th className="trace-numeric">Sequence</th><th>Type</th><th>Context</th><th className="trace-numeric">Frame duration</th><th>Actions</th></tr></thead><tbody>{records.map((record) => {
      const context = recordContext(record, contextFrames);
      const isModelRequest = record.type === "MODEL_REQUEST_SENT";
      const isModelResponse = record.type === "MODEL_RESPONSE_RECEIVED";
      const isModelRecord = isModelRequest || isModelResponse;
      const isAttemptFailure = record.type === "MODEL_ATTEMPT_FAILED";
      const isStepStarted = record.type === "STEP_STARTED";
      const isToolInput = record.type === "TOOL_CALL_STARTED";
      const isToolResult = record.type === "TOOL_CALL_COMPLETED";
      const isStructuredOutput = record.type === "STRUCTURED_OUTPUT_RECORDED";
      const isStepCompleted = record.type === "STEP_COMPLETED";
      const isEvidence = record.type === "EVIDENCE_RECORDED";
      const isCompletion = record.type === "TRACE_COMPLETED";
      const hasRecordDetail = isToolInput || isToolResult || isStructuredOutput || isStepCompleted || isEvidence || isCompletion;
      const recordDetailLabel = isToolInput ? "Tool input" : isToolResult ? "Tool result" : isStructuredOutput ? "Validation details" : isEvidence ? "Evidence details" : isCompletion ? "Completion details" : "Step result";
      const stepActionKind: StepActionKind | undefined = record.type === "STEP_ACTION_PROPOSED" ? "proposed"
        : record.type === "STEP_ACTION_VALIDATED" ? "validated"
          : record.type === "STEP_ACTION_REJECTED" ? "rejected"
            : undefined;
      const modelLabel = isModelRequest ? "Request" : "Response";
      const modelRegionLabel = isModelRequest ? "Model request" : "Model response";
      const key = `${traceId}:${record.sequence}`;
      const isModelExpanded = expanded === `${key}:model`;
      const isDiffExpanded = expanded === `${key}:diff`;
      const isRawExpanded = expanded === `${key}:raw`;
      const contentRef = record.content?.contentRef;
      const isContentExpanded = activeContentRecordSequence === record.sequence;
      const isAttemptDiagnosticsExpanded = expanded === `${key}:attempt-diagnostics`;
      const isStepExpanded = expanded === `${key}:step`;
      const isStepActionExpanded = expanded === `${key}:step-action`;
      const isRecordDetailExpanded = expanded === `${key}:record-detail`;
      const modelEntry = modelCache[key];
      const rawEntry = rawCache[key];
      const stepEntry = stepCache[key];
      const stepActionEntry = stepActionCache[key];
      const recordDetailEntry = recordDetailCache[key];
      const linkedFailure = record.failureId
        ? failures.find((failure) => failure.failureId === record.failureId)
        : failures.find((failure) => failure.sequence === record.sequence);
      const severity = recordSeverity(record, linkedFailure);
      const severityLabel = severity === "error" ? "Failure" : severity === "warning" ? "Retry or warning" : undefined;
      const isRelatedStepRecord = selectedStepFrameId !== undefined
        && record.frameId === selectedStepFrameId
        && (record.type === "STEP_STARTED" || stepTerminalRecordTypes.has(record.type));
      const isSelectedStepActivity = selectedStepFrameId !== undefined
        && !isRelatedStepRecord
        && (selectedStepStartSequence === undefined || record.sequence > selectedStepStartSequence)
        && (selectedStepTerminalSequence === undefined || record.sequence < selectedStepTerminalSequence)
        && recordBelongsToStep(record, selectedStepFrameId, contextFrames);
      const recordLabel = severityLabel ? `${severityLabel}: record ${record.sequence}, ${record.type}` : `Record ${record.sequence}, ${record.type}`;
      const frameDuration = frameDurationRecordTypes.has(record.type)
        ? framesById.get(record.frameId)?.inclusiveDurationMillis
        : null;
      return (
        <Fragment key={record.sequence}>
          <tr id={`trace-record-${record.sequence}`} className={`trace-record-row${severity === "normal" ? "" : ` trace-record-${severity}`}${isRelatedStepRecord ? " trace-record-related" : ""}${isSelectedStepActivity ? " trace-record-step-context" : ""}`} aria-label={`${recordLabel}${isRelatedStepRecord ? ", related to selected step" : isSelectedStepActivity ? ", part of selected step" : ""}`} aria-current={selectedRecordSequence === record.sequence ? "true" : undefined} tabIndex={0} onClick={(event) => { if (!(event.target as Element).closest("button")) onSelectRecord(record); }} onKeyDown={(event) => { if (event.target === event.currentTarget && (event.key === "Enter" || event.key === " ")) { event.preventDefault(); onSelectRecord(record); } }}>
            <td className="trace-record-identifier trace-numeric">{record.sequence}</td>
            <td className="trace-record-type">{record.type}</td>
            <td className="trace-record-context">{context.label === context.fullPath ? context.label : (
              <TooltipTrigger delay={250} closeDelay={0}>
                <Focusable><span className="trace-context-trigger" tabIndex={0}>{context.label}</span></Focusable>
                <Tooltip className="trace-context-tooltip" placement="top" offset={6}>{context.fullPath}</Tooltip>
              </TooltipTrigger>
            )}</td>
            <td className="trace-numeric">{frameDuration == null ? "—" : <span>{formatDuration(frameDuration)}</span>}</td>
            <td className="trace-record-actions-cell">
              <div className="trace-record-actions">
              <button type="button" aria-expanded={isRawExpanded} aria-controls={`raw-detail-${record.sequence}`} onClick={() => handleToggleRaw(record)}>{isRawExpanded ? "Hide raw record" : "Read raw record"}</button>
              {contentRef && !isModelRecord && !isAttemptFailure && <button type="button" aria-expanded={isContentExpanded} aria-controls={`content-detail-${record.sequence}`} onClick={() => isContentExpanded ? onClearContent() : onContent(contentRef, record.sequence)}>{isContentExpanded ? "Hide content" : "Read content"}</button>}
              {record.type === "PLAN_UPDATED" && <button type="button" aria-expanded={isDiffExpanded} aria-controls={`plan-diff-${record.sequence}`} onClick={() => setExpanded(isDiffExpanded ? null : `${key}:diff`)}>{isDiffExpanded ? "Hide diff" : "View diff"}</button>}
              {record.plan && record.type === "PLAN_CREATED" && <button type="button" aria-label={`View finalized plan ${record.plan.planId} from record ${record.sequence}`} onClick={() => onSelectPlan?.(record.plan!.planId)}>View finalized plan</button>}
              {record.plan && record.type === "PLAN_UPDATED" && <button type="button" aria-label={`View plan change ${record.plan.planId} from record ${record.sequence}`} onClick={() => onSelectPlan?.(record.plan!.planId, record.sequence)}>View plan change</button>}
              {contentRef && isAttemptFailure && traceId && <button type="button" aria-expanded={isAttemptDiagnosticsExpanded} aria-controls={`attempt-diagnostics-${record.sequence}`} onClick={() => setExpanded(isAttemptDiagnosticsExpanded ? null : `${key}:attempt-diagnostics`)}>{isAttemptDiagnosticsExpanded ? "Hide attempt diagnostics" : "Attempt diagnostics"}</button>}
              {record.type === "ERROR_RECORDED" && linkedFailure && <button className="trace-error-action" type="button" aria-pressed={selectedFailureId === linkedFailure.failureId} onClick={() => onSelectFailure(linkedFailure.failureId)}>View error</button>}
              {isModelRecord && traceId && (
                <button type="button" aria-expanded={isModelExpanded} aria-controls={`model-detail-${record.sequence}`} onClick={() => handleToggleModel(record)}>
                  {isModelExpanded ? `Hide ${modelLabel.toLowerCase()}` : modelLabel}
                </button>
              )}
              {isStepStarted && traceId && (
                <button type="button" aria-expanded={isStepExpanded} aria-controls={`step-detail-${record.sequence}`} onClick={() => handleToggleStep(record)}>
                  {isStepExpanded ? "Hide step details" : "Step details"}
                </button>
              )}
              {stepActionKind && traceId && (
                <button type="button" aria-expanded={isStepActionExpanded} aria-controls={`step-action-detail-${record.sequence}`} onClick={() => handleToggleStepAction(record, stepActionKind)}>
                  {isStepActionExpanded ? "Hide action details" : "Action details"}
                </button>
              )}
              {hasRecordDetail && traceId && (
                <button type="button" aria-expanded={isRecordDetailExpanded} aria-controls={`record-detail-${record.sequence}`} onClick={() => handleToggleRecordDetail(record)}>
                  {isRecordDetailExpanded ? `Hide ${recordDetailLabel.toLowerCase()}` : recordDetailLabel}
                </button>
              )}
              </div>
            </td>
          </tr>
          {isDiffExpanded && <tr className="trace-record-detail-row"><td colSpan={5}>
            <div id={`plan-diff-${record.sequence}`} className="trace-plan-diff trace-expanded" role="region" aria-label={`Plan diff for record ${record.sequence}`}>
              {record.plan && <p className="trace-plan-diff-heading">Skill: {record.plan.capabilityName}, Plan: <code>{record.plan.planId}</code></p>}
              {record.planUpdate && <p className="trace-plan-diff-range">Record {record.planUpdate.previousSequence} → Record {record.sequence}</p>}
              {!record.planUpdate || !record.plan ? <p role="status">Plan diff unavailable.</p>
                : record.planUpdate.availability === "LIMIT_EXCEEDED" ? <p role="status">Plan diff unavailable: the complete comparison exceeds the evidence delivery limit.</p>
                : record.planUpdate.changes.length === 0 ? <p className="trace-step-note">No changes to plan status, task status, or task note.</p>
                : <dl className="trace-plan-fields">{record.planUpdate.changes.map((change, index) => <div className="trace-plan-field" key={index}>
                    <dt><code>{change.taskId === undefined ? change.field : `tasks[${change.taskId}].${change.field}`}</code></dt>
                    <dd><span className="trace-plan-field-value">{change.before === null ? "null" : `"${change.before}"`}</span><span className="trace-plan-field-arrow" role="img" aria-label="changed to"> → </span><span className="trace-plan-field-value">{change.after === null ? "null" : `"${change.after}"`}</span></dd>
                  </div>)}</dl>}
            </div>
          </td></tr>}
          {isRawExpanded && (
            <tr key={`${record.sequence}-raw`} className="trace-record-detail-row">
              <td colSpan={5}>
                <div id={`raw-detail-${record.sequence}`} className="trace-raw-expanded trace-expanded" role="region" aria-label={`Raw record ${record.sequence}`}>
                  {!traceId && <p role="status">Trace context unavailable.</p>}
                  {traceId && rawEntry?.loading && <p role="status">Loading raw record&hellip;</p>}
                  {rawEntry?.error && <p role="alert">{rawEntry.error}</p>}
                  {rawEntry?.json && <pre>{rawEntry.json}</pre>}
                </div>
              </td>
            </tr>
          )}
          {isContentExpanded && (
            <tr key={`${record.sequence}-content`} className="trace-record-detail-row">
              <td colSpan={5}>
                <div id={`content-detail-${record.sequence}`} className="trace-raw-expanded trace-expanded">
                  <TraceEvidenceDetail range={contentRange} pending={contentPending} error={contentError} label={`Content for record ${record.sequence}`} onNext={onNextContent} onClear={onClearContent} />
                </div>
              </td>
            </tr>
          )}
          {isAttemptDiagnosticsExpanded && contentRef && traceId && (
            <tr key={`${record.sequence}-attempt-diagnostics`} className="trace-record-detail-row">
              <td colSpan={5}>
                <div id={`attempt-diagnostics-${record.sequence}`} className="trace-attempt-diagnostics-expanded trace-expanded">
                  <TraceAttemptDiagnostics onArtifactUnavailable={onArtifactUnavailable} traceId={traceId} source={source} recordSequence={record.sequence} contentRef={contentRef} scopeGeneration={scopeGeneration} verifyScope={verifyScope} />
                </div>
              </td>
            </tr>
          )}
          {isModelRecord && isModelExpanded && (
            <tr key={`${record.sequence}-model`} className="trace-record-detail-row">
              <td colSpan={5}>
                <div id={`model-detail-${record.sequence}`} className="trace-model-expanded trace-expanded" role="region" aria-label={`${modelRegionLabel} for record ${record.sequence}`}>
                  <h5 className="trace-record-context">{context.fullPath}</h5>
                  {!traceId && <p role="status">Trace context unavailable.</p>}
                  {traceId && modelEntry?.loading && <p role="status">Loading {modelLabel.toLowerCase()}&hellip;</p>}
                  {modelEntry?.error && <p role="alert">{modelEntry.error}</p>}
                  {modelEntry?.detail && <ModelDetailView detail={modelEntry.detail} />}
                </div>
              </td>
            </tr>
          )}
          {isStepStarted && isStepExpanded && (
            <tr key={`${record.sequence}-step`} className="trace-record-detail-row">
              <td colSpan={5}>
                <div id={`step-detail-${record.sequence}`} className="trace-step-expanded trace-expanded" role="region" aria-label={`Step details for record ${record.sequence}`}>
                  {!traceId && <p role="status">Trace context unavailable.</p>}
                  {traceId && stepEntry?.loading && <p role="status">Loading step details&hellip;</p>}
                  {stepEntry?.error && <p role="alert">{stepEntry.error}</p>}
                  {stepEntry?.detail && <>
                    <dl className="trace-step-facts">
                      <div><dt>Skill</dt><dd>{stepEntry.detail.skillName}</dd></div>
                      <div><dt>Step</dt><dd>{stepEntry.detail.stepNumber}</dd></div>
                      <div><dt>Ready tasks</dt><dd>{stepEntry.detail.readyTasks}</dd></div>
                      <div><dt>Plan status</dt><dd>{stepEntry.detail.planStatus.toLowerCase().replaceAll("_", " ")}</dd></div>
                    </dl>
                    <p className="trace-step-note">No task or action has been selected yet. That decision is recorded by the later STEP_ACTION_PROPOSED record.</p>
                  </>}
                </div>
              </td>
            </tr>
          )}
          {stepActionKind && isStepActionExpanded && (
            <tr key={`${record.sequence}-step-action`} className="trace-record-detail-row">
              <td colSpan={5}>
                <div id={`step-action-detail-${record.sequence}`} className={`trace-step-expanded trace-expanded${stepActionKind === "rejected" ? " trace-step-rejected" : ""}`} role="region" aria-label={`Action details for record ${record.sequence}`}>
                  {!traceId && <p role="status">Trace context unavailable.</p>}
                  {traceId && stepActionEntry?.loading && <p role="status">Loading action details&hellip;</p>}
                  {stepActionEntry?.error && <p role="alert">{stepActionEntry.error}</p>}
                  {stepActionEntry?.detail && <StepActionDetailView detail={stepActionEntry.detail} />}
                </div>
              </td>
            </tr>
          )}
          {hasRecordDetail && isRecordDetailExpanded && (
            <tr key={`${record.sequence}-record-detail`} className="trace-record-detail-row">
              <td colSpan={5}>
                <div id={`record-detail-${record.sequence}`} className={`trace-step-expanded trace-expanded${recordDetailEntry?.detail?.kind === "structured-output" && recordDetailEntry.detail.status !== "PASSED" ? " trace-step-rejected" : ""}`} role="region" aria-label={`${recordDetailLabel} for record ${record.sequence}`}>
                  {!traceId && <p role="status">Trace context unavailable.</p>}
                  {traceId && recordDetailEntry?.loading && <p role="status">Loading details&hellip;</p>}
                  {recordDetailEntry?.error && <p role="alert">{recordDetailEntry.error}</p>}
                  {recordDetailEntry?.detail && <RecordDetailView detail={recordDetailEntry.detail} onOpenRelated={openRelatedRecord} onSelectFailure={onSelectFailure} />}
                </div>
              </td>
            </tr>
          )}
        </Fragment>
      );
    })}</tbody></table></div>
  </div>;
}
