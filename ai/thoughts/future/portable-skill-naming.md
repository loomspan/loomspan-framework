# Portable namespace and skill naming

**Status** — Proposed greenfield contract for Loomspan Framework, Sidecar, and Cloud.

An application owns a namespace such as `order-support`. Within it, an author declares a skill such as `find-order`. Its identity is the ordered pair `(order-support, find-order)`. Keeping both components lets an author use familiar Agent Skill names while giving each integration a spelling it accepts.

## One identity across the system

Framework assigns ownership at declaration. Discovered startup YAML belongs to `default`, including files nested under directories such as `skills/orders/`. Those directories locate the file and appear in path diagnostics. YAML passed to `SkillReloader` belongs to the namespace supplied by the caller. Java declarations use the namespace on `@SkillMethod` or `@SkillExecutor`, which defaults to `default` when omitted.

| Use | Representation |
| --- | --- |
| Namespace declaration | `order-support` |
| Skill declaration or reference within the owning namespace | `find-order` |
| Qualified Loomspan reference in authored documents, catalogs, plans, and diagnostic displays | `order-support:find-order` |
| Framework invocation target | Namespace `order-support` and local name `find-order` supplied separately |
| Sidecar or Cloud invocation target | `{"namespace":"order-support","skill":"find-order"}` |
| Function name advertised to a model and returned in a tool call | `order-support--find-order` |
| Cloud application companion pack | `order-support/SKILL.md` with frontmatter `name: order-support` |
| Individual skill pack, if offered | `find-order/SKILL.md` with frontmatter `name: find-order` |

## Component grammar and length

Both the namespace and the local skill name must start with a lowercase ASCII letter. Each must match the entire ASCII pattern `[a-z][a-z0-9]*(?:-[a-z0-9]+)*` and contain 1–64 characters. Digits are allowed after the initial letter, including immediately after a hyphen. For example, `lookup-3pl` is valid in either position, while `3pl-lookup` is invalid in both. Single hyphens separate nonempty groups. Uppercase letters, underscores, whitespace, leading or trailing hyphens, and repeated hyphens are invalid. Exclude components containing `anthropic` or `claude` so either component can serve as an exported skill name. Validate the supplied spelling as written and report invalid names at declaration time. [Agent Skills name specification](https://agentskills.io/specification#name-field) · [Anthropic skill requirements](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/overview)

Callable skills and reference-only Java executors follow the same component grammar and combined length limit. At declaration, both must satisfy `length(namespace) + 2 + length(localName) <= 64`. The two characters account for the `--` separator used when advertising a callable skill to a model. Loomspan counts those two characters for executor registrations as well. For example, the 13-character namespace `order-support` leaves 49 characters for either a skill or an executor local name. A 50-character local name fails validation in both cases. [OpenAI function names](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create) · [Anthropic client tool names](https://platform.claude.com/docs/en/agents-and-tools/tool-use/define-tools)

Gemini function names must begin with a letter or an underscore. The namespace comes first in the advertised function name, so a lowercase initial letter satisfies this requirement. Loomspan applies the same initial-letter rule to the local name for consistency. [Google function declaration contract](https://docs.cloud.google.com/java/docs/reference/google-cloud-vertexai/latest/com.google.cloud.vertexai.api.FunctionDeclaration)

## Java declaration names

For both `@SkillMethod` and `@SkillExecutor`, an explicit annotation `name` supplies the local Loomspan name verbatim. When `name` is omitted or empty, use the Java method name verbatim. Validate the selected name at startup.

For example, `search()` can omit `name`. A method named `findOrder()` can declare `name = "find-order"`. If its annotation omits the name, fail at startup, identify the offending method, and tell the author to supply a valid annotation name or rename the Java method. The author chooses the spelling.

## Case in names and relative paths

Framework, Sidecar, and Cloud validate namespace and skill names at declaration and lookup. Both components of a qualified reference follow the component grammar above. An HTTP invocation validates the namespace and skill as identity components wherever the API carries them, including in a URL.

The loader that locates a skill definition enforces lowercase spelling for each directory and filename in its relative path, with the manifest exception below. Check a caller-supplied relative path as written before lookup, even on a case-insensitive host. For discovery and import, check the actual spelling on disk or in the archive. Report the relative path and identify the offending component.

- **Framework startup.** For a discovery pattern, check the relative path beneath its fixed directory prefix. With `classpath:/skills/**/*.yaml`, report `Orders/lookup.yaml` and identify `Orders` as the uppercase directory. A candidate such as `orders/lookup.YAML` must produce a filename error even when the configured discovery pattern ends in `*.yaml`. For a literal resource location, the relative path is the filename. Thus `classpath:/skills/Orders/lookup.yaml` passes the case check because its relative path is `lookup.yaml`.
- **Cloud pack import.** Validate the top-level directory as the destination namespace. During pre-publication validation, check skill definition entries relative to that directory. For the ZIP entry `order-support/loomspan/skills/Orders/lookup.yaml`, report `loomspan/skills/Orders/lookup.yaml` and flag `Orders` before handing the YAML to Framework. The same checks apply when the pack arrives by HTTP upload.
- **Framework publication from supplied YAML.** `SkillReloader.validate(...)` and `prepare(...)` receive the YAML content directly. Validate the supplied namespace and declared skill name. Preserve `SkillDocument.sourceName` verbatim as a diagnostic label, including labels such as `Orders import #42` or `Orders/lookup.yaml`. A loader that read the document from a file or archive checks the real path before constructing the document.

| Accepted | Rejected |
| --- | --- |
| `order-support:find-order` | `Order-Support:find-order`, `order-support:Find-Order` |
| `loomspan/skills/orders/find-order.yaml` | `loomspan/skills/Orders/find-order.yaml`, `loomspan/skills/orders/find-order.YAML` |
| `order-support/SKILL.md` | `Order-Support/SKILL.md`, `order-support/skill.md` |

Use the exact literal filename `SKILL.md` for the Agent Skill manifest. Its enclosing skill directory must exactly match the lowercase frontmatter name. OpenAI's skill upload API accepts case variants of the manifest filename. Loomspan requires the specification's canonical spelling across all three products. Other companion materials such as `README.md` keep their authored spelling under the pack's archive rules. [Agent Skills directory structure](https://agentskills.io/specification#directory-structure) · [OpenAI skill upload validation](https://developers.openai.com/api/docs/guides/tools-skills#limits-and-validation)

## Resolution and dispatch

Framework resolves a bare name against the containing skill's owner. In `order-support`, the child reference `find-order` resolves to `order-support:find-order`. An explicit `warehouse:find-order` selects the other namespace and goes through the usual visibility and authorization checks. Lookup is exact. A missing local target produces an error in its owner, regardless of what other applications have published. Apply the same resolution to allowed children, executor references, bindings, output forwarding, and evidence expressions.

Within each namespace, callable skill names must be unique across YAML and Java callable declarations. Reference-only Java executor names must be unique among executor registrations. A callable skill and an executor may share the same pair. For example, a child reference to `orders:lookup` selects the callable, and an `executor` field containing `orders:lookup` selects the executor. Each lookup uses only its corresponding index.

In evidence expressions, bare `and` and `or` are operators. Use a qualified reference for a skill with either local name. Include a parser test in namespace `orders` verifying that `orders:and or lookup` is a disjunction with exactly two skill references, `orders:and` and `orders:lookup`.

Framework renders the validated identity pair with a colon for human-readable qualified references. The model-tool adapter owns the provider spelling. When building a request, it joins the same pair with two hyphens and binds that exact function name to the callback for the resolved skill. On a returned tool call, dispatch looks up the name exactly as received among the callbacks offered for that invocation. An unoffered name fails dispatch. The callback already identifies the skill, so dispatch needs no reverse translation. Model requests, responses, and raw traces preserve the provider spelling. A separate trace field identifying the resolved Loomspan skill uses the colon spelling.

An end-to-end provider function-call test should exercise both spellings in one invocation. Given an allowed child reference `order-support:find-order`, verify that the model receives a function named `order-support--find-order` and that a returned call with that exact name executes the `find-order` skill in `order-support`.

In the plan-worker protocol, the worker prompt shows the assigned task's qualified Loomspan reference. The worker returns that same spelling in its structured `CALL_TOOL` action, for example `"toolName": "order-support:find-order"`. Cover this path with a test that verifies the prompt, accepts the exact assigned reference, and executes the assigned skill. The double-hyphen spelling applies to provider function definitions and their returned function calls.

At an invocation API boundary the caller supplies both components explicitly. This applies to embedded Framework callers, Sidecar HTTP and SDK callers, and Cloud invocation clients. Sidecar and the Cloud engine pass that exact pair to Framework after the applicable access checks. Catalog results expose both components for direct reuse by callers. Catalogs and Console use the colon spelling wherever they display a qualified reference.

A Framework catalog snapshot selects one namespace. Its `skill(...)` lookup accepts a local name. For example, `snapshot("orders").skill("lookup")` looks up `orders:lookup` in that snapshot. A qualified reference supplied to this lookup fails local-name validation.

A Framework `ValidatedSkill` returned by successful validation and a `SkillDescriptor` returned by catalog lookup expose the same identity accessors. For `orders:lookup`, both return `"orders"` from `namespace()`, `"lookup"` from `localName()`, and `"orders:lookup"` from `qualifiedName()`.

## Agent skill packs and plugins

Cloud's V1 publication layout exposes one companion Agent Skill per application. Use the application namespace verbatim for the top-level directory and frontmatter name. Framework declarations under `loomspan/skills/` retain their local names. For promotion, validate the destination namespace and each destination skill pair, since a longer namespace can exceed the combined function limit. The client-only export carries the same companion identity. [Cloud publication format](../../../../loomspan-cloud/ai/thoughts/future/publication-package-format.md)

If distributed through a plugin, the host can qualify the packaged skill as `plugin-name:skill-name`. The frontmatter still contains the exported component alone. Validate the actual plugin name and exported skill name together for OpenAI submission, which caps the combined identity at 64 characters. A Cloud companion's exported skill name is its application namespace, so this check can involve a different pair from the runtime function-length check. [OpenAI plugin validation](https://developers.openai.com/plugins/deploy/submission-errors) · [Claude Code plugin skills](https://code.claude.com/docs/en/skills#choose-where-skills-load)

## Adoption during development

Adopt this contract directly across Framework, Sidecar, and Cloud. Breaking changes to public APIs, SDKs, configuration, and stored formats are permitted. Update Console, examples, fixtures, tests, and documentation to use the same contract.

During implementation, inventory every place that produces, accepts, stores, or resolves a skill or executor reference. Apply the representation specified for that boundary, including startup and reload validation, root invocation and admission handoff, scoped catalog lookup, authored dependency fields and expressions, plans, tool dispatch, and Console lookups. Each input selects exactly one identity under its boundary's grammar. Reject malformed input at that boundary. A failed lookup ends there. Remove legacy overloads and lookup paths that accept alternate spellings, infer a root invocation namespace, fold case, or retry in another namespace.

When updating existing documents, fixtures, and stored references, interpret each reference under the old grammar and rewrite it for its destination field. For example, in an existing skill owned by `orders`, the authored child reference `warehouse-lookup` selects local name `lookup` in namespace `warehouse`. Rewrite it as `warehouse:lookup` to preserve that target. After adoption, the authored name `warehouse-lookup` always resolves to `orders:warehouse-lookup` in that owner. Its meaning is independent of which skills are registered. Perform this conversion on fields that carry identities and preserve authored content and raw provider evidence elsewhere.

Cover that ambiguity with a regression test. With both targets present, assert that the bare and qualified references select their respective targets. With the local target absent, assert that the bare reference fails even though the foreign target exists. Exercise each public lookup and invocation boundary with its documented input shape and verify that alternate spellings cannot select the same target through a fallback path.

### Documentation acceptance criteria for implementation tickets

When splitting this proposal into tickets, include an explicit documentation audit across Framework, Console, Sidecar, and Cloud. Each ticket that changes a naming boundary must identify and update the documentation that teaches or illustrates it. Cover READMEs, packaged Agent Skill instructions and references, authoring and migration guides, Java API and SDK documentation, HTTP examples, Console help, and publication and deployment guides.

Before closing the rollout, record which documentation sets were reviewed and verify examples against the implemented behavior. Check prose, signatures, code snippets, YAML, paths, and sample requests and responses. Review single-hyphen names in context: an old qualified reference such as `warehouse-lookup` can look valid under the new local-name grammar. Examples that teach migration must clearly identify the old spelling and show the replacement.
