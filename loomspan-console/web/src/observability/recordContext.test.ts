import { expect, test } from "vitest";
import type { TraceFrame, TraceRecord } from "../api/contracts";
import { recordContext } from "./recordContext";

type Frame = Pick<TraceFrame, "frameId" | "parentFrameId" | "frameType" | "route">;
function frame(frameId: string, parentFrameId: string | null, frameType: string, route: string): Frame {
  return { frameId, parentFrameId, frameType, route };
}
function context(current: Frame, ancestors: Frame[] = []) {
  const record: TraceRecord = {
    ...current, parentFrameId: current.parentFrameId ?? "", sequence: 56,
    type: "MODEL_RESPONSE_RECEIVED", threadName: "worker", timestampMillis: 1,
    representation: "logical", isChunk: false, isEnvelope: false,
  };
  return recordContext(record, new Map(ancestors.map((item) => [item.frameId, item])));
}
function label(current: Frame, ancestors: Frame[] = []) { return context(current, ancestors).fullPath; }

test("compact labels retain only the owning invocation without splitting skill names", () => {
  expect(context(frame("model", "child", "MODEL_CALL", "child → name#step-1-model"), [
    frame("child", "parent-step", "SKILL_EXECUTION", "child → name"),
    frame("parent-step", "root", "STEP_EXECUTION", "planTrip#step-2"),
    frame("root", null, "ROOT_MISSION", "planTrip"),
  ])).toEqual({ label: "child → name / Step 1", fullPath: "planTrip / Step 2 → child → name / Step 1" });
  expect(context(frame("model", "missing", "MODEL_CALL", "child#step-1-model")))
    .toEqual({ label: "child / Step 1", fullPath: "Parent context unavailable → child / Step 1" });
});

test.each([
  ["MODEL_CALL", "hotelSelection#step-3-model", "hotelSelection / Step 3"],
  ["STEP_EXECUTION", "hotelSelection#step-3", "hotelSelection / Step 3"],
  ["MODEL_CALL", "hotelSelection#planning-model", "hotelSelection / Planning"],
  ["MODEL_CALL", "hotelSelection#mission-model", "hotelSelection / Mission"],
  ["PLANNING", "hotelSelection#planning", "hotelSelection / Planning"],
  ["ROOT_MISSION", "hotelSelection", "hotelSelection"],
  ["SKILL_EXECUTION", "hotelSelection", "hotelSelection"],
])("formats recorded %s route %s", (type, route, expected) => {
  expect(label(frame("current", null, type, route))).toBe(expected);
});

test("follows frame identity through interleaved and repeated nested skills", () => {
  const ancestors = [
    frame("root", null, "ROOT_MISSION", "parent"),
    frame("outer-step", "root", "STEP_EXECUTION", "parent#step-4"),
    frame("child-a", "outer-step", "SKILL_EXECUTION", "child"),
    frame("step-a", "child-a", "STEP_EXECUTION", "child#step-1"),
    frame("other-step", "root", "STEP_EXECUTION", "parent#step-9"),
    frame("child-b", "other-step", "SKILL_EXECUTION", "child"),
    frame("step-b", "child-b", "STEP_EXECUTION", "child#step-2"),
  ];
  expect(label(frame("model-b", "step-b", "MODEL_CALL", "child#step-2-model"), ancestors))
    .toBe("parent / Step 9 → child / Step 2");
  expect(label(frame("model-a", "step-a", "MODEL_CALL", "child#step-1-model"), ancestors.reverse()))
    .toBe("parent / Step 4 → child / Step 1");
});

test("does not inherit a caller's step into a child mission or unknown model phase", () => {
  const ancestors = [frame("root", null, "ROOT_MISSION", "parent"),
    frame("outer-step", "root", "STEP_EXECUTION", "parent#step-4"),
    frame("child", "outer-step", "SKILL_EXECUTION", "child")];
  expect(label(frame("model", "child", "MODEL_CALL", "child#mission-model"), ancestors))
    .toBe("parent / Step 4 → child / Mission");
  expect(label(frame("model", "child", "MODEL_CALL", "unknown"), ancestors))
    .toBe("parent / Step 4 → child / Model context unavailable");
});

test("tool records use their enclosing skill step rather than treating the tool name as a skill", () => {
  expect(label(frame("tool", "step", "TOOL_INVOCATION", "lookupHotels"), [
    frame("step", "root", "STEP_EXECUTION", "hotelSelection#step-3"),
    frame("root", null, "ROOT_MISSION", "hotelSelection"),
  ])).toBe("hotelSelection / Step 3");
});

test("does not repeat the nested mission wrapper but preserves recursive invocations", () => {
  expect(label(frame("model", "nested-root", "MODEL_CALL", "skill#mission-model"), [
    frame("nested-root", "nested-skill", "ROOT_MISSION", "skill"),
    frame("nested-skill", "step", "SKILL_EXECUTION", "skill"),
    frame("step", "root", "STEP_EXECUTION", "skill#step-3"),
    frame("root", null, "ROOT_MISSION", "skill"),
  ])).toBe("skill / Step 3 → skill / Mission");
});

test("retains known labels when ancestry is absent or cyclic", () => {
  const model = frame("model", "missing", "MODEL_CALL", "child#step-2-model");
  expect(label(model)).toBe("Parent context unavailable → child / Step 2");
  expect(label({ ...model, parentFrameId: "model" }, [model]))
    .toBe("Parent context unavailable → child / Step 2");
  expect(label(frame("model", null, "MODEL_CALL", "#step-2-model")))
    .toBe("Skill unavailable / Step 2");
});

test.each(["skill#step-0-model", "skill#step-9007199254740992-model", "unrecognized", ""])(
  "does not invent context for unsupported route %s", (route) => {
    expect(label(frame("uuid", null, "MODEL_CALL", route))).toBe("Context unavailable");
  },
);

test("a missing step label stays explicit and never uses projected plan task position", () => {
  const current = { ...frame("step", "root", "STEP_EXECUTION", ""), stepNumber: 99 };
  expect(label(current, [frame("root", null, "ROOT_MISSION", "skill")]))
    .toBe("skill / Step unavailable");
});

test("fills a missing route skill from its owning frame while retaining the recorded step", () => {
  expect(label(frame("model", "root", "MODEL_CALL", "#step-2-model"), [
    frame("root", null, "ROOT_MISSION", "hotelSelection"),
  ])).toBe("hotelSelection / Step 2");
});

test("preserves missing step context on tool and parent-skill ancestry", () => {
  const ancestors = [frame("step", "root", "STEP_EXECUTION", ""),
    frame("root", null, "ROOT_MISSION", "hotelSelection")];
  expect(label(frame("tool", "step", "TOOL_INVOCATION", "lookupHotels"), ancestors))
    .toBe("hotelSelection / Step unavailable");
  expect(label(frame("child", "step", "SKILL_EXECUTION", "child"), ancestors))
    .toBe("hotelSelection / Step unavailable → child");
});
