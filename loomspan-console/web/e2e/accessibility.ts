import AxeBuilder from "@axe-core/playwright";
import { expect, type Page } from "@playwright/test";

export async function expectNoSeriousAccessibilityViolations(page: Page): Promise<void> {
  const result = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
    .analyze();
  const blocking = result.violations.filter(({ impact }) => impact === "serious" || impact === "critical");
  expect(blocking, blocking.map(({ id, help, nodes }) => `${id}: ${help} (${nodes.length} nodes)`).join("\n")).toEqual([]);
}
