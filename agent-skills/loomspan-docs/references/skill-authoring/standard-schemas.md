---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: declaration-and-local-bindings
---

# Standard schema declarations

## Applicability and current integration boundary

Use this topic for authored `input_schema` and `output_schema` declarations. The
shared compiler also validates internally generated planning contracts under the
PLANNING role. These declarations use the supported subset of JSON Schema Draft
2020-12 described here; arbitrary Draft 2020-12 schemas are not accepted.

The declaration compiler, JSON-bound skill decoder and generated Java contracts
have focused executable coverage. Child-input and output-binding projection,
assembly/composition and bounded binding compatibility checks also use canonical
trees and shared local validation. The current source checkout has incomplete consumer integration:
full input dispatch, output correction, planning and TypeSafe/evidence metadata consumers
do not all accept the canonical representation yet. The complete application
build and runtime paths are unavailable while those consumers are inconsistent.
A successful foundation validation does not establish an executable skill tree,
provider transport fidelity, or provider enforcement. Examples below demonstrate
declaration syntax, not a runnable end-to-end application.

The compiler validates supported declarations and instances locally without
coercion, default insertion or mutation. Provider schema copies omit recognized
Loomspan annotations at schema nodes and preserve standard assertions. Sending
or enforcing that copy at a provider is a separate integration concern.

## Required shape and vocabulary

A complete contract MUST be an object schema with exact `type: object`. Root
`{}`, booleans, nullable roots, scalar roots and array roots are rejected. An
optional root `$schema` MUST be exactly
`https://json-schema.org/draft/2020-12/schema`; no other dialect is inferred.

| Keyword or shape | Accepted declaration behavior |
| --- | --- |
| `type` | `object`, `array`, `string`, `integer`, `number`, `boolean`; a nested nullable node uses a two-element array with one of those types and `null`, in either order |
| `properties` | Object mapping exact property names to schemas; names are never trimmed, folded, or interpreted as schema keywords |
| `required` | Array of exact property names whose presence is required; a name need not be declared in `properties` |
| `additionalProperties` | Boolean at object nodes; omission leaves the object open. INPUT additionally permits a schema for every undeclared value |
| `items` | One object-valued item schema for homogeneous arrays, including nested arrays |
| `enum` | Array of JSON scalar values: strings, booleans, numbers and null; object and array enum values are outside the profile |
| `description` | String annotation retained as supplied; does not validate business meaning |
| `format` | One of the asserted formats below |
| `minimum`, `maximum` | Inclusive numeric bounds |
| `minLength`, `maxLength` | Nonnegative integer string-length bounds |
| `minItems`, `maxItems` | Nonnegative integer array-length bounds |
| Nested `{}` or description-only object | Unconstrained JSON value in INPUT only, including typed input map values; descriptions remain unchanged |

Types and keyword values are case-sensitive. A plain `null` type and general
unions are unsupported. Authored boolean schemas are unsupported. Internally
GENERATED declarations additionally permit `false` property schemas for ownership
protection; this does not permit authored `false` or arbitrary boolean locations.

Unknown keywords and extensions are rejected. Unsupported examples include
`nullable`, schema-node `evidence`, `default`, `pattern`, references, definitions,
anchors, composition, conditionals, tuple schemas and unlisted assertions.
`pattern`, `$ref` or `evidence` can still be ordinary property names under
`properties`; those names are data, not keywords.

The compiler checks the supported profile, validates keyword value shapes using
the bundled standard meta-schema, and initializes the validator before retaining
the immutable declaration snapshot. It does not retrieve remote schema resources.

## Presence, null and openness

Presence and nullability are independent. To permit JSON null, quote the YAML
string `"null"` in `type`; an unquoted YAML null is a null value, not a type name.

| Parent requires property | Child type permits null | Accepted state |
| --- | --- | --- |
| Yes | No | Present and non-null |
| Yes | Yes | Present, including explicit null |
| No | No | Omitted, or present and non-null |
| No | Yes | Omitted, or present with or without null |

```yaml
output_schema:
  type: object
  properties:
    selection:
      type: [object, "null"]
      description: Selected option; null when unavailable.
      additionalProperties: true
    rows:
      type: array
      items:
        type: array
        items: {type: integer, minimum: 0}
  required: [selection]
  additionalProperties: false
```

`required` neither supplies a value nor makes it non-null. An object with no
required children can accept `{}`. Omitted `additionalProperties` is open at every
object depth; authors SHOULD close objects explicitly when only declared fields
are meaningful. Setting it to `true` accepts unknown values without constraining
their shape. INPUT can instead type those values:

```yaml
input_schema:
  type: object
  properties:
    Any value: {}
    price: {type: number, minimum: 0.1000000000000000000000000001}
  additionalProperties: {type: string}
```

Exact names such as `Name` and `name`, leading/trailing whitespace, dots, slashes
and tildes remain distinct data keys. Standard mathematical semantics apply:
`1.0` is an integer-valued number, and numeric enum equality compares values
rather than numeric spelling. Schema/data decimal values and large integers in
skill YAML are preserved before tree conversion; the schema JSON codec also
retains exact decimals. This does not change ordinary application JSON codecs.
Decimal literals outside the JSON tree's numeric representation range fail with
their source path and original literal rather than being rounded into another value.

## Asserted formats

The local validator asserts the following NetworkNT 3.0.9 standard formats:

`date`, `date-time`, `duration`, `email`, `hostname`, `idn-email`, `idn-hostname`,
`ipv4`, `ipv6`, `iri`, `iri-reference`, `json-pointer`, `regex`,
`relative-json-pointer`, `time`, `uri`, `uri-reference`, `uri-template`, `uuid`.

Unknown and nonstandard names are rejected, including `alpha`, `alphanumeric`,
`color`, `ip-address`, `phone`, `style`, and `utc-millisec`. The library validators
and default JDK regular-expression implementation define these formats; no
application-specific format implementation is installed. A format is a local
assertion, not proof that a provider follows it or that a value is reachable,
deliverable, factually correct, or safe for an application operation.

## Recognized Loomspan annotations

| Annotation | Placement and shape |
| --- | --- |
| `x-loomspan-attachment` | INPUT string leaf, including a nullable string; exact object fields `media_type` and `allowed_content_types` |
| `x-loomspan-evidence` | One nonblank string on an immediate root OUTPUT property only |
| `x-loomspan-runtime-ref-capable` | Boolean on GENERATED INPUT string leaves only; authored declarations are rejected |

An attachment's `media_type` MUST be one of `image`, `pdf`, `audio`, `video`,
`file`. `allowed_content_types` MUST be a nonempty array of nonblank, syntactically
valid MIME strings parsed by Spring's media MIME parser;
unknown annotation fields are rejected. The annotation can occur on nested
properties, string array items, properties under array-object items, and typed
input map values. Surrounding standard constraints remain effective.

```yaml
input_schema:
  type: object
  properties:
    document:
      type: [string, "null"]
      x-loomspan-attachment:
        media_type: pdf
        allowed_content_types: [application/pdf]
  additionalProperties: false
```

Malformed MIME entries fail loading with their schema location and authored value.
MIME syntax does not establish provider/model support. Evidence annotation
validation preserves the exact expression text and property name; it does not
parse expression grammar or resolve child references. Read
[evidence contracts](evidence-contracts.md) for expression semantics and the
current integration limitation.

Provider copies remove only these three recognized annotations at actual schema
nodes: root, `properties` values, `items`, and schema-valued
`additionalProperties`. Similarly named ordinary properties are preserved.
Canonical schemas retain the annotations and every original accepted value.

## Attachment materialization and recorded descriptors

At declared attachment leaves, inputs MAY be strict `ref://` strings or repeatable
Spring `Resource` values. Reference strings satisfy the standard string assertions
before resolution. Those string assertions do not constrain a direct Resource's
filename or bytes. Ordinary siblings, requiredness, object openness and array bounds
remain validated. Missing optional values produce no media; explicit null produces
no media only when the leaf permits null. Required nullable fields must be present.

Materialization walks named properties first, then schema-valued
`additionalProperties` for unnamed keys, and array `items`. It preserves input keys,
array positions, omission and null. Boolean openness and unmarked fields do not
declare attachments. Canonical inputs and provider schema copies are unchanged;
an internal descriptor view and private descriptor schema validate materialized
attachments without serializing Resources.

Filename extensions supply MIME detection where recognized; otherwise one declared
allowed MIME supplies the existing fallback. Missing filenames remain missing.
Allowed-type comparisons use parsed MIME equality, including case-insensitive
type/subtype, without changing authored parameter values. Image signatures are
checked. Already-open stream Resources reject. One bounded read computes actual
size and SHA-256 and enforces `loomspan.session.attachments.max-size` (20 MiB by
default); it does not pre-read `contentLength()`. Unreadable and oversized resources
fail with field location before planning/model work. Resource access boundaries
remain in force; no automatic upload, conversion or provider fallback is implied.

Descriptors carry `attachment`, `fieldPath`, available `name`, `contentType`,
`mediaType`, `source`, actual `sizeBytes`, `sha256` and metadata. A direct Resource
uses source `resource` without an invented reference/name. `fieldPath` is JSON
Pointer: literal `a.b` is `/a.b`, nested `a` then `b` is `/a/b`, array item zero is
`/files/0`, and `/` and `~` in keys escape as `~1` and `~0`. Descriptors contain no
Resource handles or file contents. Planning receives descriptors; execution receives
the associated media Resources.

Successful nonempty collections are recorded once on the owning mission frame as
`FRAME_METADATA_RECORDED.data.attachments` before planning or no-model completion.
They remain available if the mission later fails. Failed materialization does not
emit a successful collection. Finished journal and observer details carry the same
collection, subject only to the existing journal field-name redaction rules;
canonical traces preserve descriptor content. The live activity projection does not
carry this collection.

`AttachmentMaterializationContractTest` verifies the actual local validator,
materializer, immutable containers and descriptor paths with mocked session/catalog
collaborators. `ExecutionJournalProjectorTest` and `SkillExecutionViewMapperTest`
verify list/stream projection and mapped observer details. Complete coordinator,
engine and public observer invocation remain unavailable while the broader source
graph is inconsistent; their integration tests are retained. This local evidence
does not establish provider file decoding or Console attachment rendering.

## Skill document ingestion and diagnostics

All skill YAML outside the actual top-level executor `config` subtree MUST have
string object keys and JSON-compatible values. Numeric/boolean/null keys,
non-finite numbers (`.nan`, `.inf`, `-.inf`) and unsupported YAML scalar/container
kinds fail before Jackson conversion. Quoted keys and quoted non-finite-looking
strings remain exact strings. Literal TypeSafe state, instructions and criteria
follow this same JSON-bound rule; it is not restricted to schema fields.

Executor `config` remains separately decoded domain data with its existing Java
numeric kinds. Ordinary configuration YAML uses its existing construction policy.
Both construction paths retain duplicate-key rejection, timestamp-as-string
handling, alias/cycle checks and the expanded-value resource limit. See
[executor configuration](executor-skills.md).

Declaration failures identify the schema location, using JSON Pointer escapes
`~0` for `~` and `~1` for `/`; catalog diagnostics retain the declaration source.
Instance validation returns full internal diagnostics with schema/instance paths.
Each call has a private evaluation context with format assertions enabled and no
error-count or evaluation limits. Operational validation failures propagate;
they do not become a successful verdict or an ordinary invalid candidate.
Unlimited evaluation provides no CPU or memory guarantee.

## Executable anchors

- `StandardSchemaCompiler` and `CanonicalSchema` under
  `src/main/java/ai/loomspan/internal/schema/` own profile/meta-schema checks,
  retained immutable schemas, provider copies and standard instance evaluation.
  `StandardSchemaCompilerTest` and `CanonicalSchemaTest` exercise those boundaries.
- `SkillDocumentValues` and `YamlValues` under
  `src/main/java/ai/loomspan/internal/serialization/` own lossless skill ingestion
  and the separate configuration construction policy; `SkillDocumentValuesTest`
  and `YamlValuesTest` cover literals, keys and parser controls.
- `YamlSkillCatalog`, `YamlSkillManifest` and `YamlSkillDefinition` own registration
  wiring and retained definition snapshots. Foundation tests do not establish
  complete catalog, dispatch, correction, evidence or provider integration.
- `ChildInputBindingProjection`, `ChildInputBindingDeclarations`,
  `ChildInputBindingAssembler`, `OutputBindingProjection` and
  `OutputBindingComposition` use canonical ownership projections and shared instance
  validation. Their focused tests cover generated false reservations, exact names,
  root/nested openness, source snapshots, exact transfers and bounds/formats.
  `BindingSchemaCompatibilityTest` separately covers exact numeric enum overlap;
  they do not certify complete publication, engine synthesis or actual provider requests.

## Ordinary input admission

Explicit authored and generated inputs receive recursive Java-value checks before
JSON conversion. String-keyed maps, lists, JSON scalars and finite standard numbers
are accepted; arbitrary Java objects, non-string keys, non-finite numbers and
unmarked Resource/InputStream/byte[] values reject before binding. These checks
also cover undeclared properties when `additionalProperties` is true, omitted or
`{}`. An open schema grants JSON shape freedom, not arbitrary Java-value access.
Validation preserves exact values, property names, omission and permitted null.
Numeric and boolean strings are not coerced; date strings must satisfy the
standard format rather than being rewritten.

Generated Resource, InputStream and byte[] leaves retain their internal runtime
marker. Direct handles at those leaves pass a type gate without reading content;
actual JSON strings and nulls still satisfy the canonical leaf assertions. Every
array item and map value is checked independently. Standard surrounding required,
closed-object, sibling and array-bound assertions remain effective. Binding owns
conversion failures and stream cleanup. Attachment Resource/ref admission is a
separate first pass; the attachment materializer then performs the checks above.
Neither pass certifies provider transport.

Under explicit contracts, only marked or attachment leaves select reference
resolution. Unmarked strings, unconstrained values and open extras preserve literal
`ref://` text, including nested text. Genuine generic contracts retain separate
recursive resolution behavior. Resolution does not bypass resource authorization.

The local ordinary tool argument reader accepts exactly one JSON object, including
`{}` where the contract allows it. Root null, strings, arrays, numbers, booleans,
empty input and trailing JSON/garbage reject; trailing whitespace is permitted.
Nested decimals and large integers retain their values before local validation.
Native tool strictness is not enabled: models can still emit invalid arguments,
and local pre-dispatch validation protects execution correctness.

`SkillInputValidatorTest`, `InputIngressBindingTest`, `InputReferenceTraversalTest`
and `OrdinaryToolArgumentsTest` cover these local boundaries with actual validator,
reader, schema generation and registered Java binding sources. They do not prove
complete public invocation, the actual callback/manager graph or provider request
fidelity while the surrounding application build is unavailable.
