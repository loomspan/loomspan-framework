## Code Review Findings

No actionable findings.

## Findings Resolved in This Context

None. No implementation changes were made. This review document is the only
retained repository change from this context.

## Open Questions and Assumptions

None affecting completion. The selected profile is `full` (Full 5-Step Pipeline).
No profile reassessment or developer decision is needed.

## Scope and Independent Review

Reviewed the working tree against HEAD `b32c5d50ad1eff336d4c14bc9a4ca9cd521f48d0`
on `main`: six tracked modified files, no staged changes, and the ticket-related
untracked diagnostic script, two test classes, research, plans, ticket and evidence.
There is no committed ticket delta or separate comparison base. Prior review
documents were excluded and were not read.

The only production change is `SkillInputPromptRenderer`. Read the full renderer
and direct renderer tests, all changed test/documentation regions, the complete
opt-in diagnostic and Python script, and relevant surrounding resolver/schema,
validator, builder, engine/correction/parser and YAML catalog paths. Reviewed the
governing ticket, research, both plans, repository AGENTS, design lens and shared
protocols. Defect review preceded requirements comparison; no edits were needed.

The shared rule walk now emits local required/optional/closed/open/constrained
semantics in both detail modes and traverses nested arrays and additional-value
schemas without the old rule-depth cutoff. Sorted names and JSON-quoted unusual
property paths keep output deterministic and distinguish literal dotted keys.
The illustrative header and conditional-child explanation preserve optionality.
Strict-empty and generic identities remain separate. Builder metadata remains the
contract authority; size/depth selection and correction escalation are unchanged.

Runtime validation, exact assignment, visibility/authorization and execution
remain independent of the text renderer. The engine regression verifies both
root-extra and closed-child-extra rejection before the sole accepted tool call,
the complete rejected candidate, unchanged evidence and accepted nested extras.
No new mutable state, executor/resource lifecycle, provider behavior, schema
authority, automatic forwarding, persisted format or compatibility path exists.
Prompt growth is proportional to the already-rendered schema tree; complete
boundary information is intentional. No content classification or rewriting is
introduced; the existing journal-projector exception is untouched.

## Verification Results

- PASS — `./mvnw.cmd '-Dtest=SkillInputPromptRendererTest,StepPromptBuilderTest,SkillInputContractResolverTest,SkillInputValidatorTest,StepActionValidatorTest,StepLoopMissionExecutionEngineTest,LoomspanPublicSurfaceArchitectureTest' test`: 148 tests, zero failures/errors/skips.
- PASS — `./mvnw.cmd '-Dtest=Pr18RecordedHandoffDiagnosticTest' '-Dpr18.diagnostic=true' test`: two tests, zero failures/errors/skips; actual engine request capture and actual internal Framework parser plus catalog-normalized validation exercised.
- PASS — `python $env:TEMP/pr18-review2-integrity.py`: read-only provenance, trace reconstruction, envelope/outbound controls, retention and source-integrity recomputation matches every retained generated JSON document. All six raw outputs match saved parsed actions, allowing the documented fence unwrapping and omitted null record fields. Five primary source captures and all 136 protected suite files match their provenance hashes.
- PASS — `git diff --check`: no whitespace errors.
- PASS — `./mvnw.cmd test`: 1262 tests, zero failures/errors, two expected opt-in diagnostic skips; BUILD SUCCESS. The opt-in tests were separately exercised above.
- NOT RUN — further `python ai/scripts/pr18_guidance_diagnostic.py live`: no new evidence calls for more provider sampling; retained repeated calls satisfy the requested diagnostic attempt and no live accuracy generalization is made.

Maven output was captured in `%TEMP%/pr18-review2-targeted.log`,
`%TEMP%/pr18-review2-diagnostic.log`, and `%TEMP%/pr18-review2-full.log`.
The opt-in harness rewrites derived evidence. Its execution was wrapped in an
in-memory byte snapshot and `finally` restoration of every existing evidence file,
so the retained evidence was left byte-identical. The integrity verifier ran again
afterward. This verification did not modify implementation artifacts or raw/source
evidence, and did not make provider calls or deploy services.

The read-only verifier imports the reviewed diagnostic module, replaces `save`
with an in-memory dictionary, runs `prepare`, `controls`, and `evaluate`, compares
every generated value to its saved JSON, asserts source-integrity flags and arm
counts, checks all mandatory source objects and revised exactly-once issued quotes,
and compares each decoded raw response with its derived action. It compiles the
Python source without creating a compiled artifact and hashes the evidence before
and after. Its exact source is appended to this review for reproducibility.

## Requirements and Plan Conformance

| Ticket criterion | Independently checked evidence |
| --- | --- |
| Closed root/open child and nested required fields | Both-mode renderer regression, builder and ordinary/corrective engine tests; normalized recorded root/context assertions and actual engine request |
| Other boundaries, arrays, typed additional values and illustrative optionality | Deep consecutive-array/additional-schema matrix, closed/open/empty/generic cases, literal dotted-key paths, optional-parent regression and typed-value validator test |
| Compact, verbose and correction meanings; domain/model independence | Shared walk; unrelated shipment/catalog fixtures; depth-two six/seven-property test actually selects ordinary compact/verbose and forced verbose; engine correction capture |
| Existing validation, input/evidence fidelity and API surface | Validator/resolver/action/engine regressions and architecture allowlist test; no production delta outside renderer |
| Actual Framework request for recorded handoff | Opt-in actual engine model-boundary capture; complete saved request and normalized contract; exact reconstructed sequence-480/provider-journal linkage |
| Controlled repeated GLM attempt with fixed inputs | Six retained alternating outbound/HTTP200 response pairs; guidance-only substitution and equal remainder of historical envelope; model/reasoning settings held fixed; raw responses/failed prior attempts preserved separately |
| Separate deterministic and live conclusions | Deterministic tests and parsed-contract validity reported separately from exact evidence retention; explicit one-handoff/three-per-arm limitation |

Implemented: all ticket acceptance criteria and both plans' completion gates.
Partial: none. Missing: none.

Safe deviations: description rendering is deferred as the ticket permits. The
admitted engine capture starts at step 1 and regenerates user JSON formatting;
the diagnostic records this difference and checks canonical input equality. The
live pair retains the original complete step-5 envelope/user bytes and transplants
only the captured guidance section. Neither difference contaminates the fixed-arm
comparison. Retained invalid encoding and unnormalized-contract attempts are
explicitly excluded from final scoring.

Compatibility review: Application API and sole `RestSkillHandler` SPI are unchanged;
the public-surface allowlist passed. Renderer public visibility is internal
collaboration, not supported extension exposure. Configuration/manifest defaults,
validation/reference semantics, invocation and permissions remain unchanged.
Only internal prompt wording/current diagnostic content changes. No new public
signature, conditional bean override, durable/serialized contract or Java-to-Go
REST/SSE/acquisition/problem/NDJSON boundary is introduced. No shim or protocol
marker change is needed; no ticket Pipeline notes authorize broader changes.
Historical fixtures, experimental skills and business checks remain unchanged.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected.
- **Rationale:** Authors need accurate expectations for generated illustrations,
  optional parents and scoped object permissions, while business data selection
  remains authored and enforcement remains unchanged.
- **Documents reviewed:** `agent-skills/loomspan-docs/SKILL.md`,
  `references/skill-authoring/README.md`, `input-contracts.md`, and
  `source-verification.md` under that skill.
- **Evidence checked:** Changed renderer; resolver/validator; builder detail
  selection; engine request/correction paths; named renderer, builder, engine and
  validator tests; opt-in normalized YAML/request capture and retained controls.
- **Coverage table:** Current; accurately includes scoped compact/verbose/corrective
  guidance and preserves the incomplete pure-YAML syntax qualification.
- **LLM-first usability:** Pass. Applicability, illustration versus enforcement,
  business ownership, boundaries and stable executable anchors are explicit.
- **Drift classification:** aligned. Checkout skill metadata and Maven version are
  both `1.0.0-beta.8-SNAPSHOT`; claims were checked against matching executable paths.

## Residual Risks and Optional Developer Checks

The six retained responses all passed actual Framework parsing, normalized input
validation, exact assignment and identity checks and retained all five mandatory
source values. Exact complete additional evidence fidelity is old 0/3, revised
3/3, including both issued quotes exactly once unchanged in each revised response.
These are observed fixed-handoff counts, not a reliable universal accuracy claim.
Provider routing/default sampling is not immutable; both arms use the same known
request settings and absent historical settings. No broader benchmark is required.

Optional developer checks: none. No unavailable environment blocks deterministic
verification or the requested retained diagnostic attempt.

## Disposition

**Approve** — no actionable findings; sufficient independent verification.
`REVIEW_RESULT: clean`. No implementation fixes were made in this context.

## Read-Only Integrity Verifier

```python
import importlib.util, pathlib, json, hashlib
root=pathlib.Path.cwd()
spec=importlib.util.spec_from_file_location('pr18',root/'ai/scripts/pr18_guidance_diagnostic.py')
m=importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
outputs={}; m.save=lambda name,value: outputs.__setitem__(name,value)
read=lambda name: json.loads((m.OUT/name).read_text(encoding='utf-8'))
initial={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in m.OUT.rglob('*') if p.is_file()}
m.prepare()
for name in ['trace-provider-linkage.json','diagnostic-input.json','historical-provider-request.json','source-provenance.json']:
    assert outputs[name]==read(name),name
m.controls(); m.evaluate()
for name,value in outputs.items():
    assert value==read(name),name
assert outputs['source-integrity-after.json']['sourcesUnchanged']
assert outputs['source-integrity-after.json']['protectedSuiteSourcesUnchanged']
r=read('business-retention-evaluation.json')
assert r['counts']=={'old':{'samples':3,'parseValid':3,'completeExactEvidenceFidelity':0},'revised':{'samples':3,'parseValid':3,'completeExactEvidenceFidelity':3}}
for sample in r['samples']:
    assert sample['assignmentValid'] and sample['identityPreserved']
    mandatory=['equipmentAssessment','assetContext','serviceHistory','referenceEvidence','serviceTerms']
    assert all(sample['canonicalEvidence'][field]['exactValue'] for field in mandatory)
    if sample['arm']=='revised': assert all(q['exactlyOnceUnchanged'] for q in sample['authoritativeIssuedQuotes'])
final={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in m.OUT.rglob('*') if p.is_file()}
assert initial==final
compile((root/'ai/scripts/pr18_guidance_diagnostic.py').read_text(encoding='utf-8'),'pr18_guidance_diagnostic.py','exec')
print('PASS: read-only recomputation matched every generated evidence document; 6 outbounds, 6 parsed/valid actions, exact retained values, 5 source captures and 136 protected sibling files verified; all evidence bytes unchanged.')
for path in sorted(m.OUT.glob('live-*-response.json')):
    saved=json.loads(path.read_text(encoding='utf-8'))
    content=saved['response']['choices'][0]['message']['content'].strip()
    if content.startswith('```'):
        content=content[content.index('\n')+1:content.rfind('```')].strip()
    raw=json.loads(content)
    action=read(path.name.replace('-response.json','-action.json'))
    assert all(action[k]==v for k,v in raw.items())
    assert all(v is None for k,v in action.items() if k not in raw)
    assert saved['status']==200
print('PASS: all 6 raw provider outputs exactly match their saved parsed actions; fenced syntax is handled separately.')
```
