package main

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/webassets"
)

func generateManifest(directory, productVersion string) error {
	files := os.DirFS(directory)
	inventory, err := webassets.Inventory(files)
	if err != nil {
		return fmt.Errorf("inventory assets: %w", err)
	}
	vite, err := os.ReadFile(filepath.Join(directory, filepath.FromSlash(webassets.DefaultViteManifest)))
	if err != nil {
		return fmt.Errorf("read Vite manifest: %w", err)
	}
	viteHash := sha256.Sum256(vite)
	manifest := webassets.Manifest{
		SchemaVersion:  webassets.CurrentSchema,
		ProductVersion: productVersion,
		EntryDocument:  webassets.DefaultEntry,
		ViteManifest: webassets.ViteFile{
			Path:   webassets.DefaultViteManifest,
			SHA256: hex.EncodeToString(viteHash[:]),
		},
		Assets: inventory,
	}
	raw, err := json.MarshalIndent(manifest, "", "  ")
	if err != nil {
		return err
	}
	raw = append(raw, '\n')
	return os.WriteFile(filepath.Join(directory, webassets.ManifestName), raw, 0o644)
}
