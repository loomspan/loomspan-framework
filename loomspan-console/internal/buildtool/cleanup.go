package main

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/webassets"
)

func cleanGeneratedAssets(paths projectPaths) error {
	assetRoot := filepath.Join(paths.module, "internal", "webassets")
	resolvedRoot, err := filepath.EvalSymlinks(assetRoot)
	if err != nil {
		return fmt.Errorf("resolve asset root: %w", err)
	}
	resolvedGenerated, err := filepath.EvalSymlinks(paths.generated)
	if err != nil && !os.IsNotExist(err) {
		return fmt.Errorf("resolve generated directory: %w", err)
	}
	if os.IsNotExist(err) {
		resolvedGenerated = paths.generated
	}
	relative, err := filepath.Rel(resolvedRoot, resolvedGenerated)
	if err != nil || relative != "generated" || filepath.IsAbs(relative) {
		return fmt.Errorf("refusing to clean generated assets outside %s", resolvedRoot)
	}
	info, err := os.Lstat(paths.generated)
	if err == nil && info.Mode()&os.ModeSymlink != 0 {
		return fmt.Errorf("refusing to clean symlinked generated directory")
	}
	if err != nil && !os.IsNotExist(err) {
		return err
	}
	if err := os.RemoveAll(paths.generated); err != nil {
		return err
	}
	if err := os.MkdirAll(paths.generated, 0o755); err != nil {
		return err
	}
	placeholder := filepath.Join(paths.generated, webassets.PlaceholderName)
	return os.WriteFile(placeholder, []byte("This tracked file keeps the embed package compilable before a production build.\n"), 0o644)
}
