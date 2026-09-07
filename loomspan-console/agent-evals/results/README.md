# Result policy

Use a dated directory for one current-checkout paired matrix. Repository replay
records must identify themselves as deterministic replays; fresh client/model
records must retain their actual client/model/build metadata. Retain completed
unfavorable model runs; keep infrastructure failures separate and rerun them.
Commit only records that pass fail-closed sanitization: no session directory,
MCP key, authorization header, absolute machine path, full tool payload, or
internal owner/handle/scope selector. A dated summary is derived evidence and
must reproduce from the committed records with `agent-eval verify` for the current
package, or `agent-eval verify-replay` for historical deterministic replays.

The 2026-09-05 matrix retains its original records and summary. Its `skill-package`
directory preserves the six files recovered from commit
`d202b20` whose complete package digest matches the recorded digest
`36e3f9e29dd29aa90e4eb19c29a07c9a93fb12ac3c56c569f8e5af4a1b35dc5d`.
This package is historical evidence, not an installable current-version skill.
The version script excludes dated result directories from replacement.
