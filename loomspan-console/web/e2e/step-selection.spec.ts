import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { expect, test } from "./fixtures/consoleProcess";
import { expectNoSeriousAccessibilityViolations } from "./accessibility";

test("step boundaries share a fill and the selected activity owns the outline", async ({ page, consoleProcess }, testInfo) => {
  const fixture = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../loomspan-console-fixtures/traces/planned-tool-success.ndjson");
  const records = fs.readFileSync(fixture, "utf8").trim().split("\n").flatMap((line) => {
    const record = JSON.parse(line);
    if (record.frameId !== "step") return [record];
    const event = { ...record, metadata: { stepNumber: 1, readyTasks: 1 }, data: { planStatus: "VALID" } };
    return record.recordType === "FRAME_OPENED"
      ? [record, { ...event, recordType: "STEP_STARTED" }]
      : [{ ...event, recordType: "STEP_COMPLETED" }, record];
  });
  const trace = records.map((record, index) => JSON.stringify({ ...record, sequence: index + 1 })).join("\n") + "\n";
  await page.goto(consoleProcess.pairingUrl);
  await page.goto(`${consoleProcess.origin}/trace-storage`);
  await page.getByLabel("Trace files").setInputFiles({ name: "step-selection.ndjson", mimeType: "application/x-ndjson", buffer: Buffer.from(trace) });
  await expect(page.getByRole("heading", { name: "Trace explorer" })).toBeVisible();
  await page.getByRole("tab", { name: "Records", exact: true }).click();
  const table = page.getByRole("region", { name: "Record list", exact: true });
  const started = table.getByRole("row", { name: /Record \d+, STEP_STARTED/ });
  const completed = table.getByRole("row", { name: /Record \d+, STEP_COMPLETED/ });
  const activity = table.getByRole("row", { name: /Record \d+, TOOL_CALL_STARTED/ });
  const result = table.getByRole("row", { name: /Record \d+, TOOL_CALL_COMPLETED/ });
  const background = (row: typeof started) => row.locator("td").first().evaluate((element) => getComputedStyle(element).backgroundColor);
  for (const selected of [started, completed, activity, result]) {
    await selected.locator("td").first().click();
    await expect(selected).toHaveAttribute("aria-current", "true");
    await expect(started).toHaveClass(/trace-record-related/);
    await expect(completed).toHaveClass(/trace-record-related/);
    await expect(activity).toHaveClass(/trace-record-step-context/);
    expect(await background(started)).toBe(await background(completed));
    expect(await background(activity)).toBe(await background(result));
    expect(await background(started)).not.toBe(await background(activity));
    for (const row of [started, completed, activity, result]) {
      expect(await row.evaluate((element) => getComputedStyle(element).outlineStyle)).toBe(row === selected ? "solid" : "none");
    }
  }
  await page.screenshot({ path: testInfo.outputPath("step-selection.png"), fullPage: true });
  await started.focus();
  await page.keyboard.press("Enter");
  await expect(started).toHaveAttribute("aria-current", "true");
  await page.emulateMedia({ forcedColors: "active" });
  expect(await started.evaluate((element) => getComputedStyle(element).outlineStyle)).toBe("solid");
  expect(await completed.evaluate((element) => getComputedStyle(element).outlineStyle)).toBe("none");
  await expectNoSeriousAccessibilityViolations(page);
});
