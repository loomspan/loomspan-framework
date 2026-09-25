"""Source/fixture integrity only. Markdown decisions require the separate agent replay."""
import json
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
FIXTURES = Path(__file__).parent / "fixtures/skill-install"
SKILLS = {
    "agent-skills/loomspan": "framework",
    "agent-skills/loomspan-docs": "framework",
    "agent-skills/loomspan-install": "framework",
    "loomspan-console/agent-skills/loomspan-console": "console",
}


def marker(path, name):
    frontmatter = path.read_text(encoding="utf-8").split("---", 2)[1]
    match = re.search(r"^\s*" + re.escape(name) + r':\s*["\x27]?([^"\x27\n]+?)["\x27]?\s*$', frontmatter, re.M)
    if match is None:
        raise AssertionError(f"missing {name} in {path}")
    return match[1]


class SkillInstallContractTest(unittest.TestCase):
    def test_current_family_metadata_and_replaced_paths(self):
        pom = ET.parse(ROOT / "pom.xml").getroot()
        version = pom.findtext("{http://maven.apache.org/POM/4.0.0}version")
        for relative, component in SKILLS.items():
            path = ROOT / relative / "SKILL.md"
            with self.subTest(skill=relative):
                self.assertEqual(path.parent.name, marker(path, "name"))
                self.assertEqual(component, marker(path, "loomspan-component"))
                self.assertEqual(version, marker(path, "loomspan-version"))
        self.assertFalse((ROOT / "agent-skills/bootstrap/SKILL.md").exists())
        self.assertFalse((ROOT / "loomspan-console/agent-skills/loomspan/SKILL.md").exists())

    def test_fixture_components_are_independent_and_complete(self):
        source = FIXTURES / "sources"
        sidecar = ET.parse(source / "sidecar/v2.3.0/pom.xml").getroot()
        self.assertEqual("2.3.0", sidecar.findtext("version"))
        self.assertEqual("${loomspan.version}", sidecar.findtext("dependencies/dependency/version"))
        self.assertEqual("4.5.0", sidecar.findtext("properties/loomspan.version"))
        for relative, component in SKILLS.items():
            if relative.endswith("loomspan-install"):
                continue
            path = source / "framework/v4.5.0" / relative / "SKILL.md"
            self.assertEqual(component, marker(path, "loomspan-component"))
            self.assertEqual("4.5.0", marker(path, "loomspan-version"))
        sidecar_skill = source / "sidecar/v2.3.0/agent-skills/loomspan-sidecar-authoring/SKILL.md"
        self.assertEqual("sidecar", marker(sidecar_skill, "loomspan-component"))
        self.assertEqual("2.3.0", marker(sidecar_skill, "loomspan-version"))
        self.assertEqual("4.5.0", marker(sidecar_skill, "loomspan-framework-version"))
        sdk = source / "sdk-python/v7.8.0"
        package = json.loads((sdk / "package.json").read_text())
        self.assertEqual("7.8.0", package["version"])
        self.assertEqual("sdk-python", marker(sdk / package["skill"] / "SKILL.md", "loomspan-component"))
        self.assertEqual("7.8.0", marker(sdk / package["skill"] / "SKILL.md", "loomspan-version"))
        self.assertIn("7.8.0 | 2.3.0 | supported", (sdk / package["skill"] / "references/compatibility.md").read_text())

    def test_replay_inputs_cover_rubric_without_embedding_answers(self):
        cases = json.loads((FIXTURES / "scenarios.json").read_text())
        expected = json.loads((FIXTURES / "expected.json").read_text())
        self.assertEqual(set(expected), {re.match(r"R\d+", c["id"])[0] for c in cases})
        self.assertEqual(len(cases), len({c["id"] for c in cases}))
        for case in cases:
            self.assertTrue((FIXTURES / "projects" / case["project"]).is_dir())
            self.assertNotIn("expected", case)
            self.assertTrue(case["request"])
        self.assertFalse((FIXTURES / "projects/sidecar/pom.xml").exists())

    def test_negative_sources_have_intended_defects(self):
        source = FIXTURES / "sources/framework"
        self.assertFalse((source / "v4.4.0/agent-skills/loomspan/SKILL.md").exists())
        self.assertFalse((source / "v4.4.0/loomspan-console/agent-skills/loomspan-console/SKILL.md").exists())
        self.assertEqual("sidecar", marker(source / "wrong-marker/agent-skills/loomspan-docs/SKILL.md", "loomspan-component"))
        self.assertEqual("4.6.0-SNAPSHOT", ET.parse(source / "main/pom.xml").getroot().findtext("version"))
        chained = ET.parse(FIXTURES / "projects/java-chain/pom.xml").getroot()
        self.assertEqual("${platform.version}", chained.findtext("properties/loomspan.version"))
        ambiguous = ET.parse(FIXTURES / "projects/java-ambiguous/pom.xml").getroot()
        self.assertEqual(2, len(ambiguous.findall("dependencies/dependency")))


if __name__ == "__main__":
    unittest.main()
