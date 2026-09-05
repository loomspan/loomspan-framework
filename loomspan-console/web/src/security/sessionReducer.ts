import type { BootstrapResponse } from "../api/contracts";

export type BrowserSessionState =
  | { status: "loading" }
  | { status: "unpaired"; message?: string }
  | { status: "paired"; bootstrap: BootstrapResponse };

export type BrowserSessionAction =
  | { type: "paired"; bootstrap: BootstrapResponse }
  | { type: "unpaired"; message?: string }
  | { type: "loading" };

export function sessionReducer(
  _state: BrowserSessionState,
  action: BrowserSessionAction,
): BrowserSessionState {
  switch (action.type) {
    case "paired":
      return { status: "paired", bootstrap: action.bootstrap };
    case "unpaired":
      return { status: "unpaired", message: action.message };
    case "loading":
      return { status: "loading" };
  }
}
