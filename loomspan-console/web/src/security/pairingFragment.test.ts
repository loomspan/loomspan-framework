import { expect, test, vi } from "vitest";
import { consumePairingFragment } from "./pairingFragment";

test("removes pairing fragment from the current history entry immediately", () => {
  const replaceState = vi.fn();
  const secret = "A".repeat(43);
  const result = consumePairingFragment(
    { hash: `#/pair/${secret}`, pathname: "/", search: "?view=pair" },
    { replaceState },
  );
  expect(replaceState).toHaveBeenCalledWith(null, "", "/?view=pair");
  expect(result).toBe(secret);
});

test("removes malformed pairing fragments without returning their value", () => {
  const replaceState = vi.fn();
  const result = consumePairingFragment(
    { hash: "#/pair/PAIRING_SECRET_SENTINEL", pathname: "/", search: "" },
    { replaceState },
  );
  expect(replaceState).toHaveBeenCalled();
  expect(result).toBeUndefined();
});
