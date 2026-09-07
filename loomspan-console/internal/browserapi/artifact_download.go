package browserapi

import (
	"fmt"
	"io"
	"net/http"
	"path"
	"strings"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/applicationclient"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
)

const (
	rawDownloadBufferSize = 32 * 1024
)

// artifactRawDownload streams a finalized trace artifact directly from the
// upstream application to the browser without consulting or mutating the local
// artifact cache. It requires a valid paired session and exact host. It rejects
// query parameters, ranges, conditional requests, and ambiguous trace IDs.
func (router *Router) artifactRawDownload(response http.ResponseWriter, request *http.Request, _ string) {
	// Reject query parameters.
	if request.URL.RawQuery != "" {
		writeError(response, http.StatusBadRequest, "INVALID_REQUEST", "Query parameters are not accepted.")
		return
	}
	// Reject ranges and conditional requests.
	for _, header := range []string{"Range", "If-Range", "If-Match", "If-None-Match", "If-Modified-Since", "If-Unmodified-Since"} {
		if request.Header.Get(header) != "" {
			writeError(response, http.StatusBadRequest, "INVALID_REQUEST", "Conditional or range requests are not accepted.")
			return
		}
	}
	if router.options.Target == nil {
		reportUnavailable(response)
		writeError(response, http.StatusInternalServerError, "CONSOLE_ERROR", "Target service is unavailable.")
		return
	}
	// Extract the trace ID from the path: /api/console/v1/artifacts/{traceId}/raw
	trimmed := strings.TrimPrefix(request.URL.Path, "/api/console/v1/artifacts/")
	trimmed = strings.TrimSuffix(trimmed, "/raw")
	traceID := path.Clean(trimmed)
	if traceID == "" || traceID == "." || traceID == ".." || strings.ContainsAny(traceID, "/\\") {
		writeError(response, http.StatusBadRequest, "INVALID_REQUEST", "A valid trace ID is required.")
		return
	}
	scope, domain := router.options.Target.Capture()
	if domain != nil {
		writeDomainError(response, domain)
		return
	}
	responseScope(response, string(scope.ID))
	request = request.WithContext(diagnostics.WithScope(request.Context(), string(scope.ID)))
	stream, domain := scope.OpenArtifact(request.Context(), traceID)
	if domain != nil {
		writeDomainError(response, domain)
		return
	}
	defer stream.Close()
	// Commit the safe attachment headers atomically with a final current-scope
	// check. Rotation after commit cancels the stream and terminates the body;
	// rotation before commit returns the shared bounded error.
	declared := stream.DeclaredLength()
	domain = router.options.Target.PublishCurrentAtomic(scope.ID, func() {
		response.Header().Set("Content-Type", applicationclient.ArtifactMediaType)
		response.Header().Set("Cache-Control", "no-store")
		response.Header().Set("Content-Disposition", fmt.Sprintf(`attachment; filename="%s.ndjson"`, sanitizeFilename(traceID)))
		if declared >= 0 {
			response.Header().Set("Content-Length", fmt.Sprintf("%d", declared))
		}
		response.WriteHeader(http.StatusOK)
	})
	if domain != nil {
		writeDomainError(response, domain)
		return
	}
	flusher, _ := response.(http.Flusher)
	buffer := make([]byte, rawDownloadBufferSize)
	for {
		n, err := stream.Body().Read(buffer)
		if n > 0 {
			if _, writeErr := response.Write(buffer[:n]); writeErr != nil {
				return
			}
			if flusher != nil {
				flusher.Flush()
			}
		}
		if err != nil {
			if err != io.EOF && scope.Context.Err() == nil {
				diagnostics.Report(diagnostics.WithScope(request.Context(), string(scope.ID)), diagnostics.Annotate(err, diagnostics.Facts{Classification: "internal", Cause: "body_read", Endpoint: "artifact.download", Stage: "stream"}))
			}
			return
		}
	}
}

// sanitizeFilename produces a safe filename from a trace identifier. It
// replaces any character that is not alphanumeric, dash, or underscore with
// an underscore, and bounds the result length.
func sanitizeFilename(traceID string) string {
	var builder strings.Builder
	count := 0
	for _, r := range traceID {
		if count >= 128 {
			break
		}
		if (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') || r == '-' || r == '_' {
			builder.WriteRune(r)
		} else {
			builder.WriteRune('_')
		}
		count++
	}
	result := builder.String()
	if result == "" {
		return "trace"
	}
	return result
}
