import { type FormEvent, useState } from "react";
import { Button, Input, Label, TextField } from "react-aria-components";
import { BrowserAPIError } from "../api/client";
import { useBrowserSession } from "./BrowserSessionProvider";

export function PairingPage() {
  const session = useBrowserSession();
  const [candidate, setCandidate] = useState("");
  const [notice, setNotice] = useState<string>();

  async function submit(event: FormEvent) {
    event.preventDefault();
    const secret = candidate.trim();
    setCandidate("");
    if (!secret) return;
    await session.pair(secret);
  }

  return (
    <section aria-labelledby="pairing-title" className="foundation-card">
      <p className="eyebrow">Local browser security</p>
      <h2 id="pairing-title">
        {session.status === "loading" ? "Pairing with Console" : "Pair this browser"}
      </h2>
      {session.status === "loading" ? (
        <p role="status">Checking this browser session…</p>
      ) : (
        <>
          <p>
            {session.status === "unpaired" && session.message
              ? session.message
              : "Open the printed pairing link or paste its one-time value."}
          </p>
          <form onSubmit={(event) => void submit(event)}>
            <TextField value={candidate} onChange={setCandidate}>
              <Label>One-time pairing value</Label>
              <Input autoComplete="off" />
            </TextField>
            <Button type="submit">Pair browser</Button>
          </form>
          <Button
            onPress={() => {
              setNotice(undefined);
              void session
                .requestManualChallenge()
                .then(() => {
                  setNotice("A new pairing value was printed in the Console terminal.");
                })
                .catch((error: unknown) => {
                  setNotice(
                    error instanceof BrowserAPIError && error.code === "RATE_LIMITED"
                      ? "A pairing value was already requested. Try again shortly."
                      : "A pairing value could not be printed. Check the Console terminal and try again.",
                  );
                });
            }}
          >
            Print a new pairing value
          </Button>
          {notice ? <p role="status">{notice}</p> : null}
        </>
      )}
    </section>
  );
}
