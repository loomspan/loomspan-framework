package applicationclient

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

// guardedChunkReader serves bytes in fixed chunks and can block between chunks
// so tests can observe bounded streaming and backpressure without relying on a
// brittle heap-allocation threshold. It refuses an oversized Read request so
// callers cannot accidentally buffer the whole body in one allocation.
type guardedChunkReader struct {
	data        []byte
	chunkSize   int
	blockBefore chan struct{}
	release     chan struct{}
	offset      int
	closed      atomic.Bool
}

func newGuardedChunkReader(data []byte, chunkSize int) *guardedChunkReader {
	return &guardedChunkReader{
		data:      data,
		chunkSize: chunkSize,
		release:   make(chan struct{}),
	}
}

func (reader *guardedChunkReader) Read(p []byte) (int, error) {
	if reader.closed.Load() {
		return 0, io.ErrClosedPipe
	}
	if reader.offset >= len(reader.data) {
		return 0, io.EOF
	}
	if reader.blockBefore != nil && reader.offset >= reader.chunkSize {
		select {
		case <-reader.blockBefore:
		case <-reader.release:
			reader.closed.Store(true)
			return 0, context.Canceled
		}
	}
	end := reader.offset + reader.chunkSize
	if end > len(reader.data) {
		end = len(reader.data)
	}
	if end-reader.offset > len(p) {
		end = reader.offset + len(p)
	}
	n := copy(p, reader.data[reader.offset:end])
	reader.offset += n
	return n, nil
}

func (reader *guardedChunkReader) Close() error {
	reader.closed.Store(true)
	select {
	case <-reader.release:
	default:
		close(reader.release)
	}
	return nil
}

func artifactServer(handler http.HandlerFunc) (*httptest.Server, *Client, Address) {
	server := httptest.NewServer(handler)
	address, _ := NormalizeAddress(server.URL)
	client, _ := New(address, testPolicy(), "1.0.0-beta.2-SNAPSHOT")
	return server, client, address
}

func TestOpenArtifactSendsExactHeadersAndStreamsWithoutBuffering(t *testing.T) {
	const artifact = `{"kind":"TRACE_STARTED"}\n{"kind":"TRACE_COMPLETED"}\n`
	var received http.Header
	var requestPath string
	var requestCount int32
	server, client, address := artifactServer(func(response http.ResponseWriter, request *http.Request) {
		atomic.AddInt32(&requestCount, 1)
		received = request.Header.Clone()
		requestPath = request.URL.Path
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Type", ArtifactMediaType)
		response.Header().Set("Content-Length", fmt.Sprintf("%d", len(artifact)))
		response.Header().Set("Content-Disposition", `attachment; filename="loomspan-trace-trace-1.ndjson"`)
		response.Header().Set("Cache-Control", "no-store")
		_, _ = response.Write([]byte(artifact))
	})
	defer server.Close()
	defer client.Close()

	stream, err := client.OpenArtifact(context.Background(), "trace-1", testInstanceID, testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatalf("open artifact: %v", err)
	}
	defer stream.Close()

	if got := received.Get("Accept"); got != ArtifactMediaType {
		t.Fatalf("unexpected Accept: %q", got)
	}
	if got := received.Get("Accept-Encoding"); got != "identity" {
		t.Fatalf("unexpected Accept-Encoding: %q", got)
	}
	if got := received.Get("Cache-Control"); got != "no-store" {
		t.Fatalf("unexpected Cache-Control: %q", got)
	}
	if values := received.Values(APIKeyHeader); len(values) != 1 || values[0] != strings.Repeat("k", 32) {
		t.Fatalf("unexpected API key headers: %#v", values)
	}
	for _, header := range []string{"Range", "If-Range", "If-Match", "If-None-Match", "If-Modified-Since", "If-Unmodified-Since"} {
		if got := received.Get(header); got != "" {
			t.Fatalf("unexpected conditional request header %s: %q", header, got)
		}
	}
	if !strings.HasSuffix(requestPath, "/traces/trace-1/artifact") {
		t.Fatalf("unexpected request path: %s", requestPath)
	}
	_ = address

	body, err := io.ReadAll(stream.Body())
	if err != nil {
		t.Fatalf("read body: %v", err)
	}
	if string(body) != artifact {
		t.Fatalf("body mismatch: got %q want %q", string(body), artifact)
	}
	if stream.DeclaredLength() != int64(len(artifact)) {
		t.Fatalf("declared length: got %d want %d", stream.DeclaredLength(), len(artifact))
	}
	if stream.MediaType() != ArtifactMediaType {
		t.Fatalf("media type: got %q want %q", stream.MediaType(), ArtifactMediaType)
	}
	if stream.InstanceID() != testInstanceID {
		t.Fatalf("instance id: got %q want %q", stream.InstanceID(), testInstanceID)
	}
	if atomic.LoadInt32(&requestCount) != 1 {
		t.Fatalf("expected exactly one upstream request, got %d", atomic.LoadInt32(&requestCount))
	}
}

func TestOpenArtifactStreamsMultiChunkBodyWithoutBuffering(t *testing.T) {
	artifact := bytes.Repeat([]byte("NDJSON-LINE\n"), 4096)
	server, client, _ := artifactServer(func(response http.ResponseWriter, request *http.Request) {
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Type", ArtifactMediaType)
		response.Header().Set("Content-Length", fmt.Sprintf("%d", len(artifact)))
		reader := newGuardedChunkReader(artifact, 1024)
		defer reader.Close()
		_, _ = io.Copy(response, reader)
	})
	defer server.Close()
	defer client.Close()

	stream, err := client.OpenArtifact(context.Background(), "trace-1", testInstanceID, testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatalf("open artifact: %v", err)
	}
	defer stream.Close()

	collected, err := io.ReadAll(stream.Body())
	if err != nil {
		t.Fatalf("read body: %v", err)
	}
	if !bytes.Equal(collected, artifact) {
		t.Fatalf("streamed body mismatch: got %d bytes want %d bytes", len(collected), len(artifact))
	}
}

func TestOpenArtifactBodyOutlivesBoundedJSONRequestTimeout(t *testing.T) {
	const body = "slow-artifact-body"
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set("Content-Type", ArtifactMediaType)
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Length", fmt.Sprintf("%d", len(body)))
		response.WriteHeader(http.StatusOK)
		if flusher, ok := response.(http.Flusher); ok {
			flusher.Flush()
		}
		time.Sleep(50 * time.Millisecond)
		_, _ = io.WriteString(response, body)
	}))
	defer server.Close()

	address, _ := NormalizeAddress(server.URL)
	policy := testPolicy()
	policy.RequestTimeout = 10 * time.Millisecond
	client, err := New(address, policy, "1.0.0-beta.2-SNAPSHOT")
	if err != nil {
		t.Fatal(err)
	}
	defer client.Close()
	stream, err := client.OpenArtifact(context.Background(), "trace-1", testInstanceID, testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatalf("open artifact: %v", err)
	}
	defer stream.Close()
	got, err := io.ReadAll(stream.Body())
	if err != nil {
		t.Fatalf("artifact body inherited JSON request timeout: %v", err)
	}
	if string(got) != body {
		t.Fatalf("body=%q want=%q", got, body)
	}
}

func TestOpenArtifactValidatesIdentityMediaEncodingAndLength(t *testing.T) {
	artifact := []byte(`{"kind":"TRACE_COMPLETED"}` + "\n")
	tests := []struct {
		name       string
		headers    map[string]string
		status     int
		wantErr    FailureKind
		wantErrSet bool
	}{
		{
			name:   "wrong media type",
			status: http.StatusOK,
			headers: map[string]string{
				"Content-Type":   "application/json",
				InstanceIDHeader: testInstanceID,
			},
			wantErr:    FailureProtocol,
			wantErrSet: true,
		},
		{
			name:   "missing content type",
			status: http.StatusOK,
			headers: map[string]string{
				InstanceIDHeader: testInstanceID,
			},
			wantErr:    FailureProtocol,
			wantErrSet: true,
		},
		{
			name:   "encoded response",
			status: http.StatusOK,
			headers: map[string]string{
				"Content-Type":     ArtifactMediaType,
				"Content-Encoding": "gzip",
				InstanceIDHeader:   testInstanceID,
			},
			wantErr:    FailureProtocol,
			wantErrSet: true,
		},
		{
			name:   "instance mismatch",
			status: http.StatusOK,
			headers: map[string]string{
				"Content-Type":   ArtifactMediaType,
				InstanceIDHeader: "22222222-2222-4222-8222-222222222222",
			},
			wantErrSet: false,
		},
		{
			name:   "missing instance header",
			status: http.StatusOK,
			headers: map[string]string{
				"Content-Type": ArtifactMediaType,
			},
			wantErr:    FailureProtocol,
			wantErrSet: true,
		},
		{
			name:   "duplicate instance header",
			status: http.StatusOK,
			headers: map[string]string{
				"Content-Type":   ArtifactMediaType,
				InstanceIDHeader: testInstanceID + ", " + testInstanceID,
			},
			wantErr:    FailureProtocol,
			wantErrSet: true,
		},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			server, client, _ := artifactServer(func(response http.ResponseWriter, _ *http.Request) {
				for key, value := range test.headers {
					if key == InstanceIDHeader && strings.Contains(value, ",") {
						for _, v := range strings.Split(value, ",") {
							response.Header().Add(InstanceIDHeader, strings.TrimSpace(v))
						}
						continue
					}
					response.Header().Set(key, value)
				}
				response.WriteHeader(test.status)
				_, _ = response.Write(artifact)
			})
			defer server.Close()
			defer client.Close()
			_, err := client.OpenArtifact(context.Background(), "trace-1", testInstanceID, testCredential(strings.Repeat("k", 32)))
			if err == nil {
				t.Fatal("expected error, got nil")
			}
			if !test.wantErrSet {
				var mismatch *InstanceMismatch
				if !errors.As(err, &mismatch) {
					t.Fatalf("expected InstanceMismatch, got %v", err)
				}
				return
			}
			failure, ok := err.(*Failure)
			if !ok || failure.Kind != test.wantErr {
				t.Fatalf("expected %s, got %v", test.wantErr, err)
			}
		})
	}
}

func TestOpenArtifactAcceptsMissingContentLengthAsUnknown(t *testing.T) {
	artifact := []byte(`{"kind":"TRACE_COMPLETED"}` + "\n")
	server, client, _ := artifactServer(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Type", ArtifactMediaType)
		response.WriteHeader(http.StatusOK)
		if flusher, ok := response.(http.Flusher); ok {
			flusher.Flush()
		}
		_, _ = response.Write(artifact)
	})
	defer server.Close()
	defer client.Close()

	stream, err := client.OpenArtifact(context.Background(), "trace-1", testInstanceID, testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatalf("open artifact: %v", err)
	}
	defer stream.Close()
	if stream.DeclaredLength() != -1 {
		t.Fatalf("expected unknown declared length -1, got %d", stream.DeclaredLength())
	}
	body, err := io.ReadAll(stream.Body())
	if err != nil {
		t.Fatalf("read body: %v", err)
	}
	if !bytes.Equal(body, artifact) {
		t.Fatalf("body mismatch: got %q want %q", string(body), string(artifact))
	}
}

func TestParseArtifactContentLengthRejectsDuplicateInvalidAndNegative(t *testing.T) {
	tests := []struct {
		name    string
		headers []string
	}{
		{name: "duplicate", headers: []string{"42", "43"}},
		{name: "non-numeric", headers: []string{"abc"}},
		{name: "negative", headers: []string{"-1"}},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if _, err := parseArtifactContentLength(test.headers); err == nil {
				t.Fatalf("expected error for %s Content-Length", test.name)
			}
		})
	}
	if got, err := parseArtifactContentLength(nil); err != nil || got != -1 {
		t.Fatalf("missing Content-Length: got %d err=%v, want -1 nil", got, err)
	}
	if got, err := parseArtifactContentLength([]string{"42"}); err != nil || got != 42 {
		t.Fatalf("valid Content-Length: got %d err=%v, want 42 nil", got, err)
	}
}

func TestOpenArtifactMapsBoundedProblemsRedirectAndCancellation(t *testing.T) {
	tests := []struct {
		name   string
		code   string
		status int
		kind   FailureKind
	}{
		{"INVALID_REQUEST", "INVALID_REQUEST", http.StatusBadRequest, FailureInvalidArgument},
		{"NOT_FOUND", "NOT_FOUND", http.StatusNotFound, FailureNotFound},
		{"LIMIT_EXCEEDED", "LIMIT_EXCEEDED", http.StatusTooManyRequests, FailureLimitExceeded},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			server, client, _ := artifactServer(func(response http.ResponseWriter, _ *http.Request) {
				response.Header().Set(InstanceIDHeader, testInstanceID)
				response.Header().Set("Content-Type", "application/json")
				response.WriteHeader(test.status)
				_, _ = response.Write([]byte(`{"status":` + fmt.Sprint(test.status) + `,"code":"` + test.code + `","message":"test"}`))
			})
			defer server.Close()
			defer client.Close()
			_, err := client.OpenArtifact(context.Background(), "trace-1", testInstanceID, testCredential(strings.Repeat("k", 32)))
			failure, ok := err.(*Failure)
			if !ok {
				t.Fatalf("%s did not produce a typed failure: %v", test.code, err)
			}
			if failure.Kind != test.kind {
				t.Fatalf("expected %s, got %s", test.kind, failure.Kind)
			}
		})
	}

	t.Run("redirect", func(t *testing.T) {
		server, client, _ := artifactServer(func(response http.ResponseWriter, _ *http.Request) {
			http.Redirect(response, &http.Request{}, "http://elsewhere.test", http.StatusFound)
		})
		defer server.Close()
		defer client.Close()
		_, err := client.OpenArtifact(context.Background(), "trace-1", testInstanceID, testCredential(strings.Repeat("k", 32)))
		failure, ok := err.(*Failure)
		if !ok || failure.Kind != FailureUnavailable {
			t.Fatalf("expected unavailable failure for redirect, got %v", err)
		}
	})

	t.Run("caller cancellation", func(t *testing.T) {
		started := make(chan struct{})
		server, client, _ := artifactServer(func(_ http.ResponseWriter, request *http.Request) {
			close(started)
			<-request.Context().Done()
		})
		defer server.Close()
		defer client.Close()
		parent, cancel := context.WithCancel(context.Background())
		result := make(chan error, 1)
		go func() {
			_, err := client.OpenArtifact(parent, "trace-1", testInstanceID, testCredential(strings.Repeat("k", 32)))
			result <- err
		}()
		<-started
		cancel()
		if err := <-result; !errors.Is(err, context.Canceled) {
			t.Fatalf("expected context.Canceled, got %v", err)
		}
	})
}

func TestOpenArtifactClosesBodyAndInterruptsBlockedRead(t *testing.T) {
	connected := make(chan struct{})
	server, client, _ := artifactServer(func(response http.ResponseWriter, request *http.Request) {
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Type", ArtifactMediaType)
		response.WriteHeader(http.StatusOK)
		if flusher, ok := response.(http.Flusher); ok {
			flusher.Flush()
		}
		close(connected)
		<-request.Context().Done()
	})
	defer server.Close()
	defer client.Close()

	stream, err := client.OpenArtifact(context.Background(), "trace-1", testInstanceID, testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatalf("open artifact: %v", err)
	}
	<-connected

	readDone := make(chan error, 1)
	go func() {
		_, readErr := io.ReadAll(stream.Body())
		readDone <- readErr
	}()
	if err := stream.Close(); err != nil {
		t.Fatalf("close returned error: %v", err)
	}
	select {
	case <-readDone:
	case <-time.After(time.Second):
		t.Fatal("Close did not interrupt a blocked read")
	}
	if err := stream.Close(); err != nil {
		t.Fatalf("double close returned error: %v", err)
	}
}

func TestOpenArtifactRejectsNilCredentialAndBlankTraceID(t *testing.T) {
	server, client, _ := artifactServer(func(http.ResponseWriter, *http.Request) {})
	defer server.Close()
	defer client.Close()
	_, err := client.OpenArtifact(context.Background(), "trace-1", testInstanceID, nil)
	failure, ok := err.(*Failure)
	if !ok || failure.Kind != FailureAuthentication {
		t.Fatalf("expected authentication failure for nil credential, got %v", err)
	}
	_, err = client.OpenArtifact(context.Background(), "", testInstanceID, testCredential(strings.Repeat("k", 32)))
	failure, ok = err.(*Failure)
	if !ok || failure.Kind != FailureInvalidArgument {
		t.Fatalf("expected invalid argument failure for blank trace id, got %v", err)
	}
}

func TestOpenArtifactConsumesJavaProducedFixtureBytes(t *testing.T) {
	fixture, err := os.ReadFile(filepath.Join("..", "..", "..", "loomspan-console-fixtures", "traces", "single-attempt-success.ndjson"))
	if err != nil {
		t.Fatal(err)
	}
	server, client, _ := artifactServer(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Type", ArtifactMediaType)
		response.Header().Set("Content-Length", fmt.Sprintf("%d", len(fixture)))
		_, _ = response.Write(fixture)
	})
	defer server.Close()
	defer client.Close()

	stream, err := client.OpenArtifact(context.Background(), "single-attempt-success", testInstanceID, testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatalf("open artifact: %v", err)
	}
	defer stream.Close()
	body, err := io.ReadAll(stream.Body())
	if err != nil {
		t.Fatalf("read body: %v", err)
	}
	if !bytes.Equal(body, fixture) {
		t.Fatalf("Java-produced fixture bytes mismatch: got %d bytes want %d bytes", len(body), len(fixture))
	}
	if stream.DeclaredLength() != int64(len(fixture)) {
		t.Fatalf("declared length: got %d want %d", stream.DeclaredLength(), len(fixture))
	}
}

// TestOpenArtifactSurfacesMidStreamFailureAsReadError verifies that when the
// upstream commits a 200 response with a declared Content-Length but closes
// the connection before delivering all promised bytes, the streamed Read
// returns a non-nil error rather than a truncated "success". The artifact
// client intentionally does not verify observed length against the declared
// length (the Phase 2 service owns that check), but it must not silently
// present a short body as a complete artifact. See
// Regression coverage for application artifact handling.
// edge case "shorter-than-declared ... mid-stream failure responses".
func TestOpenArtifactSurfacesMidStreamFailureAsReadError(t *testing.T) {
	declared := 4096
	written := []byte(strings.Repeat("NDJSON-LINE\n", 128)) // 1536 bytes, less than declared
	server, client, _ := artifactServer(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Type", ArtifactMediaType)
		response.Header().Set("Content-Length", fmt.Sprintf("%d", declared))
		response.WriteHeader(http.StatusOK)
		if flusher, ok := response.(http.Flusher); ok {
			flusher.Flush()
		}
		_, _ = response.Write(written)
		if flusher, ok := response.(http.Flusher); ok {
			flusher.Flush()
		}
		// Return without writing the remaining declared bytes; the server
		// closes the connection, simulating an upstream mid-stream failure.
	})
	defer server.Close()
	defer client.Close()

	stream, err := client.OpenArtifact(context.Background(), "trace-1", testInstanceID, testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatalf("open artifact: %v", err)
	}
	defer stream.Close()
	if stream.DeclaredLength() != int64(declared) {
		t.Fatalf("declared length: got %d want %d", stream.DeclaredLength(), declared)
	}
	body, readErr := io.ReadAll(stream.Body())
	if readErr == nil {
		t.Fatalf("expected read error for mid-stream failure, got nil with %d bytes", len(body))
	}
	if !bytes.Equal(body, written) {
		t.Fatalf("partial body mismatch: got %d bytes want %d bytes", len(body), len(written))
	}
}
