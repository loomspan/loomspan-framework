import http from "node:http";
import type { Page } from "@playwright/test";
import { test, expect } from "./fixtures/consoleProcess";

type NamespacePublication = {
  namespace: string;
  generationId: string;
  publicationVersion: string | null;
};

async function engine(instanceId: string, generationId: string) {
  const state = {
    available: true,
    instanceId,
    namespaces: [{ namespace: "empty", generationId, publicationVersion: "P7" }] as NamespacePublication[],
  };
  const server = http.createServer((request, response) => {
    if (!state.available) {
      response.writeHead(503).end();
      return;
    }
    if (request.headers["x-loomspan-api-key"] !== "E2E_APPLICATION_KEY_12345678901234567890") {
      response.writeHead(401).end();
      return;
    }
    const pathname = new URL(request.url ?? "/", "http://localhost").pathname;
    const headers = { "Content-Type": "application/json", "X-loomspan-Instance-Id": state.instanceId };
    if (pathname === "/_loomspan/observability/v1/instance") {
      response.writeHead(200, headers).end(JSON.stringify({
        instanceId: state.instanceId,
        consoleCompatibilityVersion: "1.0.0-beta.10-SNAPSHOT",
        observedAt: "2026-07-27T00:00:00Z",
        liveMonitoringAvailable: false,
        registeredSkillCount: 0,
        activeExecutionCount: 0,
        catalogedTraceCount: 0,
        tracePersistencePolicy: "PERSISTENT",
        completionGraceTtl: "PT2M",
        traceCatalogMetadataTtl: "PT168H",
        namespaces: state.namespaces,
      }));
      return;
    }
    response.writeHead(404, headers).end();
  });
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  if (!address || typeof address === "string") throw new Error("Engine did not bind");
  return {
    state,
    origin: `http://127.0.0.1:${address.port}`,
    close: () => new Promise<void>((resolve, reject) => {
      server.close((error) => error ? reject(error) : resolve());
      server.closeAllConnections();
    }),
  };
}

async function selectTarget(page: Page, consoleOrigin: string, engineOrigin: string, replacing: boolean) {
  await page.goto(`${consoleOrigin}/target`);
  if (replacing) await page.getByRole("button", { name: "Change target", exact: true }).click();
  await page.getByLabel("Target address").fill(engineOrigin);
  await page.getByLabel("Application key").fill("E2E_APPLICATION_KEY_12345678901234567890");
  await page.getByRole("button", { name: replacing ? "Replace target" : "Connect", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Instance Overview" })).toBeVisible();
}

test("namespace publications are current before execution across target selection and reopen", async ({ page, consoleProcess }) => {
  const east = await engine("11111111-1111-4111-8111-111111111111", "east-7");
  const west = await engine("22222222-2222-4222-8222-222222222222", "west-7");
  try {
    await page.goto(consoleProcess.pairingUrl);
    await expect(page.getByRole("navigation", { name: "Console" })).toBeVisible();
    await selectTarget(page, consoleProcess.origin, east.origin, false);
    await expect(page.getByRole("row", { name: "empty east-7 P7" })).toBeVisible();

    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(page.getByRole("row", { name: "empty east-7 P7" })).toBeVisible();
    east.state.namespaces = [{ namespace: "empty", generationId: "east-8", publicationVersion: "P8" }];
    await page.reload();
    await expect(page.getByRole("row", { name: "empty east-8 P8" })).toBeVisible();

    await selectTarget(page, consoleProcess.origin, west.origin, true);
    await expect(page.getByRole("row", { name: "empty west-7 P7" })).toBeVisible();
    await selectTarget(page, consoleProcess.origin, east.origin, true);
    await expect(page.getByRole("row", { name: "empty east-8 P8" })).toBeVisible();

    east.state.available = false;
    await page.goto(`${consoleProcess.origin}/target`);
    await page.getByRole("button", { name: "Recheck target", exact: true }).click();
    const targetContext = page.getByRole("complementary", { name: "Current target and live context" });
    await expect(targetContext).toContainText("ConnectionUNAVAILABLE");
    east.state.available = true;
    east.state.namespaces = [{ namespace: "empty", generationId: "east-reconnected", publicationVersion: "P8-reconnected" }];
    await page.getByRole("button", { name: "Recheck target", exact: true }).click();
    await expect(targetContext).toContainText("ConnectionREACHABLE");
    await page.getByRole("link", { name: "Overview", exact: true }).click();
    await expect(page.getByRole("row", { name: "empty east-reconnected P8-reconnected" })).toBeVisible();

    east.state.namespaces = [{ namespace: "empty", generationId: "east-9", publicationVersion: null }];
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(page.getByRole("row", { name: "empty east-9 Not supplied" })).toBeVisible();

    east.state.instanceId = "33333333-3333-4333-8333-333333333333";
    east.state.namespaces = [{ namespace: "empty", generationId: "east-restarted-1", publicationVersion: "P7" }];
    await page.reload();
    await expect(page.getByRole("row", { name: "empty east-restarted-1 P7" })).toBeVisible();
    await expect(page.getByRole("complementary", { name: "Current target and live context" })).toContainText(east.state.instanceId);
    east.state.namespaces = [];
    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(page.getByText("No namespaces are installed.")).toBeVisible();
  } finally {
    await Promise.all([east.close(), west.close()]);
  }
});
