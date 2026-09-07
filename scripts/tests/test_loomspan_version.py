from __future__ import annotations

import importlib.util
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).parents[1] / "loomspan_version.py"
SPEC = importlib.util.spec_from_file_location("loomspan_version", SCRIPT)
versioning = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = versioning
SPEC.loader.exec_module(versioning)


def run_git(root: Path, *arguments: str) -> str:
    return subprocess.run(
        ["git", "-C", str(root), *arguments],
        check=True,
        stdout=subprocess.PIPE,
        text=True,
    ).stdout.strip()


def write_fixture(root: Path, version: str) -> None:
    (root / "loomspan-spring-boot-starter").mkdir(parents=True)
    (root / "loomspan-console" / "agent-skills" / "loomspan").mkdir(parents=True)
    (root / "agent-skills" / "loomspan-docs").mkdir(parents=True)
    (root / "pom.xml").write_text(
        f"<project><version>{version}</version></project>\n", encoding="utf-8"
    )
    for relative in versioning.MODULE_POMS:
        (root / relative).write_text(
            f"<project><parent><version>{version}</version></parent></project>\n",
            encoding="utf-8",
        )
    for relative in versioning.VERSIONED_SKILLS:
        (root / relative).write_text(
            f'---\nname: test\nmetadata:\n  loomspan-version: "{version}"\n---\n',
            encoding="utf-8",
        )
    (root / "fixture.txt").write_text(f"runtime={version}\n", encoding="utf-8")
    run_git(root, "init", "-q")
    run_git(root, "config", "user.email", "version-test@example.invalid")
    run_git(root, "config", "user.name", "Version Test")
    run_git(root, "add", ".")
    run_git(root, "commit", "-qm", "fixture")


class VersionCommandTest(unittest.TestCase):
    def test_version_change_preserves_historical_evaluation_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as name:
            root = Path(name).resolve()
            write_fixture(root, "8.7.6-SNAPSHOT")
            archived = root / "loomspan-console/agent-evals/results/2000-01-01/skill-package/SKILL.md"
            archived.parent.mkdir(parents=True)
            original = b'loomspan-version: "8.7.6-SNAPSHOT"\n'
            archived.write_bytes(original)
            run_git(root, "add", ".")
            run_git(root, "commit", "-qm", "historical evidence")
            changed = versioning.set_version(root, "8.7.6-beta.1")
            self.assertNotIn(archived, changed)
            self.assertEqual(original, archived.read_bytes())
            self.assertEqual("8.7.6-beta.1", versioning.check_consistency(root))

    def test_beta_and_next_snapshot_refresh_derived_fixture_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as name:
            root = Path(name).resolve()
            write_fixture(root, "8.7.6-SNAPSHOT")
            body = root / "loomspan-console-fixtures/traces/example.ndjson"
            body.parent.mkdir(parents=True)
            body.write_bytes(b'{"version":"8.7.6-SNAPSHOT"}\n')
            artifact = root / "loomspan-console-fixtures/application-artifact/download-response.json"
            artifact.parent.mkdir()
            artifact.write_text(json.dumps({
                "headers": {"Content-Length": body.stat().st_size},
                "bodyFixture": "../traces/example.ndjson",
            }), encoding="utf-8")
            evaluations = root / "loomspan-console/agent-evals"
            (evaluations / "cases").mkdir(parents=True)
            metadata = json.dumps({"fixtures": [{
                "path": body.relative_to(root).as_posix(),
                "sha256": hashlib.sha256(body.read_bytes()).hexdigest(),
            }]})
            manifests = [evaluations / "fixtures.json", evaluations / "cases/example.json"]
            for path in manifests:
                path.write_text(metadata, encoding="utf-8")
            run_git(root, "add", ".")
            run_git(root, "commit", "-qm", "fixture metadata")

            for version in ("8.7.6-beta.1", "8.7.6-beta.2-SNAPSHOT"):
                changed = versioning.set_version(root, version)
                self.assertEqual(version, json.loads(body.read_bytes())["version"])
                self.assertEqual(body.stat().st_size,
                                 json.loads(artifact.read_bytes())["headers"]["Content-Length"])
                for path in manifests:
                    self.assertIn(path, changed)
                    self.assertEqual(hashlib.sha256(body.read_bytes()).hexdigest(),
                                     json.loads(path.read_bytes())["fixtures"][0]["sha256"])
                run_git(root, "add", ".")
                run_git(root, "commit", "-qm", version)

    def test_set_updates_every_tracked_current_version(self) -> None:
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            write_fixture(root, "8.7.6-SNAPSHOT")

            changed = versioning.set_version(root, "8.7.6")

            self.assertIn(root / "fixture.txt", changed)
            self.assertEqual("8.7.6", versioning.check_consistency(root))
            self.assertNotIn(
                "8.7.6-SNAPSHOT",
                run_git(root, "grep", "-h", "-F", "8.7.6", "--", "."),
            )

    def test_set_refuses_a_dirty_worktree(self) -> None:
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            write_fixture(root, "8.7.6-SNAPSHOT")
            (root / "fixture.txt").write_text("dirty\n", encoding="utf-8")

            with self.assertRaisesRegex(versioning.VersionCommandError, "clean worktree"):
                versioning.set_version(root, "8.7.6")

    def test_check_rejects_skill_metadata_drift(self) -> None:
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            write_fixture(root, "8.7.6-SNAPSHOT")
            skill = root / versioning.VERSIONED_SKILLS[0]
            skill.write_text(
                skill.read_text(encoding="utf-8").replace(
                    "8.7.6-SNAPSHOT", "8.8.0-SNAPSHOT"
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(versioning.VersionCommandError, "skill version"):
                versioning.check_consistency(root)

    def test_tag_requires_committed_release_and_creates_annotated_tag(self) -> None:
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            write_fixture(root, "8.7.6")

            tag = versioning.create_tag(root, "8.7.6")

            self.assertEqual("v8.7.6", tag)
            self.assertEqual("tag", run_git(root, "cat-file", "-t", tag))

    def test_tag_rejects_snapshot(self) -> None:
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            write_fixture(root, "8.7.7-SNAPSHOT")

            with self.assertRaisesRegex(versioning.VersionCommandError, "SNAPSHOT"):
                versioning.create_tag(root, "8.7.7-SNAPSHOT")


if __name__ == "__main__":
    unittest.main()
