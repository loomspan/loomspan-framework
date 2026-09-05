package config

import (
	"os"
	"strings"
	"testing"
)

func TestREADMEConfigurationExampleParses(t *testing.T) {
	content, err := os.ReadFile("../../README.md")
	if err != nil {
		t.Fatal(err)
	}
	start := strings.Index(string(content), "```yaml\n")
	if start < 0 {
		t.Fatal("README YAML example missing")
	}
	example := string(content)[start+len("```yaml\n"):]
	end := strings.Index(example, "```")
	if end < 0 {
		t.Fatal("README YAML fence is not closed")
	}
	if _, _, err := Decode("README.md", strings.NewReader(example[:end])); err != nil {
		t.Fatal(err)
	}
}

func TestREADMEDeclaresEveryRuntimeFlag(t *testing.T) {
	content, err := os.ReadFile("../../README.md")
	if err != nil {
		t.Fatal(err)
	}
	for _, flag := range []string{"--version", "--config", "--work-dir", "--listen", "--development-origin", "--no-open-browser", "--prompt-for-application-key"} {
		if !strings.Contains(string(content), flag) {
			t.Errorf("README does not declare %s", flag)
		}
	}
}
