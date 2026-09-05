import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import { BrowserSessionProvider, useBrowserSession } from "./BrowserSessionProvider";
import { PairingPage } from "./PairingPage";

afterEach(() => vi.unstubAllGlobals());

function jsonResponse(value: object, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function Probe() {
  const session = useBrowserSession();
  return <span data-testid="session-status">{session.status}</span>;
}

test("pairs a manually entered value without rendering or storing it", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(
      jsonResponse({ error: { code: "SESSION_REQUIRED", message: "Pairing is required." } }, 401),
    )
    .mockResolvedValueOnce(jsonResponse({ paired: true }))
    .mockResolvedValueOnce(
      jsonResponse({
        processId: "process",
        workspacePath: "workspace",
        tabId: "tab",
        csrfToken: "csrf",
      }),
    );
  vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();
  render(
    <BrowserSessionProvider>
      <PairingPage />
      <Probe />
    </BrowserSessionProvider>,
  );
  await screen.findByRole("heading", { name: "Pair this browser" });
  const secret = "PAIRING_SECRET_SENTINEL";
  await user.type(screen.getByLabelText("One-time pairing value"), secret);
  await user.click(screen.getByRole("button", { name: "Pair browser" }));
  await waitFor(() => expect(screen.getByTestId("session-status")).toHaveTextContent("paired"));
  expect(document.body).not.toHaveTextContent(secret);
  expect(localStorage.length).toBe(0);
  expect(sessionStorage.length).toBe(0);
});

test("requests a terminal-only manual challenge", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(
      jsonResponse({ error: { code: "SESSION_REQUIRED", message: "Pairing is required." } }, 401),
    )
    .mockResolvedValueOnce(jsonResponse({ challengePrinted: true }, 202));
  vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();
  render(
    <BrowserSessionProvider>
      <PairingPage />
    </BrowserSessionProvider>,
  );
  await user.click(await screen.findByRole("button", { name: "Print a new pairing value" }));
  await screen.findByText("A new pairing value was printed in the Console terminal.");
  expect(fetch.mock.calls[1]?.[0]).toBe("/api/console/v1/pairing/challenge");
});

test("renders a bounded manual challenge rate-limit message", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(
      jsonResponse({ error: { code: "SESSION_REQUIRED", message: "Pairing is required." } }, 401),
    )
    .mockResolvedValueOnce(
      jsonResponse(
        { error: { code: "RATE_LIMITED", message: "A pairing challenge is already available." } },
        429,
      ),
    );
  vi.stubGlobal("fetch", fetch);
  const user = userEvent.setup();
  render(
    <BrowserSessionProvider>
      <PairingPage />
    </BrowserSessionProvider>,
  );
  await user.click(await screen.findByRole("button", { name: "Print a new pairing value" }));
  await screen.findByText("A pairing value was already requested. Try again shortly.");
});

test("releases in-memory tab state best effort on page disposal", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(
      jsonResponse({
        processId: "process",
        workspacePath: "workspace",
        tabId: "tab",
        csrfToken: "csrf",
      }),
    )
    .mockResolvedValueOnce(jsonResponse({ released: true }));
  vi.stubGlobal("fetch", fetch);
  render(
    <BrowserSessionProvider>
      <Probe />
    </BrowserSessionProvider>,
  );
  await waitFor(() => expect(screen.getByTestId("session-status")).toHaveTextContent("paired"));
  fireEvent(window, new PageTransitionEvent("pagehide"));
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
  expect(fetch.mock.calls[1]?.[0]).toBe("/api/console/v1/tabs/release");
});

test("keeps tab state registered when the page enters the back-forward cache", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(
      jsonResponse({
        processId: "process",
        workspacePath: "workspace",
        tabId: "tab",
        csrfToken: "csrf",
      }),
    )
    .mockResolvedValueOnce(
      jsonResponse(
        { error: { code: "BROWSER_SECURITY_REJECTED", message: "Browser request rejected." } },
        403,
      ),
    )
    .mockResolvedValueOnce(
      jsonResponse({
        processId: "process",
        workspacePath: "workspace",
        tabId: "restored-tab",
        csrfToken: "restored-csrf",
      }),
    );
  vi.stubGlobal("fetch", fetch);
  render(
    <BrowserSessionProvider>
      <Probe />
    </BrowserSessionProvider>,
  );
  await waitFor(() => expect(screen.getByTestId("session-status")).toHaveTextContent("paired"));
  fireEvent(window, new PageTransitionEvent("pagehide", { persisted: true }));
  await Promise.resolve();
  expect(fetch).toHaveBeenCalledTimes(1);
  fireEvent(window, new PageTransitionEvent("pageshow", { persisted: true }));
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(3));
  expect(fetch.mock.calls[1]?.[0]).toBe("/api/console/v1/tabs/heartbeat");
  expect(fetch.mock.calls[2]?.[0]).toBe("/api/console/v1/bootstrap");
});
