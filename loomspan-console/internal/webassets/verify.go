package webassets

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"html"
	"io"
	"io/fs"
	"path"
	"regexp"
	"sort"
	"strings"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/release"
)

const maxManifestBytes = 4 << 20

var contentAddressed = regexp.MustCompile(`(?:^|/)[^/]+-[A-Za-z0-9_-]{8,}\.[A-Za-z0-9]+$`)

func DecodeManifest(data []byte) (Manifest, error) {
	if len(data) == 0 || len(data) > maxManifestBytes {
		return Manifest{}, fmt.Errorf("asset manifest size %d is invalid", len(data))
	}
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	var manifest Manifest
	if err := decoder.Decode(&manifest); err != nil {
		return Manifest{}, fmt.Errorf("decode asset manifest: %w", err)
	}
	if decoder.Decode(&struct{}{}) != io.EOF {
		return Manifest{}, fmt.Errorf("asset manifest contains trailing data")
	}
	return manifest, nil
}

func Verify(files fs.FS, expectedVersion string) (Manifest, error) {
	if err := release.ValidateProductVersion(expectedVersion); err != nil {
		return Manifest{}, err
	}
	raw, err := fs.ReadFile(files, ManifestName)
	if err != nil {
		return Manifest{}, fmt.Errorf("read %s: %w", ManifestName, err)
	}
	manifest, err := DecodeManifest(raw)
	if err != nil {
		return Manifest{}, err
	}
	if manifest.SchemaVersion != CurrentSchema {
		return Manifest{}, fmt.Errorf("unsupported asset manifest schema %d", manifest.SchemaVersion)
	}
	if manifest.ProductVersion != expectedVersion {
		return Manifest{}, fmt.Errorf("asset version %q does not match executable version %q", manifest.ProductVersion, expectedVersion)
	}
	if manifest.EntryDocument != DefaultEntry || manifest.ViteManifest.Path != DefaultViteManifest {
		return Manifest{}, fmt.Errorf("asset manifest must use entry %q and Vite manifest %q", DefaultEntry, DefaultViteManifest)
	}
	if err := validatePath(manifest.EntryDocument); err != nil {
		return Manifest{}, err
	}
	if err := validatePath(manifest.ViteManifest.Path); err != nil {
		return Manifest{}, err
	}
	if len(manifest.Assets) < 3 {
		return Manifest{}, fmt.Errorf("asset inventory is incomplete")
	}

	actual := make(map[string]AssetFile)
	err = fs.WalkDir(files, ".", func(name string, entry fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if entry.IsDir() || name == ManifestName || name == PlaceholderName {
			return nil
		}
		if err := validatePath(name); err != nil {
			return err
		}
		item, err := hashFile(files, name)
		if err != nil {
			return err
		}
		actual[name] = item
		return nil
	})
	if err != nil {
		return Manifest{}, fmt.Errorf("inventory embedded assets: %w", err)
	}

	seen := make(map[string]struct{}, len(manifest.Assets))
	previous := ""
	for _, declared := range manifest.Assets {
		if err := validatePath(declared.Path); err != nil {
			return Manifest{}, err
		}
		if declared.Path == PlaceholderName || declared.Path == ManifestName {
			return Manifest{}, fmt.Errorf("non-production path %q is in asset inventory", declared.Path)
		}
		if previous != "" && declared.Path <= previous {
			return Manifest{}, fmt.Errorf("asset inventory paths must be sorted and unique")
		}
		previous = declared.Path
		if _, exists := seen[declared.Path]; exists {
			return Manifest{}, fmt.Errorf("duplicate asset path %q", declared.Path)
		}
		seen[declared.Path] = struct{}{}
		if declared.Length < 0 || !validHash(declared.SHA256) {
			return Manifest{}, fmt.Errorf("asset metadata for %q is invalid", declared.Path)
		}
		found, exists := actual[declared.Path]
		if !exists || found.Length != declared.Length || found.SHA256 != declared.SHA256 {
			return Manifest{}, fmt.Errorf("asset %q does not match its inventory", declared.Path)
		}
	}
	if len(actual) != len(seen) {
		return Manifest{}, fmt.Errorf("asset inventory does not exactly match embedded files")
	}
	if _, ok := seen[DefaultEntry]; !ok {
		return Manifest{}, fmt.Errorf("entry document is missing from inventory")
	}
	vite, ok := actual[DefaultViteManifest]
	if !ok || vite.SHA256 != manifest.ViteManifest.SHA256 {
		return Manifest{}, fmt.Errorf("Vite manifest is missing or has the wrong hash")
	}
	if err := verifyReferences(files, seen); err != nil {
		return Manifest{}, err
	}
	return manifest, nil
}

func Inventory(directory fs.FS) ([]AssetFile, error) {
	var inventory []AssetFile
	err := fs.WalkDir(directory, ".", func(name string, entry fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if entry.IsDir() || name == ManifestName || name == PlaceholderName {
			return nil
		}
		if err := validatePath(name); err != nil {
			return err
		}
		item, err := hashFile(directory, name)
		if err != nil {
			return err
		}
		inventory = append(inventory, item)
		return nil
	})
	sort.Slice(inventory, func(i, j int) bool { return inventory[i].Path < inventory[j].Path })
	return inventory, err
}

func hashFile(files fs.FS, name string) (AssetFile, error) {
	file, err := files.Open(name)
	if err != nil {
		return AssetFile{}, err
	}
	defer file.Close()
	hash := sha256.New()
	length, err := io.Copy(hash, file)
	if err != nil {
		return AssetFile{}, err
	}
	return AssetFile{Path: name, Length: length, SHA256: hex.EncodeToString(hash.Sum(nil))}, nil
}

func validatePath(name string) error {
	if name == "" || name == "." || strings.Contains(name, "\\") || strings.HasPrefix(name, "/") ||
		path.Clean(name) != name || strings.HasPrefix(name, "../") || strings.Contains(name, "/../") {
		return fmt.Errorf("unsafe asset path %q", name)
	}
	return nil
}

func validHash(value string) bool {
	if len(value) != sha256.Size*2 {
		return false
	}
	_, err := hex.DecodeString(value)
	return err == nil && value == strings.ToLower(value)
}

func verifyReferences(files fs.FS, inventory map[string]struct{}) error {
	html, err := fs.ReadFile(files, DefaultEntry)
	if err != nil {
		return fmt.Errorf("read entry document: %w", err)
	}
	type viteItem struct {
		File           string   `json:"file"`
		CSS            []string `json:"css"`
		Imports        []string `json:"imports"`
		DynamicImports []string `json:"dynamicImports"`
		IsEntry        bool     `json:"isEntry"`
	}
	var vite map[string]viteItem
	raw, err := fs.ReadFile(files, DefaultViteManifest)
	if err != nil {
		return fmt.Errorf("read Vite manifest: %w", err)
	}
	decoder := json.NewDecoder(bytes.NewReader(raw))
	if err := decoder.Decode(&vite); err != nil {
		return fmt.Errorf("decode Vite manifest: %w", err)
	}
	if decoder.Decode(&struct{}{}) != io.EOF {
		return fmt.Errorf("Vite manifest contains trailing data")
	}
	entry, ok := vite[DefaultEntry]
	if !ok || !entry.IsEntry {
		return fmt.Errorf("Vite manifest has no %q entry", DefaultEntry)
	}
	if !strings.HasSuffix(entry.File, ".js") || !contentAddressed.MatchString(entry.File) {
		return fmt.Errorf("Vite entry JavaScript %q is not content-addressed", entry.File)
	}

	for key, item := range vite {
		if item.File == "" {
			return fmt.Errorf("Vite manifest item %q has no output file", key)
		}
		for _, reference := range append([]string{item.File}, item.CSS...) {
			if err := requireInventoriedReference(reference, inventory); err != nil {
				return err
			}
		}
		for _, imported := range append(item.Imports, item.DynamicImports...) {
			if err := validatePath(imported); err != nil {
				return fmt.Errorf("invalid Vite import %q: %w", imported, err)
			}
			if _, ok := vite[imported]; !ok {
				return fmt.Errorf("Vite manifest item %q imports missing item %q", key, imported)
			}
		}
	}

	htmlReferences, err := entryDocumentReferences(html)
	if err != nil {
		return err
	}
	for reference := range htmlReferences {
		if err := requireInventoriedReference(reference, inventory); err != nil {
			return fmt.Errorf("entry document: %w", err)
		}
	}
	for _, required := range append([]string{entry.File}, entry.CSS...) {
		if _, ok := htmlReferences[required]; !ok {
			return fmt.Errorf("entry document does not reference Vite entry asset %q", required)
		}
	}
	return nil
}

func entryDocumentReferences(html []byte) (map[string]struct{}, error) {
	references := make(map[string]struct{})
	for offset := 0; offset < len(html); {
		start := bytes.IndexByte(html[offset:], '<')
		if start < 0 {
			break
		}
		start += offset
		if bytes.HasPrefix(html[start:], []byte("<!--")) {
			end := bytes.Index(html[start+4:], []byte("-->"))
			if end < 0 {
				return nil, fmt.Errorf("parse entry document: unterminated comment")
			}
			offset = start + 4 + end + 3
			continue
		}
		tag, err := parseHTMLStartTag(html, start)
		if err != nil {
			return nil, err
		}
		if tag.end <= start {
			return nil, fmt.Errorf("parse entry document: invalid tag offset")
		}
		offset = tag.end
		if tag.name == "" || tag.closing {
			continue
		}
		attribute := ""
		switch tag.name {
		case "script":
			attribute = "src"
		case "link":
			if !hasLinkAssetRelation(tag.attributes) {
				continue
			}
			attribute = "href"
		case "style", "textarea", "title":
			next, err := skipRawTextElement(html, offset, tag.name)
			if err != nil {
				return nil, err
			}
			offset = next
			continue
		default:
			continue
		}
		value, ok := tag.attributes[attribute]
		if !ok || value == "" {
			if tag.name == "script" {
				next, err := skipRawTextElement(html, offset, tag.name)
				if err != nil {
					return nil, err
				}
				offset = next
				continue
			}
			return nil, fmt.Errorf("entry document %s element is missing %s", tag.name, attribute)
		}
		if !strings.HasPrefix(value, "/assets/") {
			return nil, fmt.Errorf("entry document %s %s %q must be a root-relative embedded asset", tag.name, attribute, value)
		}
		reference := strings.TrimPrefix(value, "/")
		if err := validatePath(reference); err != nil {
			return nil, err
		}
		references[reference] = struct{}{}
		if tag.name == "script" && !tag.selfClosing {
			next, err := skipRawTextElement(html, offset, tag.name)
			if err != nil {
				return nil, err
			}
			offset = next
		}
	}
	return references, nil
}

type htmlStartTag struct {
	name        string
	attributes  map[string]string
	end         int
	closing     bool
	selfClosing bool
}

func parseHTMLStartTag(document []byte, start int) (htmlStartTag, error) {
	if start < 0 || start >= len(document) || document[start] != '<' {
		return htmlStartTag{}, fmt.Errorf("parse entry document: invalid tag start")
	}
	end, err := htmlTagEnd(document, start+1)
	if err != nil {
		return htmlStartTag{}, err
	}
	body := strings.TrimSpace(string(document[start+1 : end-1]))
	if body == "" || strings.HasPrefix(body, "!") || strings.HasPrefix(body, "?") {
		return htmlStartTag{end: end}, nil
	}
	closing := strings.HasPrefix(body, "/")
	if closing {
		body = strings.TrimSpace(strings.TrimPrefix(body, "/"))
	}
	selfClosing := strings.HasSuffix(body, "/")
	if selfClosing {
		body = strings.TrimSpace(strings.TrimSuffix(body, "/"))
	}
	nameEnd := strings.IndexAny(body, " \t\r\n")
	if nameEnd < 0 {
		nameEnd = len(body)
	}
	name := strings.ToLower(body[:nameEnd])
	attributes, err := parseHTMLAttributes(body[nameEnd:])
	if err != nil {
		return htmlStartTag{}, err
	}
	return htmlStartTag{
		name:        name,
		attributes:  attributes,
		end:         end,
		closing:     closing,
		selfClosing: selfClosing,
	}, nil
}

func htmlTagEnd(document []byte, offset int) (int, error) {
	var quote byte
	for current := offset; current < len(document); current++ {
		switch character := document[current]; {
		case quote != 0 && character == quote:
			quote = 0
		case quote == 0 && (character == '"' || character == '\''):
			quote = character
		case quote == 0 && character == '>':
			return current + 1, nil
		}
	}
	return 0, fmt.Errorf("parse entry document: unterminated tag")
}

func parseHTMLAttributes(body string) (map[string]string, error) {
	attributes := make(map[string]string)
	for offset := 0; offset < len(body); {
		for offset < len(body) && isHTMLSpace(body[offset]) {
			offset++
		}
		if offset >= len(body) {
			break
		}
		nameStart := offset
		for offset < len(body) && !isHTMLSpace(body[offset]) && body[offset] != '=' {
			offset++
		}
		name := strings.ToLower(body[nameStart:offset])
		if name == "" {
			return nil, fmt.Errorf("parse entry document: invalid attribute")
		}
		for offset < len(body) && isHTMLSpace(body[offset]) {
			offset++
		}
		value := ""
		if offset < len(body) && body[offset] == '=' {
			offset++
			for offset < len(body) && isHTMLSpace(body[offset]) {
				offset++
			}
			if offset >= len(body) {
				return nil, fmt.Errorf("parse entry document: attribute %q has no value", name)
			}
			if body[offset] == '"' || body[offset] == '\'' {
				quote := body[offset]
				offset++
				valueStart := offset
				for offset < len(body) && body[offset] != quote {
					offset++
				}
				if offset >= len(body) {
					return nil, fmt.Errorf("parse entry document: attribute %q is unterminated", name)
				}
				value = body[valueStart:offset]
				offset++
			} else {
				valueStart := offset
				for offset < len(body) && !isHTMLSpace(body[offset]) {
					offset++
				}
				value = body[valueStart:offset]
			}
		}
		if _, exists := attributes[name]; exists {
			return nil, fmt.Errorf("parse entry document: duplicate attribute %q", name)
		}
		attributes[name] = html.UnescapeString(value)
	}
	return attributes, nil
}

func skipRawTextElement(document []byte, offset int, name string) (int, error) {
	closing := []byte("</" + strings.ToLower(name))
	relative := bytes.Index(bytes.ToLower(document[offset:]), closing)
	if relative < 0 {
		return 0, fmt.Errorf("parse entry document: unterminated %s element", name)
	}
	start := offset + relative
	end, err := htmlTagEnd(document, start+2+len(name))
	if err != nil {
		return 0, err
	}
	return end, nil
}

func isHTMLSpace(character byte) bool {
	return character == ' ' || character == '\t' || character == '\r' || character == '\n' || character == '\f'
}

func hasLinkAssetRelation(attributes map[string]string) bool {
	value, ok := attributes["rel"]
	if !ok {
		return false
	}
	for _, relation := range strings.Fields(value) {
		switch strings.ToLower(relation) {
		case "stylesheet", "modulepreload":
			return true
		}
	}
	return false
}

func requireInventoriedReference(reference string, inventory map[string]struct{}) error {
	if err := validatePath(reference); err != nil {
		return err
	}
	if _, ok := inventory[reference]; !ok {
		return fmt.Errorf("reference points to untracked asset %q", reference)
	}
	return nil
}
