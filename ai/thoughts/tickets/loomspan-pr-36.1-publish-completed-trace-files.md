# PR 36.1 — Publish trace files under their final name at completion

## Outcome

A filesystem collector watching a PR 36 entry-namespace directory can treat the appearance of `<sessionId>.<traceId>.execution-trace.ndjson` as the signal that the entire trace is available to read. Today the first record creates that filename while execution is still running.

## Requirements

- Write the active trace in the same namespace directory under a distinct in-process name that cannot match the completed-file pattern. Appending `.in-progress` to the completed name is a suggested convention.
- For a trace retained at completion, finish writing and closing all records, including `TRACE_COMPLETED`, then publish the file with a same-directory rename as the last file-writing step. Readers must see the entire file on first opening the completed name. Publish the finalized artifact descriptor only after the rename succeeds.
- Preserve current persistence and grace-retention decisions. Delete an immediately discarded trace under its in-process name. A grace-retained trace gets the completed name and expires on its existing schedule.
- Keep live trace reads and journal projection working through finalization. Finalized artifact metadata, downloads and cleanup must use the published path.

## Acceptance criteria

- [x] During execution, records can be read from the in-process file and the completed filename is absent. Journal projection succeeds using the active trace.
- [x] When a retained execution finishes, including a retained failed execution, the first read at the completed path contains the full trace and its terminal record. The in-process name is gone. Repeated finalization does not duplicate the terminal record or republish the file.
- [x] A grace-retained artifact is readable at its reported completed path until expiry. Immediate discard leaves neither filename and never exposes a completed file.
- [x] An execution interrupted before publication leaves no completed file for that execution. A failed rename follows the existing trace-finalization error path and does not advertise a finalized artifact.

## Pipeline notes

The expected production scope is Framework trace finalization and retention. Console receives live activity through the event stream and downloads completed artifacts by trace ID. Framework publishes the artifact to its catalog after finalization. Verify that the existing Console download path serves the completed file after the rename. Console production changes are only expected if verification reveals a dependency on the active filename.

Loomspan is in development and has no deployments. Destructive changes within this trace-file lifecycle are authorized. Replace the old behavior directly and omit migration, legacy filename lookup and compatibility shims. Keep all compatibility version values, including `consoleCompatibilityVersion`, at their current values for this change.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The filename becomes an observable completion signal shared by filesystem collectors, trace readers and retention cleanup.
- **Reassessment triggers:** Reassess if the checkout already implements publication on completion and only documentation or verification remains.

## Execution notes

The developer approved proceeding with the recommended Full 5-Step Pipeline. The local PR 36 entry-namespace commit is the baseline for this change.

Implementation completed through Step 4. Active `.in-progress` traces publish only through same-directory atomic rename after their closed terminal append; retention owns the exact completed path and keeps existing policy/deadline/lease behavior. IOException is cached after terminal completion, and a failed rename advertises no artifact. Real session failure/disposition and runtime-catalog HTTP download tests cover publication and failure. Focused Java checks passed 144 tests; complete Java verification in fresh isolated Maven output passed 2487 tests (0 failures/errors, 2 existing skips); Go client/artifact suites and diff hygiene passed. See the implementation plan for exact commands, the pre-fix failure receipt and resolved sandbox/shared-output verification issues. Fresh Step 5 review remains required for final pipeline completion.
