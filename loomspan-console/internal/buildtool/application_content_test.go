package main

import (
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestProductionPresentersDoNotInjectApplicationHTML(t *testing.T) {
	paths, err := resolveProjectPaths()
	if err != nil {
		t.Fatal(err)
	}
	source := filepath.Join(paths.web, "src")
	err = filepath.WalkDir(source, func(path string, entry fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if entry.IsDir() || strings.Contains(entry.Name(), ".test.") ||
			(!strings.HasSuffix(entry.Name(), ".ts") && !strings.HasSuffix(entry.Name(), ".tsx")) {
			return nil
		}
		contents, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		if strings.Contains(string(contents), "dangerouslySetInnerHTML") {
			relative, _ := filepath.Rel(source, path)
			t.Errorf("production presenter %s uses unreviewed raw HTML injection", relative)
		}
		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
}
