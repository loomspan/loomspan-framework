import path from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

const root = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    setupFiles: [path.join(root, "src/test/setup.ts")],
    include: ["src/**/*.test.{ts,tsx}", "vite.config.test.ts"],
    coverage: {
      provider: "v8",
      reporter: ["text", "json-summary"],
      include: ["src/**/*.{ts,tsx}"],
      exclude: ["src/main.tsx", "src/vite-env.d.ts"],
      thresholds: {
        lines: 80,
        functions: 80,
        statements: 80,
        branches: 70
      }
    }
  }
});
