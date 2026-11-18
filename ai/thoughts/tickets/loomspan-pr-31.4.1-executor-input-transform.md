# PR 31.4.1 — Transform validated skill input before the Java executor handoff

## Outcome

An application can expose a business input schema to callers and construct a different JSON object for its Java executor. Framework owns the transformation language and evaluates it for any executor-backed YAML skill. A provider pack can therefore accept its native request as input.

## Requirements

- Add the optional root YAML field `input_transform` to executor-backed skills only. Its two required fields are `language: jslt` and a nonblank string `expression`. Check the declaration and compile the expression during startup or namespace preparation. A failed preparation cannot displace the active generation.
- The skill's ordinary input contract remains the caller and planner contract. A parent's `input_bindings` still targets that contract. Evaluate the compiled expression once per invocation at the common executor handoff, after argument assembly and input validation. Bind JSLT's `.` to the child input object. The expression consumes JSON data and produces the complete object exposed through the existing v1 `invocation.input()`. A transform involving unsupported non-JSON input must fail explicitly rather than silently stringifying runtime handles.
- Keep general skill and executor configuration separate from the expression input. Preserve exact JSON keys, literal strings, explicit nulls and empty containers. Use a JSLT object filter that retains constructed nulls and empty containers. Preserve the original caller contract and authored YAML in diagnostics. Apply existing authorization, lifecycle and generation capture to transformed calls.
- A skill with no transform gets the existing executor handoff, including support for runtime handles. Preserve the supported Java surface. JSLT belongs to Framework internals.
- Use the locally installed Maven coordinate `ai.loomspan.thirdparty:jslt:0.1.15-jackson3-cf30e6d` for the interim Jackson 3 build. It comes from Schibsted JSLT commit `cf30e6d9be247d489dac6bf53bf930322c8fd663`. Its local POM declares Jackson Databind 3.2.2 and the expression below was exercised on Loomspan's managed Jackson 3.1.8. Make the pinned dependency reproducible for a fresh development or test checkout. Scope ends before migration to an official upstream release.

## Acceptance criteria

- [ ] The declaration below loads for an executor leaf. The same leaf called directly or as a planned child with equivalent assembled input delivers the same transformed object to Java. A direct test input supplies `context`, `policy`, `candidates` and `parameters`. The parent can bind those business arguments using the existing child schema.

```yaml
input_transform:
  language: jslt
  expression: |
    {
      "model": "jev-1.13.0",
      "state": {
        "contact": .context,
        "slash": .context."a/b",
        "tilde": .context."a~b",
        "literal": "${/input/notResolved}",
        "note": "Repeat=" + string(.context.repeat) + "; count=" + string(.context.count)
      },
      "questions": {
        "department": {
          "type": "choice",
          "instructions": {
            "question": "Which team should review " + .context.message + "?",
            "policy": .policy
          },
          "criteria": .candidates
        },
        "repeat": {
          "type": "noul",
          "instructions": ["Check contact history.", .context.history[0]],
          "criteria": { "true": null }
        },
        "impact": { "type": "score", "criteria": .parameters.levels }
      }
    }
```

- [ ] The executor observes the selected slash and tilde keys, literal placeholder, computed strings, first history item, structured criteria and explicit null as written. Constructed empty objects and arrays survive, including a valid empty root object.
- [ ] Unsupported declaration forms, unknown transform fields or languages, and invalid expressions fail at load/preparation. Invalid caller input, unsupported runtime values, evaluation failure, or a non-object result fails before the handler runs. Access denial still prevents handler execution.
- [ ] Reloaded skills use their captured generation's expression. Omitting the transform preserves ordinary direct and child calls, their configuration and handoff semantics. The supported-surface architecture check still passes.
- [ ] A clean consumer can resolve the pinned build using documented local build/install instructions; executor authors can implement against the existing v1 invocation alone. Authoring guidance distinguishes caller schema, parent bindings and the executor transform.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The change adds an authored YAML contract across validation, namespace publication, planning and executor invocation, with a locally built runtime dependency.
- **Reassessment triggers:** Reassess if the supported executor API or execution lifecycle would need to change.
- **Implementation model:** GPT-6.2 Sol.

## Pipeline notes

Framework is in development. Compatibility shims are unwanted. The TypeSafe pack migration belongs to PR 31.4.2.

## Execution notes

- The developer confirmed the Full 5-Step Pipeline at the auto profile gate on 2026-11-18.
- The separately authored, untracked PR 31.4.2 ticket is outside this change and is preserved.
