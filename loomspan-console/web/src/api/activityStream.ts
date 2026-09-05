import type { Activity, ConnectionFact, Continuity } from "./contracts";

export type SSEFrame = {
  event: string;
  data: string;
  id?: string;
};

export type ActivityStreamCallbacks = {
  onActivity: (activity: Activity) => void;
  onConnection?: (fact: ConnectionFact) => void;
  onContinuity?: (continuity: Continuity) => void;
  onReplayGap?: (reason: string) => void;
  onBaselineRefreshed?: (observedAt?: string) => void;
  onError?: (error: Error) => void;
  onClose?: () => void;
};

export type ActivityStreamOptions = {
  url: string;
  body: object;
  tabId: string;
  csrfToken?: string;
};

const maxLineLength = 16 * 1024;

/**
 * Incremental SSE decoder that handles split UTF-8 chunks, validates event
 * names and JSON, and never interprets activity text as markup.
 */
export class SSEDecoder {
  private decoder = new TextDecoder();
  private buffer = "";
  private currentEvent = "";
  private currentData = "";
  private currentId: string | undefined;
  private hasData = false;

  /**
   * Feed a chunk of bytes. Returns complete parsed frames.
   * Handles split UTF-8 by using TextDecoder with {stream: true}.
   */
  feed(chunk: Uint8Array): SSEFrame[] {
    this.buffer += this.decoder.decode(chunk, { stream: true });
    return this.drainCompleted();
  }

  /**
   * Flush any remaining buffered data at stream end.
   */
  flush(): SSEFrame[] {
    this.buffer += this.decoder.decode(new Uint8Array(0), { stream: false });
    const frames = this.drainCompleted();
    if (this.hasData) {
      frames.push({
        event: this.currentEvent || "message",
        data: this.currentData,
        id: this.currentId,
      });
      this.currentEvent = "";
      this.currentData = "";
      this.currentId = undefined;
      this.hasData = false;
    }
    return frames;
  }

  private drainCompleted(): SSEFrame[] {
    const frames: SSEFrame[] = [];
    while (true) {
      const newlineIdx = this.buffer.indexOf("\n");
      if (newlineIdx === -1) {
        if (this.buffer.length > maxLineLength) {
          throw new Error("SSE line exceeds maximum length");
        }
        break;
      }
      let line = this.buffer.slice(0, newlineIdx);
      this.buffer = this.buffer.slice(newlineIdx + 1);
      // Remove trailing \r for CRLF compatibility
      if (line.endsWith("\r")) {
        line = line.slice(0, -1);
      }
      // Empty line = frame boundary
      if (line === "") {
        if (this.hasData) {
          frames.push({
            event: this.currentEvent || "message",
            data: this.currentData,
            id: this.currentId,
          });
          this.currentEvent = "";
          this.currentData = "";
          this.currentId = undefined;
          this.hasData = false;
        }
        continue;
      }
      // Comment line
      if (line.startsWith(":")) {
        continue;
      }
      // Field line
      const colonIdx = line.indexOf(":");
      let field: string;
      let value: string;
      if (colonIdx === -1) {
        field = line;
        value = "";
      } else {
        field = line.slice(0, colonIdx);
        value = line.slice(colonIdx + 1);
        if (value.startsWith(" ")) {
          value = value.slice(1);
        }
      }
      switch (field) {
        case "event":
          this.currentEvent = value;
          break;
        case "data":
          if (this.hasData) {
            this.currentData += "\n" + value;
          } else {
            this.currentData = value;
            this.hasData = true;
          }
          break;
        case "id":
          this.currentId = value;
          break;
        case "retry":
          // Ignored — we don't auto-reconnect at this layer
          break;
        default:
          // Unknown field — ignore per SSE spec
          break;
      }
    }
    return frames;
  }
}

/** Valid SSE event names for the activity stream */
const validEvents = new Set([
  "loomspan.activity",
  "console.connection",
  "console.continuity",
  "console.replay_gap",
  "console.baseline_refreshed",
  "console.target_changed",
]);

/** Validate and parse a frame's data as JSON */
function parseFrameData(frame: SSEFrame): unknown {
  if (!validEvents.has(frame.event)) {
    throw new Error(`Unexpected activity stream event: ${frame.event}`);
  }
  try {
    return JSON.parse(frame.data);
  } catch (error) {
    throw new Error(`Invalid JSON in activity stream event: ${frame.event}`, {
      cause: error,
    });
  }
}

/**
 * Open an activity stream using fetch POST with SSE framing.
 * Uses the SSEDecoder for incremental parsing with split UTF-8 support.
 */
export function openActivityStream(
  options: ActivityStreamOptions,
  callbacks: ActivityStreamCallbacks,
): () => void {
  const controller = new AbortController();
  const decoder = new SSEDecoder();
  let cancelled = false;

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "text/event-stream",
    "X-loomspan-Console-Tab": options.tabId,
  };
  if (options.csrfToken) {
    headers["X-loomspan-Console-CSRF"] = options.csrfToken;
  }

  fetch(options.url, {
    method: "POST",
    credentials: "same-origin",
    headers,
    body: JSON.stringify(options.body),
    cache: "no-store",
    redirect: "error",
    signal: controller.signal,
  })
    .then((response) => {
      if (!response.ok) {
        throw new Error(`Activity stream failed: ${response.status}`);
      }
      if (!response.body) {
        throw new Error("Activity stream has no body");
      }
      const reader = response.body.getReader();
      const pump = (): Promise<void> =>
        reader.read().then(({ done, value }): void | Promise<void> => {
          if (done) {
            try {
              const finalFrames = decoder.flush();
              for (const frame of finalFrames) {
                dispatchFrame(frame, callbacks);
              }
            } catch (error) {
              if (!cancelled) callbacks.onError?.(error as Error);
              return;
            }
            if (!cancelled) callbacks.onClose?.();
            return;
          }
          try {
            const frames = decoder.feed(value);
            for (const frame of frames) {
              dispatchFrame(frame, callbacks);
            }
          } catch (error) {
            if (!cancelled) {
              callbacks.onError?.(error as Error);
            }
            return;
          }
          return pump();
        });
      return pump();
    })
    .catch((error) => {
      if (!cancelled && error.name !== "AbortError") {
        callbacks.onError?.(error);
      }
    });

  function close(): void {
    cancelled = true;
    controller.abort();
  }

  return close;
}

function dispatchFrame(frame: SSEFrame, callbacks: ActivityStreamCallbacks): void {
  const data = parseFrameData(frame);
  switch (frame.event) {
    case "loomspan.activity":
      callbacks.onActivity(data as Activity);
      break;
    case "console.connection":
      callbacks.onConnection?.(data as ConnectionFact);
      break;
    case "console.continuity":
      callbacks.onContinuity?.(data as Continuity);
      break;
    case "console.replay_gap":
      callbacks.onReplayGap?.((data as { reason?: string }).reason ?? "unknown");
      break;
    case "console.baseline_refreshed":
      callbacks.onBaselineRefreshed?.(
        (data as { observedAt?: string }).observedAt,
      );
      break;
    case "console.target_changed":
      callbacks.onConnection?.({ connected: false, reason: "target_changed" });
      break;
  }
}
