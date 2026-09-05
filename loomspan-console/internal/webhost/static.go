package webhost

import (
	"io/fs"
	"mime"
	"net/http"
	"path"
	"regexp"
	"strconv"
	"strings"
)

const (
	noStore   = "no-store"
	immutable = "public, max-age=31536000, immutable"
)

var contentAddressedAsset = regexp.MustCompile(`(?:^|/)[^/]+-[A-Za-z0-9_-]{8,}\.[A-Za-z0-9]+$`)

func StaticHandler(files fs.FS) http.Handler {
	return http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		ApplyBrowserHeaders(response.Header())
		response.Header().Set("Cache-Control", noStore)
		if request.Method != http.MethodGet && request.Method != http.MethodHead {
			response.Header().Set("Allow", "GET, HEAD")
			http.Error(response, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		name, navigation, ok := requestPath(request)
		if !ok {
			http.NotFound(response, request)
			return
		}
		data, err := fs.ReadFile(files, name)
		if err != nil {
			if navigation {
				name = "index.html"
				data, err = fs.ReadFile(files, name)
			}
			if err != nil {
				http.NotFound(response, request)
				return
			}
		}
		contentType := mime.TypeByExtension(path.Ext(name))
		switch path.Ext(name) {
		case ".html":
			contentType = "text/html; charset=utf-8"
		case ".js":
			contentType = "text/javascript; charset=utf-8"
		case ".css":
			contentType = "text/css; charset=utf-8"
		case ".json":
			contentType = "application/json"
		}
		if contentType == "" {
			contentType = "application/octet-stream"
		}
		response.Header().Set("Content-Type", contentType)
		response.Header().Set("Content-Length", strconv.Itoa(len(data)))
		if name != "index.html" && isContentAddressed(name) {
			response.Header().Set("Cache-Control", immutable)
		}
		response.WriteHeader(http.StatusOK)
		if request.Method == http.MethodGet {
			_, _ = response.Write(data)
		}
	})
}

const browserContentSecurityPolicy = "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'; object-src 'none'; manifest-src 'self'"

// ApplyBrowserHeaders installs the policy shared by every browser-visible
// response, including errors produced before a route handler is reached.
func ApplyBrowserHeaders(header http.Header) {
	header.Set("Content-Security-Policy", browserContentSecurityPolicy)
	header.Set("X-Frame-Options", "DENY")
	header.Set("X-Content-Type-Options", "nosniff")
	header.Set("Referrer-Policy", "no-referrer")
	header.Set("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()")
	header.Set("Cross-Origin-Opener-Policy", "same-origin")
	header.Set("Cross-Origin-Resource-Policy", "same-origin")
}

func requestPath(request *http.Request) (name string, navigation bool, ok bool) {
	if request.URL.Path == "" || !strings.HasPrefix(request.URL.Path, "/") {
		return "", false, false
	}
	escaped := strings.ToLower(request.URL.EscapedPath())
	if strings.Contains(escaped, "%2f") || strings.Contains(escaped, "%5c") ||
		strings.Contains(escaped, "%2e") || strings.Contains(request.URL.Path, "\\") {
		return "", false, false
	}
	cleaned := path.Clean(request.URL.Path)
	if cleaned != request.URL.Path && request.URL.Path != "/" {
		return "", false, false
	}
	if cleaned == "/api/console" || strings.HasPrefix(cleaned, "/api/console/") {
		return strings.TrimPrefix(cleaned, "/"), false, true
	}
	if cleaned == "/" {
		return "index.html", true, true
	}
	name = strings.TrimPrefix(cleaned, "/")
	if name == "" || strings.HasPrefix(name, ".") {
		return "", false, false
	}
	navigation = path.Ext(name) == ""
	return name, navigation, true
}

func isContentAddressed(name string) bool {
	return contentAddressedAsset.MatchString(name)
}
