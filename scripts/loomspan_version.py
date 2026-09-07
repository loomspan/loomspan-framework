#!/usr/bin/env python3
"""Keep Loomspan's project, fixtures, documentation, and skills on one version."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import xml.etree.ElementTree as ET


MODULE_POMS = (
    Path("loomspan-spring-boot-starter/pom.xml"),
)
VERSIONED_SKILLS = (
    Path("loomspan-console/agent-skills/loomspan/SKILL.md"),
    Path("agent-skills/loomspan-docs/SKILL.md"),
)
SKILL_VERSION = re.compile(
    r'^  loomspan-version:\s*(?:"([^"]+)"|\'([^\']+)\'|([^#\s]+))\s*$'
)


class VersionCommandError(Exception):
    """A safe, user-facing version command failure."""


def _local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _direct_child(element: ET.Element, name: str) -> ET.Element | None:
    return next((child for child in element if _local_name(child.tag) == name), None)


def _required_text(element: ET.Element | None, description: str) -> str:
    value = (element.text or "").strip() if element is not None else ""
    if not value:
        raise VersionCommandError(f"Missing {description}")
    return value


def validate_version(version: str) -> None:
    if (
        not version
        or version.strip() != version
        or any(character.isspace() or ord(character) < 32 for character in version)
        or version == "development"
        or "${" in version
        or "{{" in version
    ):
        raise VersionCommandError(f"Invalid Loomspan version {version!r}")


def read_root_version(root: Path) -> str:
    pom = root / "pom.xml"
    try:
        project = ET.parse(pom).getroot()
    except (ET.ParseError, OSError) as exc:
        raise VersionCommandError(f"Cannot read root POM: {exc}") from exc
    if _local_name(project.tag) != "project":
        raise VersionCommandError("Root POM document element must be project")
    version = _required_text(_direct_child(project, "version"), "root project version")
    validate_version(version)
    return version


def read_parent_version(pom: Path) -> str:
    try:
        project = ET.parse(pom).getroot()
    except (ET.ParseError, OSError) as exc:
        raise VersionCommandError(f"Cannot read module POM {pom}: {exc}") from exc
    parent = _direct_child(project, "parent")
    return _required_text(
        _direct_child(parent, "version") if parent is not None else None,
        f"parent version in {pom}",
    )


def read_skill_version(skill_file: Path) -> str:
    try:
        lines = skill_file.read_text(encoding="utf-8").splitlines()
    except (OSError, UnicodeError) as exc:
        raise VersionCommandError(f"Cannot read skill metadata {skill_file}: {exc}") from exc
    in_metadata = False
    for line in lines:
        if not line.startswith((" ", "\t")):
            in_metadata = line.strip() == "metadata:"
            continue
        if in_metadata:
            match = SKILL_VERSION.fullmatch(line)
            if match:
                return next(value for value in match.groups() if value is not None)
    raise VersionCommandError(f"Missing loomspan-version metadata in {skill_file}")


def check_consistency(root: Path, expected: str | None = None) -> str:
    version = read_root_version(root)
    if expected is not None and version != expected:
        raise VersionCommandError(
            f"Root POM version {version!r} does not equal expected version {expected!r}"
        )
    for relative in MODULE_POMS:
        actual = read_parent_version(root / relative)
        if actual != version:
            raise VersionCommandError(
                f"{relative} parent version {actual!r} does not equal {version!r}"
            )
    for relative in VERSIONED_SKILLS:
        actual = read_skill_version(root / relative)
        if actual != version:
            raise VersionCommandError(
                f"{relative} skill version {actual!r} does not equal {version!r}"
            )
    return version


def _git(root: Path, arguments: list[str], check: bool = True) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        ["git", "-C", str(root), *arguments],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if check and result.returncode != 0:
        raise VersionCommandError(result.stderr.strip() or "Git command failed")
    return result


def require_clean_worktree(root: Path) -> None:
    status = _git(root, ["status", "--porcelain", "--untracked-files=normal"]).stdout
    if status.strip():
        raise VersionCommandError(
            "Version commands require a clean worktree; commit or stash existing changes first"
        )


def tracked_text_files_containing(root: Path, value: str) -> tuple[Path, ...]:
    result = _git(root, ["grep", "-Il", "-F", "-e", value, "--", ".",
                         ":(exclude)loomspan-console/agent-evals/results/**"], check=False)
    if result.returncode not in (0, 1):
        raise VersionCommandError(result.stderr.strip() or "Cannot inspect tracked version files")
    return tuple(root / line for line in result.stdout.splitlines() if line)


def refresh_fixture_metadata(root: Path, replacements: dict[Path, bytes]) -> None:
    """Refresh byte-derived metadata against the proposed versioned fixtures."""
    def content(path: Path) -> bytes:
        return replacements[path] if path in replacements else path.read_bytes()

    artifact = root / "loomspan-console-fixtures/application-artifact/download-response.json"
    if artifact.is_file():
        raw = content(artifact)
        metadata = json.loads(raw)
        body = (artifact.parent / metadata["bodyFixture"]).resolve()
        if not body.is_relative_to(root):
            raise VersionCommandError("Artifact fixture body must stay within the repository")
        length = len(content(body))
        updated = re.sub(rb'("Content-Length"\s*:\s*)\d+',
                         lambda match: match[1] + str(length).encode(), raw)
        if updated != raw:
            replacements[artifact] = updated

    evaluations = root / "loomspan-console/agent-evals"
    manifests = [evaluations / "fixtures.json", *sorted((evaluations / "cases").glob("*.json"))]
    for manifest in manifests:
        if not manifest.is_file():
            continue
        raw = content(manifest)
        updated = raw
        for fixture in json.loads(raw)["fixtures"]:
            path = (root / fixture["path"]).resolve()
            if not path.is_relative_to(root):
                raise VersionCommandError("Evaluation fixture must stay within the repository")
            digest = hashlib.sha256(content(path)).hexdigest()
            updated = updated.replace(fixture["sha256"].encode(), digest.encode())
        if updated != raw:
            replacements[manifest] = updated


def set_version(root: Path, new_version: str) -> tuple[Path, ...]:
    root = root.resolve()
    validate_version(new_version)
    require_clean_worktree(root)
    old_version = check_consistency(root)
    if new_version == old_version:
        raise VersionCommandError(f"Project already uses version {new_version}")

    files = tracked_text_files_containing(root, old_version)
    required = {root / "pom.xml", *(root / path for path in MODULE_POMS), *(root / path for path in VERSIONED_SKILLS)}
    if not required.issubset(files):
        missing = sorted(str(path.relative_to(root)) for path in required.difference(files))
        raise VersionCommandError(
            "Version-bearing files do not contain the current version: " + ", ".join(missing)
        )

    old_bytes = old_version.encode("utf-8")
    new_bytes = new_version.encode("utf-8")
    replacements: dict[Path, bytes] = {}
    for path in files:
        if path.is_symlink() or not path.is_file():
            raise VersionCommandError(f"Version-bearing path is not a regular file: {path}")
        content = path.read_bytes()
        if old_bytes not in content:
            raise VersionCommandError(f"Tracked version disappeared from {path}")
        replacements[path] = content.replace(old_bytes, new_bytes)

    refresh_fixture_metadata(root, replacements)

    for path, content in replacements.items():
        mode = path.stat().st_mode
        temporary = path.with_name(path.name + ".loomspan-version-tmp")
        try:
            temporary.write_bytes(content)
            os.chmod(temporary, stat.S_IMODE(mode))
            os.replace(temporary, path)
        finally:
            if temporary.exists():
                temporary.unlink()

    check_consistency(root, new_version)
    return tuple(replacements)


def create_tag(root: Path, version: str) -> str:
    root = root.resolve()
    validate_version(version)
    if version.endswith("-SNAPSHOT"):
        raise VersionCommandError("Cannot tag a SNAPSHOT version")
    require_clean_worktree(root)
    check_consistency(root, version)
    tag = f"v{version}"
    _git(root, ["check-ref-format", f"refs/tags/{tag}"])
    if _git(root, ["rev-parse", "-q", "--verify", f"refs/tags/{tag}"], check=False).returncode == 0:
        raise VersionCommandError(f"Tag {tag} already exists")
    _git(root, ["tag", "-a", tag, "-m", f"loomspan {version}"])
    return tag


def repository_root(script: Path) -> Path:
    return script.resolve().parents[1]


def parse_args(arguments: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Manage the coordinated Loomspan version")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("check", help="verify POM and skill versions agree")
    set_parser = commands.add_parser("set", help="replace the coordinated project version")
    set_parser.add_argument("version")
    tag_parser = commands.add_parser("tag", help="create an annotated tag for a committed release")
    tag_parser.add_argument("version")
    return parser.parse_args(arguments)


def main(arguments: list[str] | None = None) -> int:
    args = parse_args(arguments)
    root = repository_root(Path(__file__))
    try:
        if args.command == "check":
            version = check_consistency(root)
            print(f"Loomspan version is consistent: {version}")
        elif args.command == "set":
            files = set_version(root, args.version)
            print(f"Updated Loomspan version to {args.version} in {len(files)} tracked files.")
            print("Review and commit the changes before creating a release tag.")
        else:
            tag = create_tag(root, args.version)
            print(f"Created annotated tag {tag}. Push the commit and tag when ready.")
        return 0
    except VersionCommandError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
