# PR 32 — Expose the skill and contract stage that caused an invocation to fail

## Outcome

Through the supported Framework Java API, a caller handling a failed invocation can determine whether a skill's input schema, input transform, output transform, or output schema caused the failure. Sidecar needs this in its ordinary execution response even with diagnostics set to `NEVER`. For a request to `parent`, the caller should be able to retain that requested name and separately report that `default-tsTriageStructured` published null at `/repeatContactProbability`, violating `type`.

## Requirements

- Provide an optional structured diagnosis through the normal failure path for both `SkillTemplate` and the invocation handoff used by Sidecar, including admission-time input rejection. Leave the exact Java shape to implementation. Keep the existing public `SkillInputValidationException` and its issues usable. Callers must be able to use supported types alone, without parsing exception messages, installing an observer, retaining events, or looking up a trace.
- Include the canonical name of the skill whose contract failed, the stage, and a human-readable explanation. For schema violations include the validator's structured issues with JSON Pointer, constraint or failure code, and explanation. Use standard JSON Pointer semantics, including the empty pointer for the root and escaping for literal slashes and tildes in keys. For transform failures retain the Framework or JSLT explanation, including author-provided errors. A transform error need not have a field location or schema constraint.

| Stage | Attribution |
|---|---|
| `INPUT_SCHEMA` | The receiving skill whose input contract rejected the arguments, including direct input and assembled child input. Includes generated Java skill input contracts. |
| `INPUT_TRANSFORM` | The executor-backed YAML skill whose declared transform could not consume the input, evaluate, or produce the required executor input object. |
| `OUTPUT_TRANSFORM` | The executor-backed YAML skill whose declared transform could not parse the successful executor result, evaluate, or publish its JSON value. |
| `OUTPUT_SCHEMA` | The skill whose output contract rejected the candidate it would publish. Covers executor leaves, ordinary model output and explicit planning, including assembled outputs when schema validation is the terminal cause. |

- For root input rejected before a session exists, use the immediate contract failure. After execution begins, use the same causal selection as canonical root completion, including wrapper links and primary cancellation precedence. Preserve diagnosis at the validation or transform boundary and carry it along the selected failure path. Event order, suppressed failures and unrelated branch outcomes are insufficient to establish causation.
- This is a reporting change. Preserve current validation, retry, recovery, cancellation and executor-call behavior. Runtime declaration loading, JSLT compilation, unavailable binding sources, authorization, and ordinary handler failures are outside the four stages. Scope is the Framework contract; the Sidecar HTTP representation is a consumer change.

## Acceptance criteria

- [ ] An API consumer can distinguish pre-session root input rejection, a generated Java input contract violation, and a bound child receiving an invalid value. Each reports `INPUT_SCHEMA` with the receiving skill's name and original validation issues. Existing callers can still catch the public input exception.
- [ ] If a planner repeatedly supplies arguments that violate the assigned child's argument schema and that rejection exhausts correction, report the receiving child's `INPUT_SCHEMA` issues even though dispatch never occurred. A corrected argument or a terminal planner-action error unrelated to the child's schema yields no diagnosis for that rejected attempt.
- [ ] When planning parent `parent` invokes `default-tsTriageStructured` and its successful transform publishes `{"repeatContactProbability":null}`, the public diagnosis identifies that child, `OUTPUT_SCHEMA`, pointer `/repeatContactProbability`, code `type`, and an explanation. The dependent child never runs. The diagnosis is available through admitted invocation without retained diagnostics and agrees with the selected cause where a trace is available.
- [ ] Terminal output validation yields the same diagnosis shape for executor, ordinary model, and planning/assembled results. Corrected model attempts yield no terminal diagnosis. Malformed result JSON at a schema boundary reports the validator's failure code. Root and escaped property locations are valid JSON Pointers.
- [ ] A non-object input transform result reports `INPUT_TRANSFORM` before executor entry. A declared output transform receiving malformed result text, or calling `error("Incomplete triage judgment")`, reports `OUTPUT_TRANSFORM` after one executor call. The latter retains the author's explanation. A successful transform that produces a schema-invalid value reports `OUTPUT_SCHEMA`.
- [ ] In a parallel plan, force two schema failures to be recorded in reverse accepted-task order. The reported skill and issues match canonical terminal selection, including through a causal wrapper. In a second run, record a schema violation in one branch and select an unrelated application failure or cancellation as terminal. The public contract diagnosis is absent even if the schema violation is the only recorded schema event.
- [ ] The README documents the supported diagnosis and its absence semantics. Supported-surface tests cover the public signature and ordinary invocation entry points, and `LoomspanPublicSurfaceArchitectureTest` passes.

## Existing code to reuse

- **Terminal selection.** [StepLoopMissionExecutionEngine.propagateUnitFailure](../../../src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java#L740) selects the outcome of a task group. [ExecutionCoordinator](../../../src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java#L223) gives primary cancellation precedence when recording completion, even if a different exception is being propagated. [LoomspanSession.recordFailure](../../../src/main/java/ai/loomspan/internal/core/LoomspanSession.java#L153) already associates throwable identities with recorded failures and finds wrapper causes. Carry the diagnosis from the originating boundary through these decisions. A second selector, trace replay, or a global table of failures would add unnecessary machinery. The [parallel selection test](../../../src/test/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngineTest.java#L3069) already forces event order to diverge from accepted order.
- **Input validation.** [Root preparation](../../../src/main/java/ai/loomspan/internal/skillapi/DefaultSkillTemplate.java#L156) and [assembled child input](../../../src/main/java/ai/loomspan/internal/runtime/tool/DefaultCapabilityInvoker.java#L123) already have the receiving identity and validation result. [StepActionValidator](../../../src/main/java/ai/loomspan/internal/runtime/step/StepActionValidator.java#L170) also has structured issues before it formats planner feedback. Preserve those issues when the rejection becomes terminal; evaluating the schema again is unnecessary.
- **Executor transforms.** [YamlSkillDefinition](../../../src/main/java/ai/loomspan/internal/skill/YamlSkillDefinition.java#L27) knows the skill identity and distinguishes input JSON admission, input evaluation, and output evaluation. Attribute failures there. A Java handler exception between the two transforms remains an ordinary execution failure.
- **Output validation.** [Executor publication](../../../src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java#L357) separates transform evaluation from schema validation. The [ordinary model advisor](../../../src/main/java/ai/loomspan/internal/outputschema/OutputSchemaCallAdvisor.java#L165) already carries validation issues on exhaustion. [Planned final-response validation](../../../src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java#L1015) shares an exhaustion path with linter and evidence failures. Preserve the verdict from the validator that actually failed. [OutputSchemaValidator](../../../src/main/java/ai/loomspan/internal/outputschema/OutputSchemaValidator.java#L43) uses `$` for the root in existing diagnostics; expose the empty JSON Pointer in the new public detail without changing existing diagnostic paths.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The work adds a supported Java failure contract shared across admission, nested execution, model correction, and concurrent failure selection.
- **Reassessment triggers:** Reassess scope if providing the diagnosis requires changing which failure wins or introducing a separate trace-retention requirement.

## Execution notes

- The developer approved the Full 5-Step Pipeline at the auto gate.
- At triage, `main` matched `origin/main`. This ticket was the only untracked file, with no staged or unstaged changes.
