package applicationclient

import (
	"context"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"sync"
)

// ArtifactMediaType is the exact media type the Java adapter emits for finalized
// trace artifacts and the only media type OpenArtifact accepts on a 200 response.
const ArtifactMediaType = "application/x-ndjson"

// ArtifactStream is a closeable, non-buffered view over a finalized trace
// artifact response body. The body is streamed directly from the upstream HTTP
// transport; callers must not retain Body beyond the lifetime of the stream and
// must call Close exactly once to release the underlying connection.
//
// DeclaredLength is the upstream-declared Content-Length when present and is -1
// when the upstream omitted the header. The service layer compares the final
// observed byte count against both DeclaredLength (when present) and the
// authoritative trace metadata, so an unknown length does not bypass
// verification.
type ArtifactStream struct {
	body           io.ReadCloser
	instanceID     string
	mediaType      string
	declaredLength int64
	response       *http.Response
	cancel         context.CancelFunc
	closeHooks     []func()
	closed         bool
	mu             sync.Mutex
}

// Body returns the streaming artifact body. The returned reader remains valid
// until Close is called; reading after Close returns an error.
func (stream *ArtifactStream) Body() io.Reader {
	return stream.body
}

// InstanceID returns the validated application instance ID observed on the
// response. It always matches the instance ID supplied to OpenArtifact for a
// successful stream.
func (stream *ArtifactStream) InstanceID() string {
	return stream.instanceID
}

// MediaType returns the exact Content-Type media type (without parameters) the
// upstream returned. OpenArtifact only publishes a stream when this equals
// ArtifactMediaType.
func (stream *ArtifactStream) MediaType() string {
	return stream.mediaType
}

// DeclaredLength returns the upstream-declared Content-Length, or -1 when the
// upstream omitted the header. A present value is the exact byte count the
// upstream promised; the service layer verifies the streamed bytes against it.
func (stream *ArtifactStream) DeclaredLength() int64 {
	return stream.declaredLength
}

// AddCloseHook registers lifecycle cleanup that runs exactly once when the
// stream closes. If the stream is already closed, the hook runs immediately.
// This lets target scopes release cancellation registrations without exposing
// transport internals.
func (stream *ArtifactStream) AddCloseHook(hook func()) {
	if hook == nil {
		return
	}
	stream.mu.Lock()
	if stream.closed {
		stream.mu.Unlock()
		hook()
		return
	}
	stream.closeHooks = append(stream.closeHooks, hook)
	stream.mu.Unlock()
}

// Close releases the upstream response and interrupts any blocked read. It is
// idempotent and safe to call concurrently with an in-flight Read. For
// production streams (created by OpenArtifact), this closes the underlying
// HTTP response body. For test streams (created by NewTestArtifactStream),
// this closes the injected body reader directly.
func (stream *ArtifactStream) Close() error {
	stream.mu.Lock()
	if stream.closed {
		stream.mu.Unlock()
		return nil
	}
	stream.closed = true
	response := stream.response
	cancel := stream.cancel
	body := stream.body
	hooks := stream.closeHooks
	stream.closeHooks = nil
	stream.mu.Unlock()
	for _, hook := range hooks {
		hook()
	}
	if cancel != nil {
		cancel()
	}
	if response != nil {
		return response.Body.Close()
	}
	if body != nil {
		return body.Close()
	}
	return nil
}

// OpenArtifact opens a streaming GET to the authenticated Java finalized-trace
// artifact endpoint for traceId. It applies the same application credential,
// direct transport, redirect rejection, compression rejection, problem parsing,
// identity validation, and cancellation semantics used by other target
// operations, but it does not buffer successful artifact bytes. Only problem
// bodies and response headers are bounded.
//
// The returned stream is owned by the caller and must be closed to release the
// upstream connection. Caller or scope cancellation interrupts a blocked read
// and is surfaced as context.Canceled.
func (client *Client) OpenArtifact(parent context.Context, traceId, instanceID string, credential Credential) (*ArtifactStream, error) {
	if credential == nil {
		return nil, newFailure(FailureAuthentication, "", nil)
	}
	if traceId == "" {
		return nil, newFailure(FailureInvalidArgument, "", nil)
	}
	// Artifact bodies can legitimately stream for substantially longer than
	// the bounded JSON request timeout. The transport's response-header
	// timeout still bounds connection establishment; caller and target-scope
	// cancellation own the body lifetime after headers arrive.
	requestContext, cancel := context.WithCancel(parent)
	endpoint := client.address.ArtifactEndpoint(traceId)
	request, err := http.NewRequestWithContext(requestContext, http.MethodGet, endpoint, nil)
	if err != nil {
		cancel()
		return nil, protocolFailure()
	}
	request.Header.Set("Accept", ArtifactMediaType)
	request.Header.Set("Accept-Encoding", "identity")
	request.Header.Set("Cache-Control", "no-store")
	// Reject any conditional/range request headers a future Credential
	// implementation or request-construction change might forward upstream.
	// The request is built from scratch here, so these are absent today; the
	// guard keeps artifact acquisition from issuing range/conditional GETs.
	for _, header := range []string{"Range", "If-Range", "If-Match", "If-None-Match", "If-Modified-Since", "If-Unmodified-Since"} {
		if request.Header.Get(header) != "" {
			cancel()
			return nil, newFailure(FailureInvalidArgument, "", nil)
		}
	}
	if err := credential.Apply(request); err != nil {
		cancel()
		return nil, newFailure(FailureAuthentication, "", nil)
	}
	if len(request.Header.Values(APIKeyHeader)) != 1 {
		cancel()
		return nil, newFailure(FailureAuthentication, "", nil)
	}
	response, err := client.http.Do(request)
	if err != nil {
		cancel()
		if errors.Is(err, context.Canceled) {
			return nil, context.Canceled
		}
		return nil, classifyTransport(err)
	}
	if response.StatusCode >= 300 && response.StatusCode < 400 {
		response.Body.Close()
		cancel()
		return nil, newFailure(FailureUnavailable, CategoryRedirect, nil)
	}
	if encoding := response.Header.Get("Content-Encoding"); encoding != "" && !strings.EqualFold(encoding, "identity") {
		response.Body.Close()
		cancel()
		return nil, protocolFailure()
	}
	if response.StatusCode != http.StatusOK {
		body, readErr := readBounded(response.Body, problemMaxBytes)
		response.Body.Close()
		if readErr != nil {
			slog.Error("artifact error body read failed", "status", response.StatusCode, "limit", problemMaxBytes, "err", readErr)
			cancel()
			return nil, protocolFailure()
		}
		failure := mapProblem(response.StatusCode, response.Header.Get("Content-Type"), body)
		if f, ok := failure.(*Failure); ok {
			slog.Error("artifact upstream returned non-200", "status", response.StatusCode, "failureKind", f.Kind)
		} else {
			slog.Error("artifact upstream returned non-200", "status", response.StatusCode)
		}
		instanceIDFromHeader, identityErr := optionalResponseInstanceID(response.Header.Values(InstanceIDHeader))
		if identityErr != nil {
			cancel()
			return nil, protocolFailure()
		}
		_ = instanceIDFromHeader
		cancel()
		return nil, failure
	}
	contentType := response.Header.Get("Content-Type")
	mediaType, _, mediaErr := parseMediaType(contentType)
	if mediaErr != nil || !strings.EqualFold(mediaType, ArtifactMediaType) {
		response.Body.Close()
		cancel()
		return nil, protocolFailure()
	}
	headerInstanceID, err := responseInstanceID(response.Header.Values(InstanceIDHeader))
	if err != nil {
		response.Body.Close()
		cancel()
		return nil, protocolFailure()
	}
	if headerInstanceID != instanceID {
		response.Body.Close()
		cancel()
		return nil, &InstanceMismatch{Actual: headerInstanceID}
	}
	declaredLength, lengthErr := parseArtifactContentLength(response.Header.Values("Content-Length"))
	if lengthErr != nil {
		response.Body.Close()
		cancel()
		return nil, protocolFailure()
	}
	return &ArtifactStream{
		body:           response.Body,
		response:       response,
		cancel:         cancel,
		instanceID:     headerInstanceID,
		mediaType:      mediaType,
		declaredLength: declaredLength,
	}, nil
}

// parseMediaType splits a Content-Type header into its lowercase media type
// and parameters without accepting empty or malformed values. Unlike the
// activity stream, which accepts any Content-Type prefixed with
// "text/event-stream", artifact responses require an exact media-type match
// (parameters are ignored) so a stricter split-on-";" parse is used here.
func parseMediaType(value string) (string, string, error) {
	trimmed := strings.TrimSpace(value)
	if trimmed == "" {
		return "", "", fmt.Errorf("missing content type")
	}
	parts := strings.SplitN(trimmed, ";", 2)
	mediaType := strings.TrimSpace(parts[0])
	if mediaType == "" {
		return "", "", fmt.Errorf("missing media type")
	}
	parameters := ""
	if len(parts) == 2 {
		parameters = strings.TrimSpace(parts[1])
	}
	return strings.ToLower(mediaType), parameters, nil
}

// parseArtifactContentLength accepts exactly one Content-Length header value
// and returns -1 when the header is absent. Duplicate, non-numeric, or
// negative values are protocol failures.
func parseArtifactContentLength(values []string) (int64, error) {
	if len(values) == 0 {
		return -1, nil
	}
	if len(values) != 1 {
		return 0, fmt.Errorf("artifact response has duplicate Content-Length")
	}
	length, err := strconv.ParseInt(values[0], 10, 64)
	if err != nil || length < 0 {
		return 0, fmt.Errorf("artifact response has invalid Content-Length")
	}
	return length, nil
}
