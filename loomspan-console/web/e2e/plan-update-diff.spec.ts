import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { expect, test } from "./fixtures/consoleProcess";
import { expectNoSeriousAccessibilityViolations } from "./accessibility";

test("plan update diff remains usable with keyboard, narrow viewport and forced colors", async ({ page, consoleProcess }) => {
  const fixture = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../loomspan-console-fixtures/traces/current-plan-semantic-evidence.ndjson");
  const note = '<img src=x onerror="alert(1)">\n' + "long-note ".repeat(200);
  const trace = fs.readFileSync(fixture, "utf8").trim().split("\n").map((line) => {
    const record = JSON.parse(line);
    if (record.recordType === "PLAN_CREATED" || record.recordType === "PLAN_UPDATED") {
      record.data.tasks = [{ taskId: "task-1", title: "Task", status: "PENDING", dependsOn: [], expectedOutputs: [], note: record.recordType === "PLAN_UPDATED" ? note + record.sequence : null }];
    }
    return JSON.stringify(record);
  }).join("\n") + "\n";
  await page.goto(consoleProcess.pairingUrl);
  await page.goto(`${consoleProcess.origin}/trace-storage`);
  await page.getByLabel("Trace files").setInputFiles({ name: "plan-updates.ndjson", mimeType: "application/x-ndjson", buffer: Buffer.from(trace) });
  await expect(page.getByRole("heading", { name: "Trace explorer" })).toBeVisible();
  await page.getByRole("tab", { name: "Records" }).click();
  const toggle = page.getByRole("button", { name: "View diff", exact: true }).first();
  await expect(toggle).toBeVisible();
  // Generic readers precede record-specific actions in both visual and tab order.
  const row = toggle.locator("xpath=ancestor::tr");
  await row.focus();
  await page.keyboard.press("Tab");
  await expect(row.getByRole("button", { name: "Read raw record", exact: true })).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(row.getByRole("button", { name: "Read content", exact: true })).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(toggle).toBeFocused();
  await page.keyboard.press("Enter");
  const hide = page.getByRole("button", { name: "Hide diff", exact: true });
  await expect(hide).toBeFocused();
  await expect(hide).toHaveAttribute("aria-expanded", "true");
  const detail = page.getByRole("region", { name: /Plan diff for record/ });
  await expect(detail).toContainText(note);
  await expect(detail.getByRole("img", { name: "changed to" }).first()).toBeVisible();
  await expect(detail.locator("dd").first()).toContainText("null →");
  await expect(detail.getByText("Before", { exact: true })).toHaveCount(0);
  await expect(detail.getByText("After", { exact: true })).toHaveCount(0);
  await expect(detail.locator("pre")).toHaveCount(0);
  await expect(detail.locator("img")).toHaveCount(0);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.emulateMedia({ forcedColors: "active" });
  await expect(detail).toBeVisible();
  const values = detail.locator(".trace-plan-field-value");
  for (const value of await values.all()) {
    expect(await value.evaluate((element) => getComputedStyle(element).whiteSpace)).toBe("pre-wrap");
  }
  for (const row of await detail.locator("dd").all()) {
    expect(await row.evaluate((element) => element.scrollWidth <= element.clientWidth + 1)).toBe(true);
  }
  await expectNoSeriousAccessibilityViolations(page);
  await hide.focus();
  await page.keyboard.press("Space");
  await expect(detail).toHaveCount(0);
  await expect(page.getByRole("button", { name: "View diff", exact: true }).first()).toBeFocused();
});
