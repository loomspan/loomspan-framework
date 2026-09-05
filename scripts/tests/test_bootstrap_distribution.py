from pathlib import Path
import unittest


REPOSITORY = Path(__file__).resolve().parents[2]
BOOTSTRAP = REPOSITORY / "agent-skills" / "bootstrap"


class BootstrapDistributionTest(unittest.TestCase):
    def test_bootstrap_is_instruction_only(self) -> None:
        files = {
            path.relative_to(BOOTSTRAP).as_posix()
            for path in BOOTSTRAP.rglob("*")
            if path.is_file()
        }

        self.assertEqual({"SKILL.md"}, files)

    def test_bootstrap_has_no_agent_specific_or_executable_dependency(self) -> None:
        contents = (BOOTSTRAP / "SKILL.md").read_text(encoding="utf-8")

        for forbidden in (
            "bootstrap.py",
            "$CODEX_HOME",
            "$skill-installer",
            "~/.codex",
        ):
            self.assertNotIn(forbidden, contents)


if __name__ == "__main__":
    unittest.main()
