# PR 30.1.3 — Preserve Anthropic schemas and native options

## Outcome

Repair tool root-schema loss through the selected dependency strategy. Use native output configuration while preserving effort and other options. Support the selected acceptance model's native effort without rejected thinking budgets or cap inflation; preserve legacy-model behavior.

## Scope and sequencing

**Dependencies:** PR 30.1.1.

**Expected build state:** Anthropic adapter boundary should be testable; shared schema consumers may still use legacy contracts.

PRs 30.1.2–30.1.5 restore the provider transition; PRs 30.2.2–30.3.4 restore schema consumers by their owned boundaries; PR 30.4.3 reruns all blocked integrated checks.

Own Anthropic adapter transport/options only. The embedded typed-map developer contract describes the input schemas this adapter must carry; PRs 30.2.2/30.2.3 own Java generation and local validation, and PR 30.3.1 owns ordinary native-output orchestration. Do not implement the other drivers described in preservation context.

Execute in the agreed numerical order unless working on an independent prerequisite. Dependencies describe technical prerequisites, not permission to duplicate another ticket's implementation.

## Shared migration constraints

Exactly three chat integrations remain: shared OpenAI Responses for OpenAI, Meta,
OpenRouter and Ollama; native Anthropic; native Gemini. TypeSafe remains a separate
native evaluator. Retain Spring AI ChatClient/tool-loop ownership. No second adapter,
transport, agent loop, capability registry, conversation database, protocol selector,
automatic fallback or invented public API is authorized.

Canonical inputs, prompts, outputs, tool arguments, traces and diagnostics retain
content fidelity. Do not classify sensitivity, scan secrets, mask or sanitize content.
Enforce authentication, authorization and explicit access boundaries. Existing size
limits and explicit truncation are resource controls. Preserve only the existing
ExecutionJournalProjector field-name redaction exception for the derived journal and
SkillTemplate observer; do not broaden it to canonical traces, Console or invoke results.

The supported Java API is the closed allowlist of public top-level ai.loomspan.api
types in LoomspanPublicSurfaceArchitectureTest. Internal public types are implementation
details; autoconfigure signatures are not an SPI. User-visible keys/behavior require
deliberate migration. Do not expose internal/autoconfigure types in public signatures.
Run LoomspanPublicSurfaceArchitectureTest after production type changes; record it as
blocked if intentional intermediate breakage prevents execution and rerun on restoration.

The accepted standard profile is Draft 2020-12 with NetworkNT Jackson 3 (3.0.9 is the
verified starting candidate, not an irrevocable final pin). Complete contracts have
object roots; property trees support ordinary types, explicit nullable type arrays,
scalar enums/descriptions, homogeneous nested arrays, boolean openness, standard formats
and minimum/maximum/minLength/maxLength/minItems/maxItems. Ordinary inputs additionally
support typed maps and unconstrained {} leaves. Native output/planning typed maps,
general unions/composition, references, defaults, arbitrary patterns and other unapproved
features remain excluded. Internally generated false property schemas preserve binding
ownership. Exact names, standard omission/null/openness and no coercion apply. No
automatic all-required conversion, null insertion, closure, wrapping or map reshaping.
Native strict tools are deferred; retain non-strict schemas, local pre-dispatch validation
and authorization. Retain standard local checks even when a provider ignores assertions.

Deferred Java declaration exploration (Optional wrappers, polymorphic inputs,
array-shaped DTOs and delegating creators) is excluded. Do not restore removed probes
or invent their restrictions. The retained map-key, JsonUnwrapped and UUID corrections
remain owned by PR 30.2.2. Native generated attachments are excluded.

No live provider calls, model downloads, external issue/PR submissions or test-suite
implementation are authorized by this ticket. All verification here is deterministic
or local build/startup. The neighboring suite owns later deployed/live acceptance;
missing live evidence is not permission to weaken Framework requirements.

## Acceptance criteria

- [ ] Actual Anthropic tool requests preserve all approved root and nested constraints; native output schema and effort survive per-call combination without canonical mutation.
- [ ] The selected model receives native medium effort, no rejected explicit thinking budget, and unchanged generation caps; legacy-model behavior remains covered.
- [ ] Every applicable mandatory case in the detailed contract below has executable evidence, or an explicitly identified downstream integration owner and blocked-check record consistent with this ticket's build allowance; no in-scope required behavior is silently deferred.
- [ ] Owned fixtures and callers migrate with the behavior; preservation controls and the public-surface check are run when buildable, and all failures/blocked checks are reported accurately.

**Verification emphasis:** Actual tool/output request captures, root and nested constraints, changing per-call schemas, effort/format combination, unchanged generation caps and legacy controls.

## Anthropic adapter evidence and minimal correction

SAI-02: AdapterTest.anthropicConvenienceAndNativeAndOptions and
CustomizationTest.anthropicMutatingDefaultsPreservesEffortAndNativeSchema capture a
fresh per-call OutputConfig replacing default effort even through ChatClient. Copy
the existing OutputConfig and replace format; native raw output configuration preserves
the schema. Preserve max_tokens and other defaults. No upstream merge change is required
to use that supported construction path.

SAI-03: AnthropicChatModel.toAnthropicTool copies root properties/required but loses
additionalProperties and other root constraints. Nested schemas survive. The direct
SDK InputSchema control preserves root entries; the minimal candidate is to populate
all authored root entries in the existing adapter, not write another adapter. Use a
verified fixed artifact or focused upstream correction; no options-only bypass was
established. Source inspection places this in RC2 AnthropicChatModel at toAnthropicTool
(audit-time line 1652). Retained requests include anthropic-native.json,
anthropic-sdk.json and anthropic-preserve-default-config.json in the TBD-06 probe.

Current SpringAiChatOptionsContributor maps medium to 4096 thinking tokens and raises
maxTokens to at least 8192. That legacy mapping is unsuitable for the selected Sonnet
fixture. RC2 exposes effort(OutputConfig.Effort.MEDIUM) and adaptive thinking options;
choose supported selected-model configuration without explicit rejected budgets and
prove actual wire behavior. Do not globally change older model behavior.

## Deterministic option fixtures for later acceptance

Use the agreed selected routes as offline fixture identities: direct gpt-6.1-terra,
OpenRouter openai/gpt-6.1-terra restricted to standard endpoint tag openai with fallback
disabled and require_parameters enabled, Meta muse-spark-1.3-contributor (all explicit
medium); Ollama qwen3.5:4b-q4_K_M with enabled boolean thinking, not a fabricated medium;
Anthropic claude-sonnet-5-5 native output effort medium; Gemini gemini-3.8-flash native
thinking level MEDIUM. These are accepted fixtures, not fresh availability claims.
The inspected Ollama baseline was 0.40.16 with digest
2a654d98e6fba55d452b7043684e9b57a947e393bbffa62485a7aac05ee4eefd.

Capture unchanged total generation caps (reasoning plus visible output): 1024 for a
small structured result, 4096 for tool-chain/planning requests and 2048 for attachment
requests. Cover initial, continuation, planner and model-override option combination
as those consumers become available. Phase 1 proves adapter/factory behavior; PRs
30.3.1/30.3.2 prove migrated consumer wiring; PR 30.4.3 reconciles the full matrix.
Preserve legacy-model behavior. Unsupported options fail without paid sends, implicit
fallback, increased limits or invented settings. Routing support must use existing
extension facilities, not endpoint/model guessing or another provider abstraction.

The later suite uses a 180-second physical request boundary, 210-second proxy boundary,
900-second mission boundary and 960-second runner deadline. Preserve configurable
timeout semantics; do not mistake socket inactivity for a total elapsed deadline.
Suite overlays, reservation ledgers and runner changes belong to the suite, not here.
Its total ceiling is USD 5, 81 physical requests (69 cloud), zero automatic transport
retries/schema repairs in the initial live batch, and separate paid-run authorization.
These are not new production defaults. No sends are authorized here.

## Detailed accepted contract and evidence

The following bounded extracts carry the applicable decisions, exact acceptance cases
and historical code/test anchors into this ticket. Scope ownership above determines
which cross-boundary statements are implementation here versus preservation context.
Statements about earlier research runs remain historical; do not claim they were rerun.
References to future tickets in retained text mean requirements carried by this ticket
set, not permission to postpone this ticket's owned work. Older proposals do not override
accepted decisions or the explicit exclusions above.

### Contract 1

#### Accepted SkillMethod/SkillExecutor developer model and typed-map inputs — November 11, 2026

Preserve the existing developer-facing roles and signatures. SkillMethod remains a
Java-first callable declaration: Java parameters, SkillParam metadata and existing
runtime-reference handling produce its canonical standard JSON Schema. It already
emits standard schema vocabulary; the migration removes lossy internal conversion and
custom structural validation, not its Java authoring model. SkillExecutor remains a
reference-only implementation accepting one SkillExecutorInvocation; its YAML skill
owns the business-input contract. Migrate that YAML to standard schema syntax without
reflecting the invocation/context/configuration record into a competing input schema.
Preserve duplicate callable-name rejection, executor handoff and configuration behavior.
No new annotations, signature redesign, schema override/merge system or public API is
selected. Existing ownership is a preservation requirement, not a new policy mechanism.

Compile generated and authored input contracts through the same standard pipeline.
Validate before Java binding; conversion cannot rescue an invalid contract. Preserve
map parameters and existing resource handling with the separately agreed adapters.
Already agreed exact-name/no-coercion and omission/null rules still apply: preserving
signatures does not preserve acceptance of numeric strings as integer inputs.
Recursive Java input types are rejected as agreed below; unconstrained Object input support is agreed below;
this agreement does not promise every existing signature is supported.

For example, Map<String, Integer> quantities retains a named object property whose
additionalProperties is {type: integer}. NetworkNT validates each entry before Java
binding. Allow that input vocabulary for authored schemas too, rather than creating a
Java-only exception. Maps used for execution/configuration/internal state are unaffected.
Keep existing runtime-reference traversal of map-value schemas and whole-value binding
transfers. Do not introduce arbitrary-key binding paths or general schema algebra.

Provider implementation scope:

- Responses: preserve the full input schema and explicitly send tool strict=false.
  OpenAI, Meta, OpenRouter and Ollama share this mapper; route acceptance is separate.
- Anthropic: preserve nested typed-map schemas and fix the already identified root
  constraint loss. This root fix is independently required for ordinary contracts.
- Gemini: preserve the canonical schema through raw parametersJsonSchema, bypassing
  both GoogleGenAiToolCallingManager's OpenAPI conversion and the final parameters(Schema)
  conversion. A final-setter change alone is insufficient. Raw SDK serialization is
  evidenced; the exact Spring AI integration remains an implementation gate, not an
  established options-only workaround. Keep the existing tool manager/loop ownership.

This is the simplest evidenced direction: retain useful Java declarations and fix
concrete schema-fidelity gaps. It needs no second validator, new tool loop, map conversion,
provider registry or silent schema weakening. Output/planning feature restrictions stay
separate. Relevant provider contracts: [OpenAI function calling](https://developers.openai.com/api/docs/guides/function-calling),
[Anthropic strict tool use](https://platform.claude.com/docs/en/agents-and-tools/tool-use/strict-tool-use),
[Google FunctionDeclaration](https://aistudio.google.com/api-reference/caching#Resource:FunctionDeclaration).

Mandatory ticket acceptance tests:

- Generate and validate string-, integer- and record-valued maps, nested maps and maps
  inside arrays/records. Preserve value constraints, descriptions, requiredness and
  closed record properties. Invalid entries fail with precise paths before invocation;
  integer-valued maps reject numeric strings under the agreed no-coercion policy.
- Equivalent authored and generated input contracts have equivalent standard validation.
  Preserve input values, Java binding, runtime-reference traversal and whole-map binding
  transfer without expanding binding path syntax. Retain direct runtime-value adapter
  tests for marked leaves reached through map-value schemas.
- SkillMethod parameter metadata remains effective. YAML executor input validation
  precedes invocation; SkillExecutorInvocation input/configuration/context handoff and
  duplicate callable-name rejection remain unchanged. Do not generate a business-input
  contract from the executor's invocation record.
- Capture actual requests through all three integrations. Assert complete nested map
  schemas and ordinary root constraints, explicit Responses strict=false, and Gemini
  parametersJsonSchema fidelity without prior lossy conversion. Preserve options and
  canonical schemas; no fallback or replacement of constrained values with open maps.
- Later suite gate, not execution in this ticket: verify selected live provider/model routes, including a typed-map tool alongside a
  separate supported structured final-output contract. Shared mapper proof does not
  prove Meta/OpenRouter/Ollama route acceptance. Missing credentials or rejected features
  remain explicit gates, not passing results. Full framework validation must prevent
  invalid tool arguments from invoking Java side effects.
- Continue rejecting typed-map native output/planning contracts under their deferred
  scope; input support must not silently enable excluded output features.

Existing AdapterTest sources/captures and RC2 source inspection support these findings;
no new live calls, production implementation or ticket creation accompany this agreement.

## Retained technical context

Historical research establishes the stated adapter/library behavior and limitations.
Broader historical fixtures do not expand the accepted v1 profile.

2. **Anthropic output:** native `outputConfig(...)` carries an intact raw schema.
   Preserve existing effort when replacing the per-call format; executable evidence
   below confirms this option-combination requirement.
3. **Anthropic tools:** `AnthropicChatModel.toAnthropicTool()` hardcodes a new input
   schema from properties and required, with no copy of other root entries and no
   strict setting. A local output-config workaround does not repair this tool mapper.
   Prefer an upstream mapper fix; quantify dropped fields with a mock HTTP fixture.

## Adapter fix acceptance

Prefer a focused upstream correction or a suitable verified release. Record the exact
tested artifact/commit and rerun fidelity cases against it. A merged PR is not proof
a fix ships. Do not deploy copied research classes, mutate SDK singleton mappers or
rewrite HTTP bodies to work around loss. If no acceptable artifact can satisfy the
owned contract, report that concrete dependency blocker; continue independent work
without declaring this ticket complete. External submissions are not authorized here.

## Reproduction anchors

Historical Anthropic request reproduction, using Java 21 and Maven:

```powershell
mvn -f docs/maintainers/probes/tbd-06/pom.xml test '-Dtest=AdapterTest#anthropicConvenienceAndNativeAndOptions,CustomizationTest#anthropicMutatingDefaultsPreservesEffortAndNativeSchema'
```

The fixture endpoint stops with an HTTP error after request capture. A passing
defect-reproduction assertion is not a repaired adapter or hosted capability.

## Pipeline notes

- Destructive updates are encouraged: this repository is in development with no
  deployments to support. Remove superseded implementations in place; do not add
  legacy readers, aliases, dormant paths, compatibility shims or fallback solely to
  keep intermediate commits building. Respect the supported public API boundary above.
- The developer commits between tickets without syncing. The stated intermediate
  build failures are authorized; identify each affected consumer/test and restoration
  owner. Do not mark blocked tests passed, remove assertions or skip required checks
  into success. Final integrated acceptance requires green checks.
- Preserve unrelated worktree changes and retained probes. Resource stream closure
  in SkillMethodBeanPostProcessor is already repaired, with success/failure/nested
  tests; preserve it rather than treating it as missing migration implementation.
- Source anchors and retained reports below are historical navigation/evidence, not
  fresh passing-test claims. Verify hints against the current checkout. Before claiming
  a new defect, trace callers, validation, execution and tests against the accepted
  requirement. Routine implementation choices are autonomous; broader product or
  architecture departures require a concrete evidence-backed question.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This ticket changes or provides outstanding assurance for a cross-cutting migration involving serialized contracts, external protocols or execution lifecycle behavior. Settled intent does not eliminate integration research, test planning or independent review.
- **Reassessment triggers:** A materially changed scope or checkout should be reassessed under the shared protocol; this recommendation does not waive any required review or verification.

## Reference navigation

- [Agreed overview](../phases/migration-ticket-overview.md)
- [Coverage ledger](../phases/migration-ticket-coverage.md)
- [Migration roadmap](../phases/spring-ai-responses-migration.md)
- [Historical schema research](../phases/standard-json-schema-research.md)
- [TBD-06 reproduction and retained evidence](../../../docs/maintainers/probes/tbd-06/README.md)
