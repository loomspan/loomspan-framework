package mcpadapter

import (
	"context"
	"errors"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/mcpcredential"
)

const maxRequestBody = 1 << 20
const requestTimeout = 10 * time.Second

type authenticator interface {
	Snapshot() mcpcredential.Snapshot
	Authenticate(string) (uint64, bool)
}

func SecurityHandler(port int, credentials authenticator, tracker *Tracker, next http.Handler) http.Handler {
	allowedHosts := map[string]bool{"127.0.0.1:" + strconv.Itoa(port): true, "localhost:" + strconv.Itoa(port): true}
	return http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		request = request.WithContext(diagnostics.WithRequest(request.Context(), "mcp.http"))
		if request.URL.IsAbs() || request.Host == "" || strings.Contains(request.Host, ",") || !allowedHosts[strings.ToLower(request.Host)] {
			diagnostics.Reject(request.Context(), "mcp", "host")
			transportError(response, http.StatusBadRequest, "invalid MCP authority")
			return
		}
		origins := request.Header.Values("Origin")
		if len(origins) > 1 || (len(origins) == 1 && !validOrigin(origins[0], allowedHosts)) {
			diagnostics.Reject(request.Context(), "mcp", "origin")
			transportError(response, http.StatusForbidden, "invalid MCP origin")
			return
		}
		if credentials.Snapshot().State != mcpcredential.Enabled {
			diagnostics.Reject(request.Context(), "mcp", "disabled")
			transportError(response, http.StatusServiceUnavailable, "MCP is disabled")
			return
		}
		authorizations := request.Header.Values("Authorization")
		if len(authorizations) != 1 || strings.Contains(authorizations[0], ",") || !strings.HasPrefix(authorizations[0], "Bearer ") {
			diagnostics.Reject(request.Context(), "mcp", "authentication")
			transportError(response, http.StatusUnauthorized, "MCP authentication required")
			return
		}
		credential := strings.TrimPrefix(authorizations[0], "Bearer ")
		if credential == "" || strings.TrimSpace(credential) != credential {
			diagnostics.Reject(request.Context(), "mcp", "authentication")
			transportError(response, http.StatusUnauthorized, "MCP authentication required")
			return
		}
		generation, ok := credentials.Authenticate(credential)
		if !ok {
			diagnostics.Reject(request.Context(), "mcp", "authentication")
			transportError(response, http.StatusUnauthorized, "MCP authentication required")
			return
		}
		ctx, cancel := context.WithTimeout(request.Context(), requestTimeout)
		defer cancel()
		ctx, done, err := tracker.Admit(ctx, generation)
		if err != nil {
			diagnostics.Reject(request.Context(), "mcp", "admission")
			transportError(response, http.StatusServiceUnavailable, "MCP is temporarily unavailable")
			return
		}
		defer done()
		// Authentication and admission cannot be one lock acquisition because
		// the credential store and tracker are independent owners. Close that
		// gap by revalidating after admission. Once registered, any later freeze
		// observes this request and cannot commit a new generation until done.
		admitted := credentials.Snapshot()
		if admitted.State != mcpcredential.Enabled || admitted.Generation != generation {
			diagnostics.Reject(request.Context(), "mcp", "authentication")
			transportError(response, http.StatusUnauthorized, "MCP authentication required")
			return
		}
		if request.ContentLength > maxRequestBody {
			diagnostics.Reject(request.Context(), "mcp", "body_limit")
			transportError(response, http.StatusRequestEntityTooLarge, "MCP request body is too large")
			return
		}
		request.Body = http.MaxBytesReader(response, request.Body, maxRequestBody)
		response.Header().Set("Cache-Control", "no-store")
		next.ServeHTTP(&noStoreResponseWriter{ResponseWriter: response, ctx: ctx}, request.WithContext(ctx))
		if errors.Is(ctx.Err(), context.DeadlineExceeded) && request.Context().Err() == nil {
			diagnostics.Report(request.Context(), diagnostics.Annotate(errors.New("MCP request timed out"), diagnostics.Facts{Classification: "internal", Cause: "timeout", Stage: "request"}))
		}
	})
}

type noStoreResponseWriter struct {
	http.ResponseWriter
	ctx    context.Context
	failed bool
}

func (writer *noStoreResponseWriter) Unwrap() http.ResponseWriter { return writer.ResponseWriter }
func (writer *noStoreResponseWriter) WriteHeader(status int) {
	writer.Header().Set("Cache-Control", "no-store")
	writer.ResponseWriter.WriteHeader(status)
}
func (writer *noStoreResponseWriter) Write(content []byte) (int, error) {
	writer.Header().Set("Cache-Control", "no-store")
	n, err := writer.ResponseWriter.Write(content)
	if err == nil && n < len(content) {
		err = io.ErrShortWrite
	}
	if err != nil && !writer.failed {
		writer.failed = true
		diagnostics.Report(writer.ctx, diagnostics.Annotate(err, diagnostics.Facts{Classification: "internal", Cause: "response_write", Stage: "response"}))
	}
	return n, err
}

func validOrigin(raw string, allowedHosts map[string]bool) bool {
	if raw == "" || strings.Contains(raw, ",") {
		return false
	}
	origin, err := url.Parse(raw)
	return err == nil && origin.Scheme == "http" && origin.User == nil && origin.Path == "" && origin.RawQuery == "" && origin.Fragment == "" && allowedHosts[strings.ToLower(origin.Host)]
}

func transportError(response http.ResponseWriter, status int, message string) {
	response.Header().Set("Cache-Control", "no-store")
	response.Header().Set("Content-Type", "text/plain; charset=utf-8")
	response.WriteHeader(status)
	_, _ = response.Write([]byte(message + "\n"))
}
