package webassets

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"testing"
	"testing/fstest"
)

func TestVerifyManifestAcceptsCompleteCurrentAssetSet(t *testing.T) {
	files := validAssetFS(t, "0.1.0-SNAPSHOT")
	if _, err := Verify(files, "0.1.0-SNAPSHOT"); err != nil {
		t.Fatalf("Verify() error = %v", err)
	}
}

func TestVerifyManifestRejectsVersionMismatch(t *testing.T) {
	files := validAssetFS(t, "0.1.0-SNAPSHOT")
	if _, err := Verify(files, "0.2.0"); err == nil {
		t.Fatal("Verify() accepted a stale asset version")
	}
}

func TestVerifyManifestRejectsInventoryMismatch(t *testing.T) {
	tests := []struct {
		name   string
		mutate func(fstest.MapFS)
	}{
		{name: "missing entry", mutate: func(files fstest.MapFS) { delete(files, DefaultEntry) }},
		{name: "modified", mutate: func(files fstest.MapFS) { files["assets/app-12345678.js"].Data = []byte("modified") }},
		{name: "truncated", mutate: func(files fstest.MapFS) { files["assets/app-12345678.js"].Data = nil }},
		{name: "extra", mutate: func(files fstest.MapFS) { files["extra.txt"] = &fstest.MapFile{Data: []byte("extra")} }},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			files := validAssetFS(t, "0.1.0-SNAPSHOT")
			test.mutate(files)
			if _, err := Verify(files, "0.1.0-SNAPSHOT"); err == nil {
				t.Fatal("Verify() accepted invalid inventory")
			}
		})
	}
}

func TestVerifyManifestRejectsMissingManifest(t *testing.T) {
	files := validAssetFS(t, "0.1.0-SNAPSHOT")
	delete(files, ManifestName)
	if _, err := Verify(files, "0.1.0-SNAPSHOT"); err == nil {
		t.Fatal("Verify() accepted a missing manifest")
	}
}

func TestVerifyManifestRejectsInvalidManifest(t *testing.T) {
	tests := []struct {
		name   string
		mutate func(map[string]any)
	}{
		{name: "unknown field", mutate: func(value map[string]any) { value["unknown"] = true }},
		{name: "wrong schema", mutate: func(value map[string]any) { value["schemaVersion"] = 99 }},
		{name: "unresolved version", mutate: func(value map[string]any) { value["productVersion"] = "${project.version}" }},
		{name: "absolute path", mutate: func(value map[string]any) { value["entryDocument"] = "/index.html" }},
		{name: "backslash", mutate: func(value map[string]any) { value["entryDocument"] = `dir\index.html` }},
		{name: "duplicate", mutate: func(value map[string]any) {
			assets := value["assets"].([]any)
			value["assets"] = append(assets, assets[len(assets)-1])
		}},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			files := validAssetFS(t, "0.1.0-SNAPSHOT")
			var value map[string]any
			if err := json.Unmarshal(files[ManifestName].Data, &value); err != nil {
				t.Fatal(err)
			}
			test.mutate(value)
			files[ManifestName].Data, _ = json.Marshal(value)
			if _, err := Verify(files, "0.1.0-SNAPSHOT"); err == nil {
				t.Fatal("Verify() accepted invalid manifest")
			}
		})
	}
}

func TestVerifyManifestRejectsInvalidViteEntryReferences(t *testing.T) {
	tests := []struct {
		name   string
		mutate func(fstest.MapFS)
	}{
		{name: "untracked Vite entry", mutate: func(files fstest.MapFS) {
			files[DefaultViteManifest].Data = []byte(`{"index.html":{"file":"assets/missing-12345678.js","isEntry":true}}`)
		}},
		{name: "missing Vite import", mutate: func(files fstest.MapFS) {
			files[DefaultViteManifest].Data = []byte(`{"index.html":{"file":"assets/app-12345678.js","css":["assets/app-12345678.css"],"imports":["_missing.js"],"isEntry":true}}`)
		}},
		{name: "stale HTML entry", mutate: func(files fstest.MapFS) {
			files[DefaultEntry].Data = []byte(`<script type="module" src="/assets/missing-12345678.js"></script><link rel="stylesheet" href="/assets/app-12345678.css">`)
		}},
		{name: "commented entry with active missing script", mutate: func(files fstest.MapFS) {
			files[DefaultEntry].Data = []byte(`<!-- <script src="/assets/app-12345678.js"></script> --><script src="/missing.js"></script><link rel="stylesheet" href="/assets/app-12345678.css">`)
		}},
		{name: "inline script text with active missing script", mutate: func(files fstest.MapFS) {
			files[DefaultEntry].Data = []byte(`<script>const marker = 'src="/assets/app-12345678.js"';</script><script src="/missing.js"></script><link rel="stylesheet" href="/assets/app-12345678.css">`)
		}},
		{name: "relative entry breaks deep links", mutate: func(files fstest.MapFS) {
			files[DefaultEntry].Data = []byte(`<script type="module" src="assets/app-12345678.js"></script><link rel="stylesheet" href="/assets/app-12345678.css">`)
		}},
		{name: "unhashed entry with hashed decoy", mutate: func(files fstest.MapFS) {
			files["assets/app.js"] = files["assets/app-12345678.js"]
			files[DefaultEntry].Data = []byte(`<script type="module" src="/assets/app.js"></script><link rel="stylesheet" href="/assets/app-12345678.css">`)
			files[DefaultViteManifest].Data = []byte(`{"index.html":{"file":"assets/app.js","css":["assets/app-12345678.css"],"isEntry":true}}`)
		}},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			files := validAssetFS(t, "0.1.0-SNAPSHOT")
			test.mutate(files)
			rewriteManifest(t, files, "0.1.0-SNAPSHOT")
			if _, err := Verify(files, "0.1.0-SNAPSHOT"); err == nil {
				t.Fatal("Verify() accepted an invalid entry dependency graph")
			}
		})
	}
}

func TestVerifyManifestRejectsNonContentAddressedJavaScript(t *testing.T) {
	files := validAssetFS(t, "0.1.0-SNAPSHOT")
	files["assets/app.js"] = files["assets/app-12345678.js"]
	delete(files, "assets/app-12345678.js")
	files[DefaultEntry].Data = []byte(`<script type="module" src="/assets/app.js"></script><link rel="stylesheet" href="/assets/app-12345678.css">`)
	files[DefaultViteManifest].Data = []byte(`{"index.html":{"file":"assets/app.js","css":["assets/app-12345678.css"],"isEntry":true}}`)
	rewriteManifest(t, files, "0.1.0-SNAPSHOT")
	if _, err := Verify(files, "0.1.0-SNAPSHOT"); err == nil {
		t.Fatal("Verify() accepted non-content-addressed JavaScript")
	}
}

func validAssetFS(t *testing.T, version string) fstest.MapFS {
	t.Helper()
	files := fstest.MapFS{
		DefaultEntry: {
			Data: []byte(`<script type="module" src="/assets/app-12345678.js"></script><link rel="stylesheet" href="/assets/app-12345678.css">`),
		},
		DefaultViteManifest: {
			Data: []byte(`{"index.html":{"file":"assets/app-12345678.js","css":["assets/app-12345678.css"],"isEntry":true}}`),
		},
		"assets/app-12345678.js":  {Data: []byte(`console.log("loomspan")`)},
		"assets/app-12345678.css": {Data: []byte(`body{color:black}`)},
	}
	rewriteManifest(t, files, version)
	return files
}

func rewriteManifest(t *testing.T, files fstest.MapFS, version string) {
	t.Helper()
	inventory, err := Inventory(files)
	if err != nil {
		t.Fatal(err)
	}
	viteHash := sha256.Sum256(files[DefaultViteManifest].Data)
	manifest := Manifest{
		SchemaVersion:  CurrentSchema,
		ProductVersion: version,
		EntryDocument:  DefaultEntry,
		ViteManifest: ViteFile{
			Path:   DefaultViteManifest,
			SHA256: hex.EncodeToString(viteHash[:]),
		},
		Assets: inventory,
	}
	raw, err := json.Marshal(manifest)
	if err != nil {
		t.Fatal(err)
	}
	files[ManifestName] = &fstest.MapFile{Data: raw}
}
