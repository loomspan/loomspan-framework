---
name: loomspan-install
description: Select and install or update exact version-aligned Loomspan guidance through the host's supported skill manager for Sidecar or embedded Java projects, with optional independently versioned SDK guidance.
license: Apache-2.0
metadata:
  loomspan-component: framework
  loomspan-version: "1.0.0-beta.6-SNAPSHOT"
---

# Install version-aligned Loomspan guidance

Read these instructions directly from the official framework source, or invoke
this skill if explicitly installed through the host. This is instruction-only:
use the host's project reading, exact-source inspection and skill management
capabilities. No installer script, package manager, installation ledger or
client-specific directory convention is needed. Do not automatically add
`loomspan-install` to the guidance set below.

## Select components

Establish whether the customer uses Sidecar or embeds the Java framework from
project/deployment information or explicit user choice. A live service and local
Loomspan source checkout are not prerequisites.

- **Sidecar (any application language, including direct API):** determine its
  exact target version from explicit configuration/deployment metadata, such as a
  pinned image tag or documented release. Missing or conflicting environments
  require a focused question. Do not infer a version from `latest` or an opaque
  digest. Inspect the root POM of that exact official Sidecar revision and resolve
  its direct `ai.loomspan:loomspan-spring-boot-starter` dependency. That dependency
  selects framework guidance; the Sidecar version independently selects Sidecar
  guidance. The application needs no POM.
- **Embedded Java:** inspect the application's POM for that direct starter
  dependency. Accept a literal version or a single property whose literal value
  is declared in the same POM. These same bounded rules apply to the Sidecar POM.
  Inheritance, BOMs, profiles, property chains, absent/unversioned or ambiguous
  dependencies require clarification or an explicit resolution limitation. Do
  not run a new Maven resolution engine or guess. If the user supplies the exact
  unresolved version explicitly, report that provenance; never edit the POM.
- **Optional SDK:** use the application's actual resolved SDK dependency version,
  its published package identity and official exact source. Ask if unresolved;
  never derive SDK versions from Sidecar/framework versions or vice versa.

## Resolve and validate the complete set before writes

Official sources are `https://github.com/loomspan/loomspan-framework` and
`https://github.com/loomspan/loomspan-sidecar`; SDK owners publish their own source
location and package/version mapping. For these two repositories release `X`
means tag `vX`; a snapshot means `main` only if its root POM declares that exact
snapshot. Verify the root version at every chosen revision. Resolve each revision
once, pinning the commit when host source access permits, so moving main cannot
mix files. SDKs must likewise provide an exact version source.

| Skill | Exact source folder | Component/version |
| --- | --- | --- |
| `loomspan` | framework `agent-skills/loomspan` | framework / selected framework version |
| `loomspan-docs` | framework `agent-skills/loomspan-docs` | framework / selected framework version |
| `loomspan-console` | framework `loomspan-console/agent-skills/loomspan-console` | console / selected framework version under today's coordinated Console release policy |
| `loomspan-sidecar-authoring` (Sidecar customers) | Sidecar `agent-skills/loomspan-sidecar-authoring` | sidecar / selected Sidecar version |
| `loomspan-sdk-<language>` (SDK customers) | SDK owner's published complete skill folder | sdk-<language> / application's selected SDK version |

Install the router, docs and Console for both paths; add Sidecar authoring for
Sidecar customers and only the relevant SDK guidance when used. For every selected
folder verify the manifest name, `metadata.loomspan-component`, exact owning
`metadata.loomspan-version` and all required bundled resources. Sidecar also
requires `metadata.loomspan-framework-version` equal to its POM dependency and
the selected `loomspan-docs` version. Reject missing, incomplete or mismatched
sources before any host write. An old release lacking the router or renamed
Console is unavailable; never substitute old Console `loomspan` or current main.

Inspect SDK compatibility separately from source-version alignment. Consume only
explicitly published facts, citing source URL/revision, SDK version and supported
Sidecar version(s). An SDK-owned `references/compatibility.md` table of exact
pairs is the recommended initial convention, not a new package format. Equal
version numbers are not compatibility evidence. With no fact, report **unknown**;
otherwise exact documentation may still be installed without a supported-runtime
claim. An explicitly incompatible combination requires developer resolution
before installation; do not silently switch targets. Synthetic fixtures never
establish published compatibility.

## Use the host's supported scope and authorization

Default to **project-local** skill installation. Honor an explicit supported
scope. If the host cannot inspect/install exact complete sources or support the
requested/default scope, report that limitation and stop the affected installation;
do not silently use user/global scope or manually invent a directory layout.

After all source preflight succeeds, inspect existing installations through the
host. Apply its ordinary authorization and the user's existing update consent;
do not require fresh per-skill approvals already covered by that consent. Respect
declined replacements and report skips. In particular, older Console guidance
named `loomspan` conflicts with the new router: identify its responsibility and
use only authorized host replacement/removal operations. Never auto-delete it,
create an alias or report a coherent set while the conflict remains.

Update only the selected skills. Do not change application dependencies, runtime
targets or deployment files; install software dependencies; connect to a runtime
as an installation prerequisite; publish configuration; or execute skills.

## Verify and report actual outcomes

Use the host to verify installed names and component/version markers (including
Sidecar's framework marker) where exposed. Distinguish planned, completed,
skipped, failed and unverifiable outcomes. Report selected component versions,
exact sources/revisions, scope, compatibility provenance/limitations and any host
refresh requirement. A host failure may leave partial results: stop further unsafe
work, report each actual outcome and remaining work, and do not claim atomicity,
invent rollback or delete prior installations as cleanup.
