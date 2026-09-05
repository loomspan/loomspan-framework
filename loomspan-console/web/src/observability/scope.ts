import { BrowserAPIError } from "../api/client";

export const targetScopeParameter = "targetScopeId";
const alreadyRefreshed = new WeakSet<BrowserAPIError>();

export function scopeBoundPath(path: string, targetScopeID?: string | null) {
  if (!targetScopeID) return path;
  const parameters = new URLSearchParams({ [targetScopeParameter]: targetScopeID });
  return `${path}?${parameters.toString()}`;
}

export async function requireCurrentTargetScope(
  responseScopeID: string,
  currentScopeID: string | undefined,
  refreshTarget: () => Promise<void>,
) {
  if (responseScopeID && responseScopeID === currentScopeID) return;
  await refreshTarget().catch(() => undefined);
  const error = new BrowserAPIError(
    "TARGET_CHANGED",
    "The selected target changed. Start this operation again.",
    409,
  );
  alreadyRefreshed.add(error);
  throw error;
}

export async function recoverObservabilityError(
  error: unknown,
  refreshTarget: () => Promise<void>,
) {
  const normalized = error instanceof BrowserAPIError
    ? error
    : new BrowserAPIError(
      "CONSOLE_ERROR",
      "The Console operation could not be completed.",
      500,
    );
  if (normalized.code === "TARGET_CHANGED" && !alreadyRefreshed.has(normalized)) {
    await refreshTarget().catch(() => undefined);
  }
  return normalized;
}
