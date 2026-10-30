import { render, screen } from "@testing-library/react";
import { expect, test } from "vitest";
import type { Trace } from "../api/contracts";
import { TraceSummary } from "./TraceSummary";

test("qualified entry and captured target generations remain distinct", () => {
 const trace: Trace = {targetScopeId:"scope",traceId:"trace",sessionId:"session",entrySkill:"orders-search",entryNamespace:"orders",generationId:"entry-generation",namespaceGenerationIds:{orders:"entry-generation",tax:"removed-target-generation"},outcome:"SUCCEEDED",finalizedAt:"2026-10-30T00:00:00Z",sizeBytes:10,persistencePolicy:"ALWAYS",applicationTraceExpiresAt:"2026-10-31T00:00:00Z",localAvailable:true};
 render(<TraceSummary trace={trace}/>);
 expect(screen.getByText("orders-search")).toBeInTheDocument();
 expect(screen.getByText("Entry generation")).toBeInTheDocument();
 expect(screen.getByText("entry-generation")).toBeInTheDocument();
 expect(screen.getByText("orders: entry-generation, tax: removed-target-generation")).toBeInTheDocument();
});
