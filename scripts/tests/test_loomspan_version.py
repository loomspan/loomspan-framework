from __future__ import annotations

import importlib.util
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
    (root / "loomspan-sample").mkdir()
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
