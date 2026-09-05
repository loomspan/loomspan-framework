package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestGenerateManifestSortsAndHashesDeterministically(t *testing.T) {
	first := createAssetDirectory(t, []string{"assets/app-12345678.css", "index.html", "assets/app-12345678.js", ".vite/manifest.json"})
	second := createAssetDirectory(t, []string{".vite/manifest.json", "assets/app-12345678.js", "index.html", "assets/app-12345678.css"})
	if err := generateManifest(first, "0.1.0-SNAPSHOT"); err != nil {
		t.Fatal(err)
	}
	if err := generateManifest(second, "0.1.0-SNAPSHOT"); err != nil {
		t.Fatal(err)
	}
	one, _ := os.ReadFile(filepath.Join(first, "loomspan-assets.json"))
	two, _ := os.ReadFile(filepath.Join(second, "loomspan-assets.json"))
	if string(one) != string(two) {
		t.Fatalf("manifest generation is not deterministic\n%s\n%s", one, two)
	}
}

func createAssetDirectory(t *testing.T, order []string) string {
	t.Helper()
	directory := t.TempDir()
	content := map[string]string{
		"index.html":              `<script src="/assets/app-12345678.js"></script>`,
		".vite/manifest.json":     `{"index.html":{"file":"assets/app-12345678.js","css":["assets/app-12345678.css"],"isEntry":true}}`,
		"assets/app-12345678.js":  `console.log("loomspan")`,
		"assets/app-12345678.css": `body{color:black}`,
	}
	for _, name := range order {
		filename := filepath.Join(directory, filepath.FromSlash(name))
		if err := os.MkdirAll(filepath.Dir(filename), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(filename, []byte(content[name]), 0o600); err != nil {
			t.Fatal(err)
		}
	}
	return directory
}
