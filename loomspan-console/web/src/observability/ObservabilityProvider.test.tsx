import { act, render, screen } from "@testing-library/react";
import { beforeEach, expect, test, vi } from "vitest";
import { useEffect } from "react";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router";

const targetView = vi.hoisted(() => ({
  current: {
    target: {
      status: {
        targetScopeId: "scope-1",
        targetSelection: "SELECTED" as const,
        targetAuthentication: "ESTABLISHED" as const,
      },
    },
    scopeGeneration: 0,
    refresh: vi.fn().mockResolvedValue(undefined),
  },
}));

vi.mock("../target/TargetProvider", () => ({
  useTarget: () => targetView.current,
}));

vi.mock("../api/client", () => ({
  getObservabilityInstance: vi.fn(),
  listSkills: vi.fn(),
  listActiveExecutions: vi.fn(),
  listTraces: vi.fn(),
  BrowserAPIError: class BrowserAPIError extends Error {
    code: string;
    status: number;
    constructor(code: string, message: string, status: number) {
      super(message);
      this.code = code;
      this.status = status;
    }
  },
}));

import {
  BrowserAPIError,
  getObservabilityInstance,
  listActiveExecutions,
  listSkills,
  listTraces,
} from "../api/client";
import { ObservabilityProvider, useObservability } from "./ObservabilityProvider";
import { SkillCatalog } from "./SkillCatalog";

function Consumer() {
  const { instance, loadInstance } = useObservability();
  useEffect(() => {
    if (instance === null) void loadInstance();
  }, [instance, loadInstance]);
  return (
    <div>
      <span data-testid="instance-status">
        {instance?.status ? "loaded" : "empty"}
      </span>
      <button type="button" onClick={() => void loadInstance()}>
        refresh
      </button>
    </div>
  );
}

beforeEach(() => {
  vi.mocked(getObservabilityInstance).mockReset();
  vi.mocked(listSkills).mockReset();
  vi.mocked(listActiveExecutions).mockReset();
  vi.mocked(listTraces).mockReset();
  targetView.current = {
    target: {
      status: {
        targetScopeId: "scope-1",
        targetSelection: "SELECTED" as const,
        targetAuthentication: "ESTABLISHED" as const,
      },
    },
    scopeGeneration: 0,
    refresh: vi.fn().mockResolvedValue(undefined),
  };
});

function SkillsConsumer() {
  const { skills, loadSkills } = useObservability();
  return (
    <div>
      <span data-testid="skills">{skills.items.map((item) => (item as { registeredName: string }).registeredName).join(",")}</span>
      <span data-testid="skills-error">{skills.error?.code ?? ""}</span>
      <button type="button" onClick={() => void loadSkills("stale-cursor")}>continue</button>
      <button type="button" onClick={() => void loadSkills("old-cursor")}>old</button>
      <button type="button" onClick={() => void loadSkills()}>new</button>
    </div>
  );
}

function CollectionsConsumer() {
  const {
    activeExecutions,
    traces,
    loadActiveExecutions,
    loadTraces,
  } = useObservability();
  return (
    <div>
      <span data-testid="active-items">{activeExecutions.items.length}</span>
      <span data-testid="trace-items">{traces.items.length}</span>
      <span data-testid="trace-error">{traces.error?.code ?? ""}</span>
      <button type="button" onClick={() => void loadActiveExecutions("active-cursor")}>active</button>
      <button type="button" onClick={() => void loadTraces("trace-cursor")}>traces</button>
    </div>
  );
}

test("provider fetches instance status when consumer requests it on mount", async () => {
  vi.mocked(getObservabilityInstance).mockResolvedValue({
    targetScopeId: "scope-1",
    instanceId: "test-id",
    consoleCompatibilityVersion: "1.0.0-beta.2-SNAPSHOT",
    observedAt: "2026-07-27T00:00:00Z",
    liveMonitoringAvailable: true,
    registeredSkillCount: 0,
    activeExecutionCount: 0,
    catalogedTraceCount: 0,
    tracePersistencePolicy: "PERSISTENT",
    completionGraceTtl: "PT2M",
    traceCatalogMetadataTtl: "PT168H",
  });
  render(
    <ObservabilityProvider>
      <Consumer />
    </ObservabilityProvider>,
  );
  await vi.waitFor(() => {
    expect(screen.getByTestId("instance-status").textContent).toBe("loaded");
  });
  expect(getObservabilityInstance).toHaveBeenCalledTimes(1);
});

test("provider does not auto-fetch without consumer requesting it", () => {
  function PassiveConsumer() {
    useObservability();
    return <div data-testid="passive" />;
  }
  render(
    <ObservabilityProvider>
      <PassiveConsumer />
    </ObservabilityProvider>,
  );
  expect(getObservabilityInstance).not.toHaveBeenCalled();
});

test("scopeGeneration change resets state", async () => {
  vi.mocked(getObservabilityInstance).mockResolvedValue({
    targetScopeId: "scope-1",
    instanceId: "test-id",
    consoleCompatibilityVersion: "1.0.0-beta.2-SNAPSHOT",
    observedAt: "2026-07-27T00:00:00Z",
    liveMonitoringAvailable: true,
    registeredSkillCount: 0,
    activeExecutionCount: 0,
    catalogedTraceCount: 0,
    tracePersistencePolicy: "PERSISTENT",
    completionGraceTtl: "PT2M",
    traceCatalogMetadataTtl: "PT168H",
  });
  const { rerender } = render(
    <ObservabilityProvider>
      <Consumer />
    </ObservabilityProvider>,
  );
  await vi.waitFor(() => {
    expect(screen.getByTestId("instance-status").textContent).toBe("loaded");
  });
  targetView.current.scopeGeneration = 1;
  rerender(
    <ObservabilityProvider>
      <Consumer />
    </ObservabilityProvider>,
  );
  await vi.waitFor(() => {
    expect(screen.getByTestId("instance-status").textContent).toBe("empty");
  });
});

test("manual refresh calls loadInstance again", async () => {
  vi.mocked(getObservabilityInstance).mockResolvedValue({
    targetScopeId: "scope-1",
    instanceId: "test-id",
    consoleCompatibilityVersion: "1.0.0-beta.2-SNAPSHOT",
    observedAt: "2026-07-27T00:00:00Z",
    liveMonitoringAvailable: true,
    registeredSkillCount: 0,
    activeExecutionCount: 0,
    catalogedTraceCount: 0,
    tracePersistencePolicy: "PERSISTENT",
    completionGraceTtl: "PT2M",
    traceCatalogMetadataTtl: "PT168H",
  });
  render(
    <ObservabilityProvider>
      <Consumer />
    </ObservabilityProvider>,
  );
  await vi.waitFor(() => {
    expect(getObservabilityInstance).toHaveBeenCalledTimes(1);
  });
  screen.getByText("refresh").click();
  await vi.waitFor(() => {
    expect(getObservabilityInstance).toHaveBeenCalledTimes(2);
  });
});

test("successful empty collection loads only once", async () => {
  vi.mocked(listSkills).mockResolvedValue({
    targetScopeId: "scope-1",
    items: [],
    hasMore: false,
    nextCursor: null,
    observedAt: "2026-07-27T00:00:00Z",
  });
  render(
    <MemoryRouter>
      <ObservabilityProvider>
        <SkillCatalog />
      </ObservabilityProvider>
    </MemoryRouter>,
  );
  await vi.waitFor(() => {
    expect(listSkills).toHaveBeenCalledTimes(1);
    expect(screen.getByText("No skills are registered.")).toBeInTheDocument();
  });
  await new Promise((resolve) => setTimeout(resolve, 25));
  expect(listSkills).toHaveBeenCalledTimes(1);
});

test("expired pagination cursor restarts from the first page", async () => {
  vi.mocked(listSkills)
    .mockRejectedValueOnce(new BrowserAPIError("STALE_CURSOR", "expired", 409))
    .mockResolvedValueOnce({
      targetScopeId: "scope-1",
      items: [{ registeredName: "fresh", source: "YAML", sourcePath: "classpath:/fresh.yaml" }],
      hasMore: false,
      nextCursor: null,
      observedAt: "2026-07-27T00:00:00Z",
    });
  render(
    <ObservabilityProvider>
      <SkillsConsumer />
    </ObservabilityProvider>,
  );
  screen.getByText("continue").click();
  await vi.waitFor(() => {
    expect(screen.getByTestId("skills").textContent).toBe("fresh");
  });
  expect(vi.mocked(listSkills).mock.calls).toEqual([["stale-cursor"], []]);
});

test("invalid pagination cursor remains a visible failure", async () => {
  vi.mocked(listSkills).mockRejectedValueOnce(
    new BrowserAPIError("INVALID_CURSOR", "invalid", 400),
  );
  render(
    <ObservabilityProvider>
      <SkillsConsumer />
    </ObservabilityProvider>,
  );
  screen.getByText("continue").click();
  await vi.waitFor(() => {
    expect(screen.getByTestId("skills-error").textContent).toBe("INVALID_CURSOR");
  });
  expect(listSkills).toHaveBeenCalledTimes(1);
});

test("response from another scope is rejected and refreshes target state", async () => {
  vi.mocked(listSkills).mockResolvedValue({
    targetScopeId: "scope-2",
    items: [],
    hasMore: false,
    nextCursor: null,
    observedAt: "2026-07-27T00:00:00Z",
  });
  render(
    <ObservabilityProvider>
      <SkillsConsumer />
    </ObservabilityProvider>,
  );
  screen.getByText("new").click();
  await vi.waitFor(() => {
    expect(screen.getByTestId("skills-error").textContent).toBe("TARGET_CHANGED");
  });
  expect(targetView.current.refresh).toHaveBeenCalledTimes(1);
});

test("TARGET_CHANGED error refreshes authoritative target state", async () => {
  vi.mocked(listSkills).mockRejectedValue(
    new BrowserAPIError("TARGET_CHANGED", "changed", 409),
  );
  render(
    <ObservabilityProvider>
      <SkillsConsumer />
    </ObservabilityProvider>,
  );
  screen.getByText("new").click();
  await vi.waitFor(() => {
    expect(targetView.current.refresh).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("skills-error").textContent).toBe("TARGET_CHANGED");
  });
});

test("active and trace collections load scope-bound pages", async () => {
  vi.mocked(listActiveExecutions).mockResolvedValue({
    targetScopeId: "scope-1",
    items: [],
    hasMore: false,
    nextCursor: null,
    resumeCursor: "resume-1",
    observedAt: "2026-07-27T00:00:00Z",
  });
  vi.mocked(listTraces).mockResolvedValue({
    targetScopeId: "scope-1",
    items: [],
    hasMore: false,
    nextCursor: null,
    observedAt: "2026-07-27T00:00:00Z",
  });
  render(
    <ObservabilityProvider>
      <CollectionsConsumer />
    </ObservabilityProvider>,
  );
  screen.getByText("active").click();
  screen.getByText("traces").click();
  await vi.waitFor(() => {
    expect(listActiveExecutions).toHaveBeenCalledWith("active-cursor");
    expect(listTraces).toHaveBeenCalledWith("trace-cursor");
    expect(screen.getByTestId("active-items").textContent).toBe("0");
    expect(screen.getByTestId("trace-items").textContent).toBe("0");
  });
});

test("stale active cursor restarts while invalid trace cursor remains visible", async () => {
  vi.mocked(listActiveExecutions)
    .mockRejectedValueOnce(new BrowserAPIError("STALE_CURSOR", "stale", 409))
    .mockResolvedValueOnce({
      targetScopeId: "scope-1",
      items: [],
      hasMore: false,
      nextCursor: null,
      resumeCursor: "resume-2",
      observedAt: "2026-07-27T00:00:00Z",
    });
  vi.mocked(listTraces).mockRejectedValue(
    new BrowserAPIError("INVALID_CURSOR", "invalid", 400),
  );
  render(
    <ObservabilityProvider>
      <CollectionsConsumer />
    </ObservabilityProvider>,
  );
  screen.getByText("active").click();
  screen.getByText("traces").click();
  await vi.waitFor(() => {
    expect(vi.mocked(listActiveExecutions).mock.calls).toEqual([
      ["active-cursor"],
      [],
    ]);
    expect(screen.getByTestId("trace-error").textContent).toBe("INVALID_CURSOR");
  });
});

test("older pagination response cannot overwrite a newer refresh", async () => {
  let resolveOld!: (value: Awaited<ReturnType<typeof listSkills>>) => void;
  let resolveNew!: (value: Awaited<ReturnType<typeof listSkills>>) => void;
  const oldPage = new Promise<Awaited<ReturnType<typeof listSkills>>>((resolve) => {
    resolveOld = resolve;
  });
  const newPage = new Promise<Awaited<ReturnType<typeof listSkills>>>((resolve) => {
    resolveNew = resolve;
  });
  vi.mocked(listSkills)
    .mockReturnValueOnce(oldPage)
    .mockReturnValueOnce(newPage);
  render(
    <ObservabilityProvider>
      <SkillsConsumer />
    </ObservabilityProvider>,
  );
  screen.getByText("old").click();
  screen.getByText("new").click();
  await act(async () => {
    resolveNew({
      targetScopeId: "scope-1",
      items: [{ registeredName: "new", source: "YAML", sourcePath: "classpath:/new.yaml" }],
      hasMore: false,
      nextCursor: null,
      observedAt: "2026-07-27T00:00:00Z",
    });
  });
  await vi.waitFor(() => {
    expect(screen.getByTestId("skills").textContent).toBe("new");
  });
  await act(async () => {
    resolveOld({
      targetScopeId: "scope-1",
      items: [{ registeredName: "old", source: "YAML", sourcePath: "classpath:/old.yaml" }],
      hasMore: false,
      nextCursor: null,
      observedAt: "2026-07-27T00:00:00Z",
    });
  });
  expect(screen.getByTestId("skills").textContent).toBe("new");
});
