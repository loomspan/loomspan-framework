import { describe, expect, it } from "vitest";
import { formatDuration } from "./duration";

describe("formatDuration", () => {
  it.each([
    [0, "0ms"],
    [999, "999ms"],
    [1000, "1.0s"],
    [59_999, "60.0s"],
    [60_000, "1m 0.0s"],
    [75_432, "1m 15.4s"],
    [3_905_678, "65m 5.7s"],
  ])("formats %i milliseconds as %s", (millis, expected) => {
    expect(formatDuration(millis)).toBe(expected);
  });
});
