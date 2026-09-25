from pathlib import Path
import unittest


REPOSITORY = Path(__file__).resolve().parents[2]
INSTALLER = REPOSITORY / "agent-skills" / "loomspan-install"


class InstallDistributionTest(unittest.TestCase):
    def test_install_is_instruction_only(self) -> None:
        files = {
            path.relative_to(INSTALLER).as_posix()
            for path in INSTALLER.rglob("*")
            if path.is_file()
        }

        self.assertEqual({"SKILL.md"}, files)

    def test_install_has_no_agent_specific_or_executable_dependency(self) -> None:
        contents = (INSTALLER / "SKILL.md").read_text(encoding="utf-8")

        for forbidden in (
            "install.py",
            "$CODEX_HOME",
            "$skill-installer",
            "~/.codex",
        ):
            self.assertNotIn(forbidden, contents)


if __name__ == "__main__":
    unittest.main()
