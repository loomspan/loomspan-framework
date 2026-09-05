package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestReadProductVersionReadsOnlyDirectProjectVersion(t *testing.T) {
	xml := `<?xml version="1.0"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <!-- preserve the complete version -->
  <parent><version>99.0.0</version></parent>
  <version>0.1.0-SNAPSHOT</version>
  <properties><dependency.version>2.0.0</dependency.version></properties>
  <dependencies><dependency><version>3.0.0</version></dependency></dependencies>
</project>`
	filename := writePOM(t, xml)
	got, err := readProductVersion(filename)
	if err != nil {
		t.Fatal(err)
	}
	if got != "0.1.0-SNAPSHOT" {
		t.Fatalf("version = %q", got)
	}
}

func TestReadProductVersionRejectsInvalidDocuments(t *testing.T) {
	tests := map[string]string{
		"missing":    `<project></project>`,
		"duplicate":  `<project><version>1.0.0</version><version>2.0.0</version></project>`,
		"unresolved": `<project><version>${revision}</version></project>`,
		"blank":      `<project><version> </version></project>`,
		"malformed":  `<project><version>1.0.0</project>`,
		"wrong root": `<not-project><version>1.0.0</version></not-project>`,
	}
	for name, document := range tests {
		t.Run(name, func(t *testing.T) {
			if _, err := readProductVersion(writePOM(t, document)); err == nil {
				t.Fatal("readProductVersion() accepted invalid POM")
			}
		})
	}
}

func writePOM(t *testing.T, document string) string {
	t.Helper()
	filename := filepath.Join(t.TempDir(), "pom.xml")
	if err := os.WriteFile(filename, []byte(document), 0o600); err != nil {
		t.Fatal(err)
	}
	return filename
}
