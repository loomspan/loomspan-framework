import path from "node:path";
import fs from "node:fs";
import { fileURLToPath } from "node:url";
import { expect, test } from "./fixtures/consoleProcess";

const traceFixture = (name: string) => path.resolve(path.dirname(fileURLToPath(import.meta.url)),
  `../../../loomspan-console-fixtures/traces/${name}.ndjson`);

test("shows explicit classification, exact messages and recorded cause through the real Console", async ({ page, consoleProcess }) => {
  await page.goto(consoleProcess.pairingUrl);
  await expect(page.getByRole("navigation", { name: "Console" })).toBeVisible();
  await page.goto(`${consoleProcess.origin}/trace-storage`);
  await page.getByLabel("Trace files").setInputFiles(traceFixture("terminal-wrapper-recorded-cause"));
  await expect(page.getByRole("heading", { name: "Trace explorer" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Terminal failure", exact: true })).toBeVisible();
  await expect(page.getByText("Selected as the completion failure.")).toBeVisible();
  await expect(page.locator("pre").filter({ hasText: "terminal application wrapper" }).first()).toBeVisible();
  await page.getByRole("button", { name: "failure-1", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Recoverable failure", exact: true })).toBeVisible();
  await expect(page.getByText("Selected as the completion failure.")).toHaveCount(0);
  await expect(page.locator("pre").filter({ hasText: "correct this" }).first()).toContainText("雪");
  await expect(page.getByText("Recovered failure", { exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Load diagnostic 1" }).click();
  await expect(page.getByRole("button", { name: "Copy diagnostic" })).toBeVisible();
  await expect(page.locator(".failure-diagnostic-output pre")).toContainText("diagnostic cause");
});

const signalPrefix = '{\r\n  "stepAction":"CANNOT_COMPLETE",\r\n  "reason":';

test("inspects a completion-linked inability and downloads its retained signal", async ({ page, consoleProcess }) => {
  // Capture the application call before platform clipboard newline handling.
  await page.addInitScript(() => {
    let clipboard = "";
    Object.defineProperty(navigator, "clipboard", { value: {
      writeText: async (value: string) => { clipboard = value; },
      readText: async () => clipboard,
    } });
  });
  await page.goto(consoleProcess.pairingUrl);
  await expect(page.getByRole("navigation", { name: "Console" })).toBeVisible();
  await page.goto(consoleProcess.origin + "/trace-storage");
  await page.getByLabel("Trace files").setInputFiles(traceFixture("assigned-planner-inability"));

  const panel = page.getByRole("region", { name: "Trace failure details" });
  await expect(panel.getByRole("heading", { name: "Planner inability evidence" })).toBeVisible();
  await expect(panel.getByText("Selected as the completion failure.")).toBeVisible();
  await expect(panel.getByText("Assigned task reported planner inability")).toBeVisible();
  await expect(panel.getByText("assigned-step", { exact: true })).toBeVisible();
  await expect(panel.getByText("verify-source", { exact: true })).toBeVisible();
  expect(await panel.locator(".failure-diagnostics pre").textContent()).toBe("  Need an approved source.\r\n雪\t");
  await expect(panel.getByText(/Accepted planner inability signal/)).toBeVisible();
  await panel.getByRole("button", { name: "Load diagnostic 2" }).click();
  await expect(panel.getByText("This diagnostic was truncated during capture at 1048576 bytes.")).toBeVisible();

  const partialReason = '"  Need an approved source.\\r\\n';
  const retained = signalPrefix + " ".repeat(1048575 - signalPrefix.length - partialReason.length) + partialReason;
  expect(await panel.locator(".failure-diagnostic-output pre").textContent()).toBe(retained);
  await panel.getByRole("button", { name: "Copy diagnostic" }).click();
  await expect(panel.getByText("Diagnostic copied to the clipboard.")).toBeVisible();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(retained);
  const downloadEvent = page.waitForEvent("download");
  await panel.getByRole("button", { name: "Download diagnostic" }).click();
  const download = await downloadEvent;
  expect(download.suggestedFilename()).toBe("failure-inability-1.txt");
  expect(fs.readFileSync(await download.path(), "utf8")).toBe(retained);
});

test("selects nested synthesis inability after root success and inspects the parent's feedback", async ({ page, consoleProcess }) => {
  await page.goto(consoleProcess.pairingUrl);
  await expect(page.getByRole("navigation", { name: "Console" })).toBeVisible();
  await page.goto(consoleProcess.origin + "/trace-storage");
  await page.getByLabel("Trace files").setInputFiles(traceFixture("nested-synthesis-planner-inability"));
  await expect(page.locator(".trace-explorer-summary")).toContainText("SUCCEEDED");
  await page.getByRole("tab", { name: "Records" }).click();
  await page.getByRole("row", { name: /ERROR_RECORDED/ }).getByRole("button", { name: "View error" }).click();

  const panel = page.getByRole("region", { name: "Trace failure details" });
  await expect(panel.getByRole("heading", { name: "Planner inability evidence" })).toBeVisible();
  await expect(panel.getByText("Selected as the completion failure.")).toHaveCount(0);
  await expect(panel.getByText("Final synthesis reported planner inability")).toBeVisible();
  await expect(panel.getByText("synthesis-step", { exact: true })).toBeVisible();
  await expect(panel.getByText("gather-records", { exact: true })).toBeVisible();
  await expect(panel.getByText("default-summarize#step-2", { exact: true }).first()).toBeVisible();
  expect(await panel.locator(".failure-diagnostics pre").textContent()).toBe("  Records disagree.\r\n雪\t");
  await panel.getByRole("button", { name: "Load diagnostic 2" }).click();
  const signal = panel.locator(".failure-diagnostic-output pre");
  await expect(signal).toBeVisible();
  expect(await signal.textContent()).toBe(signalPrefix + '"  Records disagree.\\r\\n雪\\t"\r\n}');

  await panel.getByRole("button", { name: "Show in timeline" }).click();
  await expect(page.getByRole("tab", { name: "Timeline" })).toHaveAttribute("aria-selected", "true");
  await panel.getByRole("button", { name: "Show records" }).click();
  const finalRequest = page.getByRole("row", { name: /MODEL_REQUEST_SENT/ }).last();
  await finalRequest.getByRole("button", { name: "Request", exact: true }).click();
  const response = page.getByRole("region", { name: "Tool response 1" });
  await expect(response).toBeVisible();
  expect(await response.locator("pre").allTextContents()).toEqual([
    "call-coordinate", "default-coordinate",
    '{"status":"failed","kind":"planner_inability","correctionPermitted":true,"message":"  Records disagree.\\r\\n雪\\t"}',
  ]);
});
