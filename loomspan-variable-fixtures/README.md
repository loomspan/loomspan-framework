# YAML variable conformance fixtures

These files exercise whole-node discovery and substitution through the internal authored YAML facility. Java consumes every case with `VariableMarkerFixtureTest`. Independent implementations can consume the same YAML and JSON files. Startup and published skill preparation consume the same markers across all three selected sections. `ExecutorGenerationExecutionIntegrationTest#workedVariableCorpusReachesProviderAndActualExecutor` prepares and invokes the worked case. `StartupVariableResolutionIntegrationTest` exercises effective Spring source selection and shared resolution through provider and configured executor consumers.

Enumerate every immediate case directory and read its `expected.json`. Read `documents` in array order. Each document has a relative `source` filename and `kind` equal to `execution` or `skill`. The source string is also the exact filename carried by occurrences and failures.

Successful discovery compares `occurrences` in order, each with `name`, `source`, and `path`. The `names` array contains distinct names in first-occurrence order across documents. Discovery needs no supplied values. For substitution, `values` maps exact names to strings. When `resolved` is present, its array contains the complete resulting documents in document order. Cases with complex keys omit JSON output because JSON object keys are strings.

Negative cases declare `failure` with `phase` equal to `discovery` or `substitution`, a fixed `reason`, and an ordered `locations` array of source/path pairs. Discovery failures omit success expectations. Substitution failures still require successful discovery expectations. The corpus uses `name-must-be-string`, `blank-name`, `padded-name`, and `key-collision`. The facility also reports `missing-value`, `blank-value`, and `lookup-failed`, covered by Java unit tests. Failed substitution returns no partial document.

## Selection and markers

For execution YAML, start at each immediate value below `loomspan.connections` and `loomspan.executor-config`. Selector keys are outside traversal. For skill YAML, the actual root must contain `executor` and its actual top-level `config` must be a mapping. Traverse the selected node and every descendant key, value and sequence element. Unknown fields and fields named `prompt` inside selected data participate. Other skill fields, models, session policy, invocation-shaped inputs and other root sections trigger zero lookups.

A mapping with exactly the case-sensitive string key `$var` is a marker. Its value must be a YAML string containing a nonblank name with no surrounding whitespace. The shared whitespace set consists of U+0009–U+000D, U+001C–U+0020, U+00A0, U+1680, U+2000–U+200A, U+2028–U+2029, U+202F, U+205F and U+3000. This is the union of Java `Character.isWhitespace` and `Character.isSpaceChar`. Quoted `"123"` is a name. A numeric scalar is invalid. Names are opaque and case-sensitive, with no escape, expression, or default syntax. Block and flow mappings agree. A multi-key mapping containing `$var` is ordinary data, with nested markers participating normally.

Each replacement is one nonblank string. Preserve its exact whitespace, punctuation, newlines and literal placeholders. Returned strings are terminal leaves. Other scalar kinds and containers retain ordinary YAML construction semantics. The parser rejects recursive aliases and logical expansion above 1,000,000 nodes, counting keys, values and each repeated alias use.

## Locations and ordering

Traverse depth first in authored mapping-entry order, key before paired value, and sequence-index order. Aliases contribute an occurrence at each authored use path.

String keys matching `[A-Za-z_][A-Za-z0-9_-]*` use dot notation with no leading dot. Other string keys use JSON-quoted brackets, such as `headers["X.Region"]`. Sequences use zero-based `[i]`. At authored entry index i, marker keys and complex keys use `{key#i}` and their paired values use `{value#i}`. These indices count literal siblings too. Nested markers in complex keys append their own ordinary key/index segments. Retain authored locations after substitution.

Detect effective-key equality before insertion. A collision reports both original locations in authored order. A literal sibling's location is its ordinary value path. A marker or complex key's location is its `{key#i}` path. Construct resolution diagnostics from those locations and fixed reasons without supplied strings or callback causes.

The `worked` case has four distinct names and six occurrences across `loomspan/execution.yaml` and `loomspan/skills/read-record.yaml`. The separate `header-key` and `collision` cases demonstrate marker keys and unresolved paired-value locations.
