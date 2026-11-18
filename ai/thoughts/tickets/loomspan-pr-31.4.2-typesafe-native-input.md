# PR 31.4.2 — Send native TypeSafe requests from executor input

## Outcome

The TypeSafe executor in the sibling `loomspan-extensions` repository takes the complete provider request from `SkillExecutorInvocation.input()`. A skill can accept native `model`, `state` and `questions` directly or construct them from business input using Framework's executor-only JSLT transform. Both routes use one provider validation and transport path.

## Requirements

- Depends on PR 31.4.1 and its Framework artifact being available to local consumers. Implementation model is GPT-6.2 Sol.
- Retire the pack's `config.request` template and `config.bindings` pointer mapping. Remove TypeSafe leaf-level connection overrides and use the captured executor namespace configuration for `api_key` and `base_url`, retaining existing transport defaults. Preserve Framework's general `config` capability for other executors.
- Keep the decisions pack responsible for provider request validation, serialization, transport, retries and the complete provider JSON result. The pack consumes the Framework handoff through the supported v1 API and has no JSLT dependency.
- Migrate the pack's support-triage recipe, fixtures, consumers, and current authoring guidance in Extensions and Framework. Planning parents may still use Framework `input_bindings` to supply a child argument or select a previous child's answer. Those bindings serve a different purpose from the retired pack mapping.

## Acceptance criteria

- [ ] The migrated `triage` leaf accepts its existing ticket input and uses `input_transform` to place the ticket object in native `state`. A captured HTTP request to `/v1/systemone` contains the declared model and the recipe's team, repeat and impact questions. The parent can still pass the ticket, select `/answers/repeat/noul` from triage and supply it to `assess` for the follow-up request.
- [ ] A leaf accepting equivalent native input directly, with the transform omitted, sends an equivalent request. Both paths use captured connection settings, perform provider validation before sending, and return the complete provider response. Verify through the installed pack's consumer path.
- [ ] Current examples and authoring docs in both repositories describe the new request path and connection settings. Removed TypeSafe config keys fail explicitly rather than silently altering a request or its connection.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This is an intentional executor configuration migration across repositories with a provider protocol and a runnable consumer recipe to verify.
- **Reassessment triggers:** Reassess sequencing if the consumer environment does not yet have the Framework transform and its pinned dependency.

## Pipeline notes

The old TypeSafe configuration contract may be removed outright during development. Migrate in-repository consumers together. Do not introduce compatibility aliases, fallback request construction or a dual-format transition.
