import { expect, test } from "vitest";
import authenticationRequired from "../../../browser-fixtures/target/bootstrap-authentication-required.json";
import connected from "../../../browser-fixtures/target/bootstrap-connected.json";
import noTarget from "../../../browser-fixtures/target/bootstrap-no-target.json";
import accessBlocked from "../../../browser-fixtures/target/error-access-blocked.json";
import authenticationError from "../../../browser-fixtures/target/error-authentication-required.json";
import incompatible from "../../../browser-fixtures/target/error-incompatible.json";
import targetChanged from "../../../browser-fixtures/target/error-target-changed.json";
import unavailable from "../../../browser-fixtures/target/error-unavailable.json";

test("consumes the complete browser target fixture contract", () => {
  expect(noTarget.target.status.targetSelection).toBe("NONE");
  expect(authenticationRequired.target.status.targetAuthentication).toBe("REQUIRED");
  expect(connected.target.status.instanceId).toBe(
    "22222222-2222-4222-8222-222222222222",
  );
  expect(authenticationError.error.code).toBe("TARGET_AUTHENTICATION_REQUIRED");
  expect(accessBlocked.error.code).toBe("TARGET_ACCESS_BLOCKED");
  expect(unavailable.error.details.transportCategory).toBe("timeout");
  expect(incompatible.error.details.expectedCompatibilityVersion).toBe(
    "0.1.0-SNAPSHOT",
  );
  expect(targetChanged.error.details.currentTargetScopeId).toBe(
    "33333333-3333-4333-8333-333333333333",
  );
});

