# Result policy

Use a dated directory for one current-checkout paired matrix. Repository replay
records must identify themselves as deterministic replays; fresh client/model
records must retain their actual client/model/build metadata. Retain completed
unfavorable model runs; keep infrastructure failures separate and rerun them.
Commit only records that pass fail-closed sanitization: no session directory,
MCP key, authorization header, absolute machine path, full tool payload, or
internal owner/handle/scope selector. A dated summary is derived evidence and
must reproduce from the committed records with `agent-eval verify`.
