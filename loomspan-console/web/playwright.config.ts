import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  outputDir: "./test-results",
  use: {
    browserName: "chromium",
    trace: "retain-on-failure"
  },
});
