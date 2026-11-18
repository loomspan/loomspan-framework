# PR 31.4.3 — Let executor-backed skills publish a purpose-specific JSON result

## Outcome

A skill author can use an optional, Framework-owned `output_transform` to turn a reusable executor's successful result into the result callers need. For example, the TypeSafe executor returns a provider response containing `answers.repeat.noul`. A support-triage skill can publish that value as `repeatContactProbability`. Direct callers and planning parents then depend on the triage skill's vocabulary.

```yaml
output_transform:
  language: jslt
  expression: |
    {
      "assignedTeam": .answers.team.choice,
      "repeatContactProbability": .answers.repeat.noul,
      "impactScore": .answers.impact.score
    }
```

## Requirements

- Support this root field exclusively on executor-backed YAML skills. It can be used alone or together with `input_transform`. Follow the declaration and generation semantics of PR 31.4.1: exactly `language: jslt` and a nonblank string `expression`, compiled at startup or namespace preparation and captured with the leaf generation.
- After the executor succeeds, take its ordinary result **text**, parse exactly one complete JSON value, and bind JSLT's `.` to that value. Evaluate the compiled expression once at the leaf boundary. The skill author owns the expected shape of the executor response. A missing field follows normal JSLT lookup semantics. Use the existing transform engine's object-construction behavior for nulls and empty containers.
- Serialize **any JSON result** as the skill's result text. Strings retain their JSON quotes and escaping. Objects, arrays, numbers, booleans and JSON null are valid root results. Direct invocation, parent tool calls, `child_result` bindings and `output_from` all consume the same published result.
- Invalid executor result text or a JSLT evaluation error fails that skill invocation. The executor may already have performed side effects. A transform failure must neither retry the executor nor publish the raw response as a successful fallback. An executor failure follows its existing failure path. With the field omitted, ordinary executor result text passes through exactly, including empty or non-JSON text.
- Keep the raw executor result and published skill result distinguishable in canonical diagnostics under existing access and resource controls. Scope is Framework authoring and execution; the TypeSafe response serves as an example. Executor `output_schema` support, provider-pack migration and Java extension API changes are outside this ticket.

## Acceptance criteria

- [ ] An executor leaf loads with either transform independently or with both. Invalid declarations, unsupported languages and compilation errors fail at startup/preparation. Model-backed YAML rejects the field. Failed preparation leaves the active generation intact, and a retained call uses its captured expression.
- [ ] Given a raw response containing choice `returns`, repeat probability `0.95` and impact score `0.8`, the example publishes `{"assignedTeam":"returns","repeatContactProbability":0.95,"impactScore":0.8}`. Direct invocation and a parent forwarding the leaf through `output_from` receive the whole result. A parent binding from `/repeatContactProbability` receives the number `0.95`. Consumers never reapply the expression.
- [ ] All JSON root types work as expression inputs and results. For a raw object with no matching field, a simple missing-field lookup publishes the four-character JSON text `null`. A string result publishes quoted JSON such as `"returns"`; quotes and backslashes inside string values round-trip. Explicit null members and empty containers survive object construction.
- [ ] Empty text, bare unquoted text, malformed JSON and multiple JSON values fail before evaluation. A valid JSON string, including `""`, succeeds with the identity expression. An evaluation failure and a parse failure each report a failed skill after exactly one executor call. Executor failures skip the transform. A leaf omitting the field still returns its original text byte-for-byte for ordinary string results.
- [ ] Authoring guidance explains the opt-in JSON requirement, missing-field behavior and string quoting. Diagnostic evidence distinguishes the original executor text from the transformed result and identifies transform failures after successful executor work.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The declaration changes the serialized skill result across direct invocation, parent orchestration and diagnostics, with a failure boundary after potentially side-effecting work.
- **Reassessment triggers:** Reassess scope if publishing one result across all consumers requires a supported API change.
- **Implementation model:** GPT-6.2 Sol.
