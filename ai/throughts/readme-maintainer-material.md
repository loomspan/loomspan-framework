# Material parked from the README

Status: pending human review; not maintained application guidance.

The user requested `ai/throughts` for this audit. The repository also has an
existing `ai/thoughts` directory; this file uses the explicitly requested path.
These extracts preserve material for a later keep/move/delete decision. They are
not revalidated release instructions and should not be executed on that assumption.

## Repository structure

The Loomspan Framework repository contains two projects:

- `src`: Java sources and tests for the core starter; built by the root `pom.xml`.
- `loomspan-console`: an independent Go module with an embedded React application. Its explicit build uses pinned Go, Node.js, and npm toolchains and is not part of the Maven build.

Ordinary Java development and `mvn test` do not invoke Console tooling. See
[`loomspan-console/README.md`](../../loomspan-console/README.md) for the Console build
and local hot-reload workflow.

## Release engineering

Repository maintainers coordinate Maven, fixture, documentation, and Agent
Skill versions with one command:

```bash
python scripts/loomspan_version.py check
python scripts/loomspan_version.py set <release-version>
# Review, test, and commit the release version.
python scripts/loomspan_version.py tag <release-version>
```

`set` requires a clean worktree and replaces the exact current version in all
tracked text files, including the reactor POMs and version-coupled skill
metadata. It also refreshes fixture byte lengths and evaluation fixture
checksums. Historical evaluation records are preserved. It does not commit.
`tag` requires a clean, committed, non-SNAPSHOT
version and creates the annotated `v<version>` tag used by the installer; it does
not push. After releasing, use `set` again to begin the next development
snapshot. Git tags are the release-version catalog, so there is no separate
installer version list to update.

Keep the project on the next anticipated beta version with `-SNAPSHOT` during
normal development. Remove that suffix when preparing the beta release. The
transition is:

```text
development: <release-version>-SNAPSHOT
release: <release-version>
tag: v<release-version>
then begin the next development snapshot
```

Starting from a clean development snapshot worktree, prepare the release.
If the version is already the release version, skip the `set` command. Pushing
the tag below starts the Console release and automatic Maven Central publication:

```bash
python scripts/loomspan_version.py check
python scripts/loomspan_version.py set <release-version>

# Review and run the appropriate tests before committing.
git add .
git commit -m "Release <release-version>"
git push origin main

# Before tagging, run Console Release and Maven Central Release manually
# against main in GitHub Actions. Both manual runs only validate.
# Require both runs and Console CI to pass on this exact commit.

python scripts/loomspan_version.py tag <release-version>
git push origin main v<release-version>
```

The Console preflight must pass native packaging and archive smoke checks on
Windows x86_64, Linux x86_64, and macOS ARM64, including the aggregate checksum
check. If any check fails, fix it and repeat the preflight on the new commit
before creating the tag. After pushing the tag, verify that Maven Central
publication succeeds and the GitHub Release contains all three Console archives
and `SHA256SUMS` before announcing the release.

After releasing, start the next development version from a clean worktree.
This updates the working branch; the release tag continues to identify its release:

```bash
python scripts/loomspan_version.py set <next-version>-SNAPSHOT
git add .
git commit -m "Begin next development cycle"
git push origin main
```

Continue incrementing the beta number for later betas. Release candidates use
`1.0.0-rc.1`, and the first production release uses `1.0.0`.

Substitute the actual release and next-development versions in these commands.
Bootstrap resolves a release from its exact `v<version>` tag. It resolves a
SNAPSHOT from `main` only when the root POM and version-coupled skill metadata
on `main` declare that exact SNAPSHOT version.

### Maven Central releases

The `release` Maven profile builds sources and Javadoc JARs, signs artifacts,
and configures Sonatype's Central Publishing plugin. The published coordinate is
`ai.loomspan:loomspan-spring-boot-starter` (JAR and attached documentation).

To review the release build locally without signing, uploading, or changing
the current development version, run these commands (`mvnw.cmd` on Windows):

```bash
python scripts/loomspan_version.py check
python -m unittest discover scripts/tests -v
./mvnw --batch-mode --no-transfer-progress clean verify
./mvnw --batch-mode --no-transfer-progress -Prelease -DskipTests -Dgpg.skip=true verify
```

Inspect the starter's binary, `-sources.jar`, and `-javadoc.jar` in
`target`. These checks do not exercise signing,
Central credentials, or Sonatype's server-side validation. A manual run of
the **Maven Central Release** GitHub Actions workflow performs the same
build-only checks, with no publishing secrets.

After review, the existing version script prepares and tags the committed
release. A pushed `v<version>` tag must match the root POM and must not contain
`SNAPSHOT`. The workflow checks coordinated versions, runs all Java tests
(including the public API architecture test), and verifies release packaging
before its upload job uses these GitHub Actions secrets:

| Secret | Purpose |
| --- | --- |
| `CENTRAL_TOKEN_USERNAME` | Central Portal user token username |
| `CENTRAL_TOKEN_PASSWORD` | Central Portal user token password |
| `GPG_PRIVATE_KEY` | ASCII-armored signing private key |
| `GPG_PASSPHRASE` | Signing key passphrase |

The verified Sonatype namespace must cover `ai.loomspan`. Before the first
upload, ensure the corresponding GPG public key is available on a keyserver
supported by [Sonatype's GPG requirements](https://central.sonatype.org/publish/requirements/gpg/).
The workflow imports the private key on its runner and supplies the passphrase
through an environment variable.

The upload uses `autoPublish=true` and waits up to 30 minutes for Sonatype to
report `PUBLISHED`. Publication happens automatically after validation; no
manual Publish action is needed in the [Central Portal](https://central.sonatype.com/publishing/deployments).
Maven Central search indexing may take additional time. Published versions
cannot be overwritten; fixes require a new version.

The Console release also runs on the same tag and can publish independently
of Maven. Check both workflows before announcing a release. If an upload job
fails, inspect the Portal before rerunning that job: a deployment may already
exist even if GitHub did not receive its final status. Manual workflow runs
only verify; use the original tag run when retrying an upload.

## Shutdown implementation and Sidecar packaging assertion

The one overall budget starts when root admission closes and covers admitted execution, trace finalization, caller-thread public-view mapping and success or available-failure observation, cutoff, and framework mission-executor cleanup. Loomspan's prompt close-event gate and framework close-event gate are independent and return promptly; bounded waiting occurs in the following Spring lifecycle-stop stage so framework resources required by admitted work remain alive until completion or cutoff. At cutoff, uncooperative framework work is fenced from late writes and interrupted. Other application listeners remain independent: Loomspan defines no shared listener-priority convention and does not bound unrelated application hooks or halt the JVM.

The Loomspan Sidecar has a separate host rule: it stops dispatch immediately and discards queued work, with no drain timer. Its listener and resource ordering must be proven in Sidecar packaging tests and is not part of the framework listener contract.

## Older knowledge-set audience assumption

The skill-authoring index previously said its intended future consumer was a
SkillBuilder application, and that an LLM could meanwhile use the documents while
working in this repository. That framing was replaced with application-developer
and installed-assistant guidance. A future SkillBuilder remains a separate product
idea, not a prerequisite for these references.

## Contributor instruction formerly in the application API reference

When changing Loomspan production types, run this architecture test. New
application-facing API must be a deliberate project decision: place it in
`ai.loomspan.api`, add it to the closed allowlist, document it in the
root README and this knowledge set, and add supported-surface tests.

