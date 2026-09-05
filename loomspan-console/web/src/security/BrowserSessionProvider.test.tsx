import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import { BrowserSessionProvider, useBrowserSession } from "./BrowserSessionProvider";

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

function SessionProbe() {
  const session = useBrowserSession();
  return (
    <div>
      <span>{session.status}</span>
      {session.status === "paired" ? <span>{session.bootstrap.workspacePath}</span> : null}
    </div>
  );
}

test("bootstraps with a valid cookie and keeps CSRF only in memory", async () => {
  const fetch = vi.fn().mockResolvedValue(
    new Response(
      JSON.stringify({
        processId: "process",
        workspacePath: "C:/work",
        tabId: "tab",
        csrfToken: "CSRF_SECRET_SENTINEL",
      }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    ),
  );
  vi.stubGlobal("fetch", fetch);
  render(
    <BrowserSessionProvider>
      <SessionProbe />
    </BrowserSessionProvider>,
  );
  await screen.findByText("C:/work");
  expect(document.body).not.toHaveTextContent("CSRF_SECRET_SENTINEL");
  expect(localStorage.length).toBe(0);
  expect(sessionStorage.length).toBe(0);
});

test("session rejection clears security state and renders unpaired", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({ error: { code: "SESSION_REQUIRED", message: "Pairing is required." } }),
        { status: 401, headers: { "Content-Type": "application/json" } },
      ),
    ),
  );
  render(
    <BrowserSessionProvider>
      <SessionProbe />
    </BrowserSessionProvider>,
  );
  await waitFor(() => expect(screen.getByText("unpaired")).toBeVisible());
});

test("heartbeats an open visible tab with its current memory credentials", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          processId: "process",
          workspacePath: "workspace",
          tabId: "tab",
          csrfToken: "csrf",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    )
    .mockResolvedValueOnce(
      new Response(JSON.stringify({ active: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
  vi.stubGlobal("fetch", fetch);
  render(
    <BrowserSessionProvider>
      <SessionProbe />
    </BrowserSessionProvider>,
  );
  await screen.findByText("workspace");
  fireEvent(document, new Event("visibilitychange"));
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
  expect(fetch.mock.calls[1]?.[0]).toBe("/api/console/v1/tabs/heartbeat");
});

test("heartbeats an open tab before the disconnected registration expires", async () => {
  vi.useFakeTimers();
  const fetch = vi.fn().mockImplementation(async (input: string) => {
    if (input === "/api/console/v1/bootstrap") {
      return new Response(
        JSON.stringify({
          processId: "process",
          workspacePath: "workspace",
          tabId: "tab",
          csrfToken: "csrf",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );
    }
    return new Response(JSON.stringify({ active: true }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  });
  vi.stubGlobal("fetch", fetch);
  render(
    <BrowserSessionProvider>
      <SessionProbe />
    </BrowserSessionProvider>,
  );
  await vi.waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
  await act(async () => {
    await vi.advanceTimersByTimeAsync(60_000);
  });
  await vi.waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
  expect(fetch.mock.calls[1]?.[0]).toBe("/api/console/v1/tabs/heartbeat");
});

test("does not re-bootstrap when page disposal races a rejected heartbeat", async () => {
  let resolveHeartbeat!: (response: Response) => void;
  const pendingHeartbeat = new Promise<Response>((resolve) => {
    resolveHeartbeat = resolve;
  });
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          processId: "process",
          workspacePath: "workspace",
          tabId: "tab",
          csrfToken: "csrf",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    )
    .mockReturnValueOnce(pendingHeartbeat)
    .mockResolvedValueOnce(
      new Response(JSON.stringify({ released: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
  vi.stubGlobal("fetch", fetch);
  render(
    <BrowserSessionProvider>
      <SessionProbe />
    </BrowserSessionProvider>,
  );
  await screen.findByText("workspace");
  fireEvent(document, new Event("visibilitychange"));
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
  fireEvent(window, new PageTransitionEvent("pagehide"));
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(3));
  await act(async () => {
    resolveHeartbeat(
      new Response(
        JSON.stringify({
          error: { code: "BROWSER_SECURITY_REJECTED", message: "Browser request rejected." },
        }),
        { status: 403, headers: { "Content-Type": "application/json" } },
      ),
    );
    await pendingHeartbeat;
  });
  expect(fetch).toHaveBeenCalledTimes(3);
});
