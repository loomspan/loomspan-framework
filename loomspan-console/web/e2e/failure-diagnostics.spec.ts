import path from "node:path";
import { fileURLToPath } from "node:url";
import { expect, test } from "./fixtures/consoleProcess";

const fixture = path.resolve(path.dirname(fileURLToPath(import.meta.url)),
  "../../../loomspan-console-fixtures/traces/terminal-wrapper-recorded-cause.ndjson");

test("shows explicit classification, exact messages and recorded cause through the real Console", async ({ page, consoleProcess }) => {
  await page.goto(consoleProcess.pairingUrl);
  await expect(page.getByRole("navigation", { name: "Console" })).toBeVisible();
  await page.goto(`${consoleProcess.origin}/trace-storage`);
  await page.getByLabel("Trace files").setInputFiles(fixture);
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
