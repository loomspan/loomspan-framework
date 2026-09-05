package applicationclient

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

const (
	testInstanceID = "11111111-1111-4111-8111-111111111111"
	handshakeFrame = "event: handshake\ndata: {\"instanceId\":\"" + testInstanceID + "\",\"observedAt\":\"2026-07-25T12:00:00Z\",\"afterCursor\":\"0\"}\n\n"
)

func activityFrame(id, cursor, kind string) string {
	return fmt.Sprintf("id: %s\nevent: activity\ndata: {\"instanceId\":\"%s\",\"cursor\":\"%s\",\"sessionId\":\"session-1\",\"traceId\":\"trace-1\",\"canonicalSequence\":%s,\"timestamp\":\"2026-07-25T12:00:00Z\",\"kind\":\"%s\",\"executionStatus\":\"ACTIVE\",\"summary\":\"test\",\"details\":{}}\n\n",
		id, testInstanceID, cursor, cursor, kind)
}

func sseServer(handler http.HandlerFunc) (*httptest.Server, *Client, Address) {
	server := httptest.NewServer(handler)
	address, _ := NormalizeAddress(server.URL)
	client, _ := New(address, testPolicy(), "0.1.0-SNAPSHOT")
	return server, client, address
}

func TestOpenActivitySendsRequiredHeaders(t *testing.T) {
	var received http.Header
	server, client, address := sseServer(func(response http.ResponseWriter, request *http.Request) {
		received = request.Header.Clone()
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Type", "text/event-stream")
		response.Header().Set("Cache-Control", "no-store")
		_, _ = response.Write([]byte(handshakeFrame))
	})
	defer server.Close()
	defer client.Close()
	stream, err := client.OpenActivity(context.Background(), testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatal(err)
	}
	defer stream.Close()
	if received.Get("Accept") != "text/event-stream" {
		t.Fatalf("unexpected Accept: %q", received.Get("Accept"))
	}
	if received.Get("Accept-Encoding") != "identity" {
		t.Fatalf("unexpected Accept-Encoding: %q", received.Get("Accept-Encoding"))
	}
	if received.Get("Cache-Control") != "no-store" {
		t.Fatalf("unexpected Cache-Control: %q", received.Get("Cache-Control"))
	}
	if received.Get("Last-Event-ID") != "" {
		t.Fatal("Last-Event-ID should not be set")
	}
	if values := received.Values(APIKeyHeader); len(values) != 1 || values[0] != strings.Repeat("k", 32) {
		t.Fatalf("unexpected API key headers: %#v", values)
	}
	_ = address
}

func TestOpenActivityRejectsNonEventStreamContentType(t *testing.T) {
	server, client, _ := sseServer(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Type", "application/json")
		_, _ = response.Write([]byte(`{}`))
	})
	defer server.Close()
	defer client.Close()
	_, err := client.OpenActivity(context.Background(), testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
	failure, ok := err.(*Failure)
	if !ok || failure.Kind != FailureProtocol {
		t.Fatalf("expected protocol failure, got %v", err)
	}
}

func TestOpenActivityRejectsInstanceIDMismatch(t *testing.T) {
	server, client, _ := sseServer(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, "22222222-2222-4222-8222-222222222222")
		response.Header().Set("Content-Type", "text/event-stream")
		_, _ = response.Write([]byte(handshakeFrame))
	})
	defer server.Close()
	defer client.Close()
	_, err := client.OpenActivity(context.Background(), testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
	var mismatch *InstanceMismatch
	if !errors.As(err, &mismatch) || mismatch.Actual == "" {
		t.Fatalf("expected typed instance mismatch, got %v", err)
	}
}

func TestOpenActivityRejectsMissingInstanceIDHeader(t *testing.T) {
	server, client, _ := sseServer(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set("Content-Type", "text/event-stream")
		_, _ = response.Write([]byte(handshakeFrame))
	})
	defer server.Close()
	defer client.Close()
	_, err := client.OpenActivity(context.Background(), testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
	failure, ok := err.(*Failure)
	if !ok || failure.Kind != FailureProtocol {
		t.Fatalf("expected protocol failure for missing instance ID, got %v", err)
	}
}

func TestOpenActivityMapsProblemResponsesToFailures(t *testing.T) {
	tests := []struct {
		code string
		kind FailureKind
	}{
		{"INVALID_REQUEST", FailureInvalidArgument},
		{"INVALID_CURSOR", FailureInvalidCursor},
		{"STALE_CURSOR", FailureStaleCursor},
		{"LIVE_MONITORING_UNAVAILABLE", FailureLiveMonitoringUnavailable},
		{"LIMIT_EXCEEDED", FailureLimitExceeded},
	}
	for _, test := range tests {
		server, client, _ := sseServer(func(response http.ResponseWriter, _ *http.Request) {
			response.Header().Set("Content-Type", "application/json")
			response.WriteHeader(http.StatusBadRequest)
			_, _ = response.Write([]byte(`{"status":400,"code":"` + test.code + `","message":"test"}`))
		})
		_, err := client.OpenActivity(context.Background(), testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
		client.Close()
		server.Close()
		failure, ok := err.(*Failure)
		if !ok {
			t.Fatalf("%s did not produce a typed failure: %v", test.code, err)
		}
		if failure.Kind != test.kind {
			t.Fatalf("expected %s, got %s", test.kind, failure.Kind)
		}
	}
}

func TestOpenActivityReturnsCallerCancellation(t *testing.T) {
	started := make(chan struct{})
	server, client, _ := sseServer(func(_ http.ResponseWriter, request *http.Request) {
		close(started)
		<-request.Context().Done()
	})
	defer server.Close()
	defer client.Close()
	parent, cancel := context.WithCancel(context.Background())
	result := make(chan error, 1)
	go func() {
		_, err := client.OpenActivity(parent, testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
		result <- err
	}()
	<-started
	cancel()
	if err := <-result; !errors.Is(err, context.Canceled) {
		t.Fatalf("expected context.Canceled, got %v", err)
	}
}

func TestActivityStreamReadsHandshakeThenActivityFrames(t *testing.T) {
	sse := handshakeFrame + activityFrame("7", "7", "TRACE_COMPLETED") + activityFrame("8", "8", "EXECUTION_OBSERVATION_ENDED")
	server, client, _ := sseServer(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Type", "text/event-stream")
		_, _ = response.Write([]byte(sse))
	})
	defer server.Close()
	defer client.Close()
	stream, err := client.OpenActivity(context.Background(), testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatal(err)
	}
	defer stream.Close()
	frame, err := stream.Next()
	if err != nil {
		t.Fatal(err)
	}
	if frame.Event != "handshake" || frame.ID != "" {
		t.Fatalf("unexpected handshake frame: event=%q id=%q", frame.Event, frame.ID)
	}
	frame, err = stream.Next()
	if err != nil {
		t.Fatal(err)
	}
	if frame.Event != "activity" || frame.ID != "7" {
		t.Fatalf("unexpected first activity frame: event=%q id=%q", frame.Event, frame.ID)
	}
	frame, err = stream.Next()
	if err != nil {
		t.Fatal(err)
	}
	if frame.Event != "activity" || frame.ID != "8" {
		t.Fatalf("unexpected second activity frame: event=%q id=%q", frame.Event, frame.ID)
	}
}

func TestOpenActivityConsumesCanonicalReplayFixture(t *testing.T) {
	fixture, err := os.ReadFile(filepath.Join("..", "..", "..", "loomspan-console-fixtures", "application-sse", "replay.sse"))
	if err != nil {
		t.Fatal(err)
	}
	server, client, _ := sseServer(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Type", "text/event-stream; charset=UTF-8")
		_, _ = response.Write(fixture)
	})
	defer server.Close()
	defer client.Close()
	stream, err := client.OpenActivity(context.Background(), testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatal(err)
	}
	defer stream.Close()
	handshake, err := stream.Next()
	if err != nil || handshake.Event != "handshake" {
		t.Fatalf("canonical handshake: frame=%#v err=%v", handshake, err)
	}
	first, err := stream.Next()
	if err != nil || first.ID != "7" {
		t.Fatalf("canonical first activity: frame=%#v err=%v", first, err)
	}
	second, err := stream.Next()
	if err != nil || second.ID != "8" {
		t.Fatalf("canonical second activity: frame=%#v err=%v", second, err)
	}
}

func TestActivityStreamRejectsActivityBeforeHandshake(t *testing.T) {
	sse := activityFrame("7", "7", "TRACE_COMPLETED")
	server, client, _ := sseServer(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Type", "text/event-stream")
		_, _ = response.Write([]byte(sse))
	})
	defer server.Close()
	defer client.Close()
	stream, err := client.OpenActivity(context.Background(), testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatal(err)
	}
	defer stream.Close()
	_, err = stream.Next()
	failure, ok := err.(*Failure)
	if !ok || failure.Kind != FailureProtocol {
		t.Fatalf("expected protocol failure for activity before handshake, got %v", err)
	}
}

func TestActivityStreamRejectsDuplicateHandshake(t *testing.T) {
	sse := handshakeFrame + handshakeFrame
	server, client, _ := sseServer(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Type", "text/event-stream")
		_, _ = response.Write([]byte(sse))
	})
	defer server.Close()
	defer client.Close()
	stream, err := client.OpenActivity(context.Background(), testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatal(err)
	}
	defer stream.Close()
	_, err = stream.Next()
	if err != nil {
		t.Fatal(err)
	}
	_, err = stream.Next()
	failure, ok := err.(*Failure)
	if !ok || failure.Kind != FailureProtocol {
		t.Fatalf("expected protocol failure for duplicate handshake, got %v", err)
	}
}

func TestActivityStreamRejectsCursorIDMismatch(t *testing.T) {
	sse := handshakeFrame + "id: 7\nevent: activity\ndata: {\"instanceId\":\"" + testInstanceID + "\",\"cursor\":\"8\",\"sessionId\":\"session-1\",\"traceId\":\"trace-1\",\"canonicalSequence\":8,\"timestamp\":\"2026-07-25T12:00:00Z\",\"kind\":\"TRACE_COMPLETED\",\"executionStatus\":\"ACTIVE\",\"summary\":\"test\",\"details\":{}}\n\n"
	server, client, _ := sseServer(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Type", "text/event-stream")
		_, _ = response.Write([]byte(sse))
	})
	defer server.Close()
	defer client.Close()
	stream, err := client.OpenActivity(context.Background(), testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatal(err)
	}
	defer stream.Close()
	_, err = stream.Next()
	if err != nil {
		t.Fatal(err)
	}
	_, err = stream.Next()
	failure, ok := err.(*Failure)
	if !ok || failure.Kind != FailureProtocol {
		t.Fatalf("expected protocol failure for cursor/id mismatch, got %v", err)
	}
}

func TestActivityStreamRejectsInvalidJSONData(t *testing.T) {
	sse := handshakeFrame + "id: 7\nevent: activity\ndata: not-json\n\n"
	server, client, _ := sseServer(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Type", "text/event-stream")
		_, _ = response.Write([]byte(sse))
	})
	defer server.Close()
	defer client.Close()
	stream, err := client.OpenActivity(context.Background(), testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatal(err)
	}
	defer stream.Close()
	_, err = stream.Next()
	if err != nil {
		t.Fatal(err)
	}
	_, err = stream.Next()
	failure, ok := err.(*Failure)
	if !ok || failure.Kind != FailureProtocol {
		t.Fatalf("expected protocol failure for invalid JSON, got %v", err)
	}
}

func TestActivityStreamRejectsUnknownEventType(t *testing.T) {
	sse := handshakeFrame + "id: 7\nevent: unknown\ndata: {\"cursor\":\"7\"}\n\n"
	server, client, _ := sseServer(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Type", "text/event-stream")
		_, _ = response.Write([]byte(sse))
	})
	defer server.Close()
	defer client.Close()
	stream, err := client.OpenActivity(context.Background(), testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatal(err)
	}
	defer stream.Close()
	_, err = stream.Next()
	if err != nil {
		t.Fatal(err)
	}
	_, err = stream.Next()
	failure, ok := err.(*Failure)
	if !ok || failure.Kind != FailureProtocol {
		t.Fatalf("expected protocol failure for unknown event type, got %v", err)
	}
}

func TestActivityStreamRejectsActivityWithoutID(t *testing.T) {
	sse := handshakeFrame + "event: activity\ndata: {\"cursor\":\"7\"}\n\n"
	server, client, _ := sseServer(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Type", "text/event-stream")
		_, _ = response.Write([]byte(sse))
	})
	defer server.Close()
	defer client.Close()
	stream, err := client.OpenActivity(context.Background(), testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatal(err)
	}
	defer stream.Close()
	_, err = stream.Next()
	if err != nil {
		t.Fatal(err)
	}
	_, err = stream.Next()
	failure, ok := err.(*Failure)
	if !ok || failure.Kind != FailureProtocol {
		t.Fatalf("expected protocol failure for activity without id, got %v", err)
	}
}

func TestActivityStreamReturnsEOFAtEndOfStream(t *testing.T) {
	sse := handshakeFrame
	server, client, _ := sseServer(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Type", "text/event-stream")
		_, _ = response.Write([]byte(sse))
	})
	defer server.Close()
	defer client.Close()
	stream, err := client.OpenActivity(context.Background(), testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatal(err)
	}
	defer stream.Close()
	_, err = stream.Next()
	if err != nil {
		t.Fatal(err)
	}
	_, err = stream.Next()
	if err != io.EOF {
		t.Fatalf("expected EOF, got %v", err)
	}
}

func TestActivityStreamClosesCleanly(t *testing.T) {
	server, client, _ := sseServer(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Type", "text/event-stream")
		_, _ = response.Write([]byte(handshakeFrame))
		time.Sleep(100 * time.Millisecond)
	})
	defer server.Close()
	defer client.Close()
	stream, err := client.OpenActivity(context.Background(), testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatal(err)
	}
	if err := stream.Close(); err != nil {
		t.Fatalf("close returned error: %v", err)
	}
	if err := stream.Close(); err != nil {
		t.Fatalf("double close returned error: %v", err)
	}
	_, err = stream.Next()
	if err == nil {
		t.Fatal("expected error reading from closed stream")
	}
}

func TestActivityStreamCloseInterruptsBlockedNext(t *testing.T) {
	connected := make(chan struct{})
	server, client, _ := sseServer(func(response http.ResponseWriter, request *http.Request) {
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Type", "text/event-stream")
		response.WriteHeader(http.StatusOK)
		if flusher, ok := response.(http.Flusher); ok {
			flusher.Flush()
		}
		close(connected)
		<-request.Context().Done()
	})
	defer server.Close()
	defer client.Close()
	stream, err := client.OpenActivity(context.Background(), testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatal(err)
	}
	<-connected
	nextDone := make(chan error, 1)
	go func() {
		_, nextErr := stream.Next()
		nextDone <- nextErr
	}()
	if err := stream.Close(); err != nil {
		t.Fatal(err)
	}
	select {
	case <-nextDone:
	case <-time.After(time.Second):
		t.Fatal("Close did not interrupt a blocked Next")
	}
}

func TestOpenActivityRejectsNilCredential(t *testing.T) {
	server, client, _ := sseServer(func(http.ResponseWriter, *http.Request) {})
	defer server.Close()
	defer client.Close()
	_, err := client.OpenActivity(context.Background(), testInstanceID, "0", nil)
	failure, ok := err.(*Failure)
	if !ok || failure.Kind != FailureAuthentication {
		t.Fatalf("expected authentication failure, got %v", err)
	}
}

func TestOpenActivityRejectsRedirect(t *testing.T) {
	server, client, _ := sseServer(func(response http.ResponseWriter, _ *http.Request) {
		http.Redirect(response, &http.Request{}, "http://elsewhere.test", http.StatusFound)
	})
	defer server.Close()
	defer client.Close()
	_, err := client.OpenActivity(context.Background(), testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
	failure, ok := err.(*Failure)
	if !ok || failure.Kind != FailureUnavailable {
		t.Fatalf("expected unavailable failure for redirect, got %v", err)
	}
}

func TestOpenActivityRejectsEncodedResponse(t *testing.T) {
	server, client, _ := sseServer(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Encoding", "gzip")
		response.Header().Set("Content-Type", "text/event-stream")
		_, _ = response.Write([]byte(handshakeFrame))
	})
	defer server.Close()
	defer client.Close()
	_, err := client.OpenActivity(context.Background(), testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
	failure, ok := err.(*Failure)
	if !ok || failure.Kind != FailureProtocol {
		t.Fatalf("expected protocol failure for encoded response, got %v", err)
	}
}

func TestActivityStreamRejectsContradictoryHandshake(t *testing.T) {
	tests := map[string]string{
		"instance": `{"instanceId":"22222222-2222-4222-8222-222222222222","observedAt":"2026-07-25T12:00:00Z","afterCursor":"0"}`,
		"cursor":   `{"instanceId":"11111111-1111-4111-8111-111111111111","observedAt":"2026-07-25T12:00:00Z","afterCursor":"1"}`,
		"time":     `{"instanceId":"11111111-1111-4111-8111-111111111111","observedAt":"invalid","afterCursor":"0"}`,
	}
	for name, data := range tests {
		t.Run(name, func(t *testing.T) {
			server, client, _ := sseServer(func(response http.ResponseWriter, _ *http.Request) {
				response.Header().Set(InstanceIDHeader, testInstanceID)
				response.Header().Set("Content-Type", "text/event-stream")
				_, _ = response.Write([]byte("event: handshake\ndata: " + data + "\n\n"))
			})
			defer server.Close()
			defer client.Close()
			stream, err := client.OpenActivity(context.Background(), testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
			if err != nil {
				t.Fatal(err)
			}
			defer stream.Close()
			if _, err := stream.Next(); err == nil {
				t.Fatal("contradictory handshake was accepted")
			}
		})
	}
}

func TestActivityStreamRejectsPayloadFromDifferentInstance(t *testing.T) {
	other := "22222222-2222-4222-8222-222222222222"
	sse := handshakeFrame +
		"id: 1\nevent: activity\ndata: {\"instanceId\":\"" + other + "\",\"cursor\":\"1\"}\n\n"
	server, client, _ := sseServer(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Type", "text/event-stream")
		_, _ = response.Write([]byte(sse))
	})
	defer server.Close()
	defer client.Close()
	stream, err := client.OpenActivity(context.Background(), testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatal(err)
	}
	defer stream.Close()
	if _, err := stream.Next(); err != nil {
		t.Fatal(err)
	}
	if _, err := stream.Next(); err == nil {
		t.Fatal("activity from a different instance was accepted")
	}
}

func TestActivityStreamRejectsOversizedUnterminatedLine(t *testing.T) {
	server, client, _ := sseServer(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, testInstanceID)
		response.Header().Set("Content-Type", "text/event-stream")
		_, _ = response.Write([]byte(strings.Repeat("x", maxSSELineBytes+3)))
	})
	defer server.Close()
	defer client.Close()
	stream, err := client.OpenActivity(context.Background(), testInstanceID, "0", testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatal(err)
	}
	defer stream.Close()
	if _, err := stream.Next(); err == nil {
		t.Fatal("oversized unterminated line was accepted")
	}
}
