# PR 7 — Prepare skill updates from application-supplied content

## Outcome

An application can supply a complete set of YAML skill documents from its own
storage, validate and freeze that candidate, stage matching external REST resources,
and activate it through the existing publication lifecycle without replacing a live
skill directory. Applications can use this after ordinary startup, after a process
restart, and for subsequent updates.

## Deliberate simplicity

Always initialize using configured skill locations, defaulting to
`classpath:/skills/**/*.yaml`. If no resources are found, initialize successfully
with no YAML skills; existing Java skills remain available. Invalid discovered
content still fails validation as it does today.

There are no mutually exclusive loading modes. An application may freely alternate
between preparing configured resources and preparing supplied content:

- `prepare()` reads and validates the configured skill locations.
- `prepare(Collection<SkillDocument> documents)` validates and freezes the supplied
  complete YAML set.
- `publish(candidate)` activates either kind of prepared candidate.

Preparing or publishing supplied content does not change
configured locations. A later no-argument `prepare()` reads those locations again.

The absence of an initialization switch, deferred activation, a special
not-initialized state, or framework-enforced pre-publication execution gating is
intentional. These are not gaps to fill during implementation. An empty YAML startup
  generation is a valid active generation. Applications that need content restoration
or REST resource staging before accepting traffic own that readiness policy.

## Requirements

- Add the supported public value type `ai.loomspan.api.SkillDocument` as
  `public record SkillDocument(String sourceName, String yaml) {}` and the overload
  `PreparedSkillUpdate prepare(Collection<SkillDocument> documents)` to
  `SkillReloader`. These names and the document representation are agreed API
  decisions, not illustrative implementation suggestions. Keep the no-argument
  `prepare()` method.
- Source names are nonblank logical diagnostic labels, not filesystem paths or
  resource locators. A label such as `support/triage.yaml` does not imply file access
  or an execution hierarchy; relationships remain defined by YAML declarations
  such as `allowed_skills`. Reject duplicate source names using exact string
  equality, independently of duplicate declared skill names. Reject null collections,
  documents, source names, and YAML strings; invalid or empty YAML goes through
  framework validation. Do not require a filesystem path or filename extension.
- Copy the supplied collection during preparation and use its immutable string
  values to prepare the candidate. Later collection mutations cannot change the
  candidate. Callers must not mutate the collection while preparation is copying it.
  Use the existing YAML declaration format rather than introducing a parallel Java
  schema model. The public input contract must not require Spring resources,
  streams, or filesystem objects.
- Extend the supported public surface to accept a complete, explicitly supplied
  collection of named YAML skill documents. Callers must be able to supply content
  already held in memory, without temporary files, symlinks, custom filesystem
  providers, internal imports, or replacement of framework beans.
- Preserve framework authority over parsing, validation, child references, model
  references and skill authorization. Support the existing model-backed and REST
  YAML declaration surface. Existing Java declarations and model/handler bindings
  remain fixed; this is not Java hot swap or model-connection reload.
- Preparation must freeze the supplied content and return a candidate catalog and
  generation ID without activating it. Subsequent caller mutations or storage
  changes cannot alter that candidate. Publication must not reread source content.
- The input is a complete replacement YAML set, not a merge or incremental patch.
  Preserve existing empty-set semantics and validation of duplicates and invalid
  declarations. Named sources must produce useful diagnostics without requiring
  physical paths, using the source-name rules above.
- Reuse the existing preparation/publication lifecycle for both sources. Failed
  preparation or rejected publication preserves the active generation, including
  the ordinary startup generation. Keep existing stale/foreign/repeated candidate
  and shutdown rejection guarantees across both preparation methods. Competing
  candidates use the same rules regardless of source.
- Preserve existing automatic directory-based startup, no-argument preparation,
  and catalog/snapshot behavior. Do not introduce a loading-mode selection or new
  initialization state. A directory-only preparation API is insufficient for this
  ticket's application-supplied content requirement.
- After a restart, the application can select its durable snapshot and resubmit its
  content through the new preparation method after ordinary framework startup.
  The framework assigns a fresh process-local generation ID. Publication neither
  persists the supplied content nor changes what configured locations load on the
  next restart. Do not require preservation of old framework IDs.
- Keep database access, durable active-pointer recovery, route/client storage and
  application readiness policy outside the framework. Do not introduce SQLite,
  Sidecar schema knowledge, a generic storage plugin system or a distributed
  transaction with application persistence.

## Acceptance criteria

- [ ] The supported public API accepts `Collection<SkillDocument>` with logical
  source names and YAML strings, while retaining no-argument `prepare()`. Null
  inputs, blank source names, duplicate source names, and invalid YAML are rejected
  according to the contract. Logical labels require neither physical paths nor
  filename extensions and do not define execution relationships.
- [ ] An application with no resources at the default skill location starts normally
  with no YAML skills and retains any Java skills. Through supported public APIs,
  it prepares in-memory model-backed/REST YAML, stages generation-keyed REST
  resources, publishes, and executes without skill files on disk.
- [ ] Failed supplied-content preparation preserves the active generation and
  allows a corrected candidate to be prepared without process restart.
- [ ] Candidates from both preparation methods freeze complete input content, provide
  useful source diagnostics, and preserve full validation and empty-set semantics.
- [ ] Supplied documents receive the same parsing, model-reference, child-reference,
  duplicate-skill, and authorization behavior as configured resources. Source naming
  and duplicate-source behavior are documented and demonstrated without physical
  paths. Caller mutations after preparation cannot alter a candidate.
- [ ] Supplied content replaces the entire YAML set, retaining fixed Java skills and
  model/handler bindings. Publishing an empty supplied set removes all YAML skills.
- [ ] Publication activates the prepared content only; old admitted/running trees
  keep their generation while new roots use the replacement generation.
- [ ] Foreign, stale, repeated, competing and shutdown-time publication
  cases obey the documented lifecycle without partial activation.
- [ ] A restart example resubmits the same application-owned durable snapshot and
  stages its REST configuration against the newly assigned framework generation ID
  after ordinary startup. Publication does not persist content or change restart
  discovery; persistence, recovery, and traffic readiness remain application-owned.
- [ ] Existing directory-based applications retain their startup/reload behavior.
- [ ] An application can alternate between both preparation methods. Supplied-content
  publication leaves configured locations unchanged, and subsequent no-argument
  preparation reads those locations. Cross-source competing candidates follow the
  existing stale-candidate rules.
- [ ] Documentation explains automatic startup, both preparation methods, complete
  replacement semantics, source identity, and application-owned persistence and
  readiness. It explicitly identifies the omitted startup controls as intentional.
  Examples use only supported public contracts.

## Context

Replacing live skill files before validation can leave invalid files behind even
when the running process retains its valid generation. A restart then reads those
invalid files. Supplying candidates separately lets applications validate updates
without overwriting their durable active content.

Sidecar is planning an embedded console with session-owned edits and publication
of complete skill/REST snapshots. Database storage, potentially SQLite for the
beta, is being evaluated but is not selected. The framework contract must work
independently of that choice: an application reads storage and supplies content.

Current `SkillReloader.prepare()` discovers configured skill locations and offers
no source argument. The scope is a supplied-content preparation overload that reuses the existing
lifecycle. Database-backed restoration happens after normal framework startup;
application readiness controls when execution traffic is accepted.

The supplied-document API is deliberately a collection of `SkillDocument` records,
not a map: named fields make the two strings explicit, and a collection lets
Loomspan detect duplicate source names instead of losing them to map overwrites.
It is not called a skill tree because execution relationships live in the YAML.

The proposed Sidecar startup flow is: allow ordinary framework startup,
read the application's durable active snapshot,
prepare its skills, stage matching REST configuration/clients under the fresh
generation ID, publish, then open execution traffic. A fresh installation with no
discovered YAML already has a valid startup generation and need not publish an empty
set to complete framework initialization. Any additional readiness policy belongs
to Sidecar.
This ticket does not implement Sidecar authentication, persistence, UI or imports.

This extension does not make physical replacement of configured files restart-safe.
Directory-based applications retain responsibility for the validity of their startup
files. Applications supplying content own durable snapshot selection and recovery
across persistence/publication failures. Framework publication is an in-memory
operation, not a durable storage transaction.

Coordinate with the [generation-retirement notification ticket](loomspan-pr-8-generation-retirement-notification.md)
so startup and replacement generations share safe cleanup semantics. Retirement
notifications remain in that companion's scope; this ticket does not introduce a
separate cleanup contract. Neither ticket permits Sidecar to depend on framework
internals.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Extends the supported public API and content preparation/validation
  path while preserving existing initialization, publication, and execution admission
  behavior. Requires public-surface, source-freezing, and cross-source lifecycle tests.
- **Reassessment triggers:** Current-checkout evidence that the supplied-content
  public contract already exists and the remaining work does not change supported
  API or lifecycle behavior.
