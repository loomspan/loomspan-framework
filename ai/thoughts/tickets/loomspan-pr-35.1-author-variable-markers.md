# PR 35.1 — Preserve authored variable markers and their locations

## Outcome

Give startup and preparation a common internal way to discover and substitute whole YAML nodes before any string-keyed map, typed binder, or skill parser can discard their structure. This ticket owns the shared semantics and reusable conformance fixtures. PRs 35.3 and 35.4 own entry-point integration.

## Requirements and acceptance criteria

- [ ] Start only below each authored `loomspan.connections.<connection>`, `loomspan.executor-config.<namespace>`, and the top-level `config` map of an executor-backed skill. Walk all keys, values and sequence elements, including unknown executor fields and fields named `prompt` inside those boundaries. Section selectors, other skill fields (including prompts, schemas, roles and transforms), models, session policy and invocation inputs trigger zero lookups.
- [ ] A mapping with exactly the case-sensitive key `$var` references one nonblank YAML string without surrounding whitespace. Block and flow forms agree. Quoted `"123"` works. A number, null, blank, or padded name fails at its authored location. There is no escape, default or expression syntax. A multi-key mapping containing `$var` is an ordinary mapping and nested markers still count.
- [ ] Discovery works without values. Substitution returns one nonblank string per marker, preserving punctuation, whitespace within a nonblank value, newlines and literal placeholder text. Preserve all other scalar types and containers. There is no inline interpolation, YAML reparse or recursive expansion. Keep existing YAML resource limits, including alias expansion limits.
- [ ] Report filename and authored path for each occurrence. Use dot notation for identifier keys, JSON-quoted brackets for other string keys (e.g. `headers["X.Region"]`), zero-based `[i]` for sequences and `{key#i}` for a mapping-key marker at zero-based authored entry i. Use `{value#i}` for its paired value while its key is unresolved. Diagnostics identify both original locations when keys resolve to the same string, before overwrite. Construct resolution errors/logs from locations and reasons without supplied values.
- [ ] Supply reusable YAML and expected names/paths for independent Go discovery and Java preparation. A worked execution file `loomspan/execution.yaml` uses `provider.key` at `loomspan.connections.primary.api-key`, `app.region` at both the connection's X-Region header and the sales HTTP records target's X-Region header, and `records.url` at the target's base-url. The target also has literal Accept = application/json. In `loomspan/skills/read-record.yaml`, `config.path` references `records.lookup-path`. A nested sequence use of `app.region` adds an occurrence without adding a fifth name. Include a separate header with marker key `header.name` and marker value `app.region`, plus a collision case with a literal sibling.
- [ ] Keep the facility internal. Run `LoomspanPublicSurfaceArchitectureTest` after production type changes.

## Pipeline notes

All Loomspan products are in development with no QA or production deployments. Destructively replace contracts superseded by this ticket. Breaking other Loomspan projects that still use those contracts is acceptable until their own updates land. Do not add compatibility shims or retain legacy behavior to keep those projects working.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The YAML grammar and location convention define a serialized contract shared with other products.
- **Reassessment triggers:** Reassess if the current checkout already implements the production contract and only localized fixture or documentation work remains.
