import { expect, test, vi } from "vitest";
import { BrowserAPIError } from "../api/client";
import {
  recoverObservabilityError,
  requireCurrentTargetScope,
  scopeBoundPath,
} from "./scope";

test("scopeBoundPath carries the producing target scope", () => {
  expect(scopeBoundPath("/skills/CheckDns", "scope/a")).toBe(
    "/skills/CheckDns?targetScopeId=scope%2Fa",
  );
});

test("matching response scope does not refresh target state", async () => {
  const refresh = vi.fn();
  await requireCurrentTargetScope("scope-1", "scope-1", refresh);
  expect(refresh).not.toHaveBeenCalled();
});

test("mismatched response scope refreshes and rejects the response", async () => {
  const refresh = vi.fn().mockResolvedValue(undefined);
  await expect(
    requireCurrentTargetScope("scope-2", "scope-1", refresh),
  ).rejects.toMatchObject({ code: "TARGET_CHANGED" });
  expect(refresh).toHaveBeenCalledTimes(1);
});

test("TARGET_CHANGED errors refresh authoritative target state", async () => {
  const refresh = vi.fn().mockResolvedValue(undefined);
  const error = new BrowserAPIError("TARGET_CHANGED", "changed", 409);
  await expect(recoverObservabilityError(error, refresh)).resolves.toBe(error);
  expect(refresh).toHaveBeenCalledTimes(1);
});

test("unexpected failures are sanitized without a target refresh", async () => {
  const refresh = vi.fn();
  const result = await recoverObservabilityError(
    new Error("unsafe internal detail"),
    refresh,
  );
  expect(result.code).toBe("CONSOLE_ERROR");
  expect(result.message).not.toContain("unsafe");
  expect(refresh).not.toHaveBeenCalled();
});
