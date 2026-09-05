package webassets

const (
	ManifestName        = "loomspan-assets.json"
	PlaceholderName     = "embed-placeholder.txt"
	CurrentSchema       = 1
	DefaultEntry        = "index.html"
	DefaultViteManifest = ".vite/manifest.json"
)

type Manifest struct {
	SchemaVersion  int         `json:"schemaVersion"`
	ProductVersion string      `json:"productVersion"`
	EntryDocument  string      `json:"entryDocument"`
	ViteManifest   ViteFile    `json:"viteManifest"`
	Assets         []AssetFile `json:"assets"`
}

type ViteFile struct {
	Path   string `json:"path"`
	SHA256 string `json:"sha256"`
}

type AssetFile struct {
	Path   string `json:"path"`
	Length int64  `json:"length"`
	SHA256 string `json:"sha256"`
}
