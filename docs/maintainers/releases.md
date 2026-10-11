# Coordinated framework and Console releases

Use this procedure for repository maintenance. Commands are run from the framework
root unless stated otherwise; use `./mvnw.cmd` on Windows. Read the current
[version script](../../scripts/loomspan_version.py),
[Maven Central Release workflow](../../.github/workflows/publish.yml), and
[Console Release workflow](../../.github/workflows/console-release.yml) before a
release. Those files are the executable authority. Publication is a separate,
explicit maintainer action; a documentation review does not authorize it.

## Prepare and verify

1. Choose the release version and next development version. Normal development
   uses `<release-version>-SNAPSHOT`; a release uses `<release-version>` and its
   annotated tag is `v<release-version>`.
2. Start from a clean worktree and check coordinated versions:

   ```bash
   python scripts/loomspan_version.py check
   python scripts/loomspan_version.py set <release-version>
   ```

   Skip `set` if already on that version. It replaces the exact current version in
   tracked text files, including versioned skill metadata, and refreshes fixture
   byte lengths and evaluation fixture checksums. It does not commit. Review the
   complete diff, including records containing the replaced version.
3. Verify before committing:

   ```bash
   python scripts/loomspan_version.py check
   python -m unittest discover scripts/tests -v
   ./mvnw --batch-mode --no-transfer-progress clean verify
   ./mvnw --batch-mode --no-transfer-progress -Prelease -DskipTests -Dgpg.skip=true verify
   ```

   Inspect the binary, sources and Javadoc JARs under `target/`. These commands do
   not exercise signing, credentials or server-side publication.
4. Commit the reviewed release changes and push the release commit to `main`.
   Manually run **Maven Central Release** and **Console Release** on that commit.
   Manual runs verify and do not publish. Require both runs and **Console CI** to
   pass for the exact commit before tagging. Console preflight includes three
   native packages, smoke checks, macOS DMG verification and aggregate checksums.

## Tag, publish and confirm

After release authorization, create and push the tag from the clean, committed
release worktree:

```bash
python scripts/loomspan_version.py tag <release-version>
git push origin main v<release-version>
```

`tag` rejects snapshots and existing tags, requires coordinated versions and a
clean worktree, creates the annotated tag, and does not push it. A pushed matching
tag starts both publication workflows; they can succeed or fail independently.

The Maven `release` profile attaches sources and Javadoc and signs artifacts.
The tag workflow publishes `ai.loomspan:loomspan-spring-boot-starter` using
`CENTRAL_TOKEN_USERNAME`, `CENTRAL_TOKEN_PASSWORD`, `GPG_PRIVATE_KEY` and
`GPG_PASSPHRASE`. The Sonatype namespace must cover `ai.loomspan`, and the signing
public key must meet the publishing service's current requirements. The current
plugin enables automatic publication and waits for **validation**, with a
30-minute maximum wait. A successful upload job does not establish that publication
or search indexing has completed: confirm the deployment in the Central Portal.

Console's validated release set contains exactly four artifacts plus `SHA256SUMS`:

- `loomspan-console-VERSION-windows-x86_64.zip`
- `loomspan-console-VERSION-linux-x86_64.tar.gz`
- `loomspan-console-VERSION-macos-arm64.tar.gz`
- `loomspan-console-VERSION-macos-arm64.dmg`

The workflow verifies each artifact's checksum before publishing. Optional macOS
signing and notarization configuration belongs to the
[Console packaging instructions](../../loomspan-console/README.md#canonical-production-commands).
Verify the GitHub Release assets and the Maven publication before announcing.

If Maven upload fails, inspect the Central Portal before retrying: a deployment
may already exist. Manual runs only verify; retry an upload through its original
tag run after resolving the failure. Published Maven versions cannot be overwritten.
Review Console's existing release state separately before retrying its publication.

## Begin the next development cycle

From a clean worktree, update, review, commit and push the next snapshot:

```bash
python scripts/loomspan_version.py set <next-version>-SNAPSHOT
```

The release tag continues to identify the released commit. Assistant guidance
resolves releases from exact tags and snapshots from `main` only when the root
version matches; pin the source commit for reproducible snapshot evaluations.
