# PR 36 — Write execution traces under the entry skill’s namespace

## Outcome

Group persisted execution traces by the namespace of the skill that started the execution. This lets Loomspan Cloud collect traces by namespace and lets Framework and Sidecar administrators grant departments access to their own trace directories through filesystem permissions. [GitHub PR #36](https://github.com/loomspan/loomspan-framework/pull/36)

## Requirements

- Add the host-level configuration setting `loomspan.execution-trace.directory`, defaulting to `${java.io.tmpdir}/loomspan-traces`. A configured value is the trace root itself. Namespace-published execution configuration cannot override it.
- Write each trace to `<root>/<canonical entry namespace>/<existing filename>`. The entry namespace owns the entire trace, including calls to skills in other namespaces.
- Limit the change to selecting the output directory. Reuse existing directory creation, filename generation, trace content, persistence policy, and write-error handling. Administrators manage permissions on the filesystem.

## Acceptance criteria

- [ ] With default settings, a trace for a skill in `finance` is written to `${java.io.tmpdir}/loomspan-traces/finance/<sessionId>.<traceId>.execution-trace.ndjson`. Existing trace metadata reports the actual path and existing retention rules apply.
- [ ] With a host-configured root, executions starting in `finance` and `shared` write to their respective immediate subdirectories there. A call from the first execution into `shared` stays in the single `finance` trace. A published execution configuration cannot redirect either trace.
- [ ] A missing namespace directory is created on write. A pre-created directory retains its administrator-set permissions. If the designated path is unwritable, existing write-error handling applies and no trace is written elsewhere.

## Pipeline notes

Loomspan is in development and has no deployments or backward compatibility requirements. Replacing the flat layout outright is intentional. Destructive changes within this scope are acceptable. Omit migration, legacy path lookup, and compatibility shims.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The change is narrow, but it establishes a persisted filesystem layout that administrators will rely on for namespace access boundaries. Correct placement matters on every trace-writing path.
- **Reassessment triggers:** Reassess if the checkout already implements the requested layout and host setting and only documentation or verification remains.
