package applicationclient

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	maxSSELineBytes  = 16 * 1024
	maxSSEFrameBytes = 256 * 1024
	maxSSEDataBytes  = 128 * 1024
)

type ActivityFrame struct {
	Event string
	ID    string
	Data  []byte
}

type InstanceMismatch struct {
	Actual string
}

func (mismatch *InstanceMismatch) Error() string {
	return "activity response instance does not match the requested instance"
}

type ActivityStream struct {
	response      *http.Response
	reader        *bufio.Reader
	instanceID    string
	afterCursor   string
	handshakeSeen bool
	closeHooks    []func()
	closed        bool
	mu            sync.Mutex
}

func (client *Client) OpenActivity(parent context.Context, instanceID, afterCursor string, credential Credential) (*ActivityStream, error) {
	if credential == nil {
		return nil, newFailure(FailureAuthentication, "", nil)
	}
	endpoint := client.address.ActivityEndpoint(instanceID, afterCursor)
	request, err := http.NewRequestWithContext(parent, http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, protocolFailure()
	}
	request.Header.Set("Accept", "text/event-stream")
	request.Header.Set("Accept-Encoding", "identity")
	request.Header.Set("Cache-Control", "no-store")
	if request.Header.Get("Last-Event-ID") != "" {
		return nil, protocolFailure()
	}
	if err := credential.Apply(request); err != nil {
		return nil, newFailure(FailureAuthentication, "", nil)
	}
	if len(request.Header.Values(APIKeyHeader)) != 1 {
		return nil, newFailure(FailureAuthentication, "", nil)
	}
	response, err := client.http.Do(request)
	if err != nil {
		if errors.Is(err, context.Canceled) {
			return nil, context.Canceled
		}
		return nil, classifyTransport(err)
	}
	if response.StatusCode >= 300 && response.StatusCode < 400 {
		response.Body.Close()
		return nil, newFailure(FailureUnavailable, CategoryRedirect, nil)
	}
	if encoding := response.Header.Get("Content-Encoding"); encoding != "" && !strings.EqualFold(encoding, "identity") {
		response.Body.Close()
		return nil, protocolFailure()
	}
	if response.StatusCode != http.StatusOK {
		body, readErr := readBounded(response.Body, problemMaxBytes)
		response.Body.Close()
		if readErr != nil {
			return nil, protocolFailure()
		}
		return nil, mapProblem(response.StatusCode, response.Header.Get("Content-Type"), body)
	}
	contentType := response.Header.Get("Content-Type")
	if !strings.HasPrefix(strings.ToLower(contentType), "text/event-stream") {
		response.Body.Close()
		return nil, protocolFailure()
	}
	headerInstanceID, err := responseInstanceID(response.Header.Values(InstanceIDHeader))
	if err != nil {
		response.Body.Close()
		return nil, protocolFailure()
	}
	if headerInstanceID != instanceID {
		response.Body.Close()
		return nil, &InstanceMismatch{Actual: headerInstanceID}
	}
	return &ActivityStream{
		response:    response,
		reader:      bufio.NewReaderSize(response.Body, 4096),
		instanceID:  instanceID,
		afterCursor: afterCursor,
	}, nil
}

func (stream *ActivityStream) InstanceID() string {
	return stream.instanceID
}

func (stream *ActivityStream) Next() (ActivityFrame, error) {
	stream.mu.Lock()
	if stream.closed {
		stream.mu.Unlock()
		return ActivityFrame{}, fmt.Errorf("activity stream is closed")
	}
	handshakeSeen := stream.handshakeSeen
	stream.mu.Unlock()
	var lines []string
	var frameSize int
	for {
		line, err := stream.readBoundedLine()
		if err != nil {
			if len(line) == 0 && len(lines) == 0 && errors.Is(err, io.EOF) {
				return ActivityFrame{}, io.EOF
			}
			if errors.Is(err, context.Canceled) {
				return ActivityFrame{}, context.Canceled
			}
			return ActivityFrame{}, protocolFailure()
		}
		frameSize += len(line)
		if frameSize > maxSSEFrameBytes {
			return ActivityFrame{}, protocolFailure()
		}
		trimmed := strings.TrimRight(string(line), "\r\n")
		if trimmed == "" {
			if len(lines) == 0 {
				continue
			}
			frame, parseErr := parseActivityFrame(lines, handshakeSeen)
			if parseErr != nil {
				return ActivityFrame{}, parseErr
			}
			if frame.Event == "handshake" {
				if err := validateHandshakeData(stream.instanceID, stream.afterCursor, frame.Data); err != nil {
					return ActivityFrame{}, err
				}
				stream.mu.Lock()
				if stream.closed || stream.handshakeSeen {
					stream.mu.Unlock()
					return ActivityFrame{}, protocolFailure()
				}
				stream.handshakeSeen = true
				stream.mu.Unlock()
			}
			if frame.Event == "activity" {
				if err := validateActivityData(stream.instanceID, frame.ID, frame.Data); err != nil {
					return ActivityFrame{}, err
				}
			}
			return frame, nil
		}
		if len(trimmed) > maxSSELineBytes {
			return ActivityFrame{}, protocolFailure()
		}
		lines = append(lines, trimmed)
	}
}

func (stream *ActivityStream) readBoundedLine() ([]byte, error) {
	line := make([]byte, 0, 4096)
	for {
		fragment, err := stream.reader.ReadSlice('\n')
		if len(line)+len(fragment) > maxSSELineBytes+2 {
			return nil, protocolFailure()
		}
		line = append(line, fragment...)
		if errors.Is(err, bufio.ErrBufferFull) {
			continue
		}
		return line, err
	}
}

// AddCloseHook registers lifecycle cleanup that runs exactly once when the
// stream closes. If the stream is already closed, the hook runs immediately.
func (stream *ActivityStream) AddCloseHook(hook func()) {
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

func (stream *ActivityStream) Close() error {
	stream.mu.Lock()
	if stream.closed {
		stream.mu.Unlock()
		return nil
	}
	stream.closed = true
	body := stream.response.Body
	hooks := stream.closeHooks
	stream.closeHooks = nil
	stream.mu.Unlock()
	for _, hook := range hooks {
		hook()
	}
	return body.Close()
}

func parseActivityFrame(lines []string, handshakeSeen bool) (ActivityFrame, error) {
	var event, id string
	var data []byte
	var dataCount, idCount, eventCount int
	for _, line := range lines {
		colon := strings.IndexByte(line, ':')
		if colon < 0 {
			return ActivityFrame{}, protocolFailure()
		}
		field := line[:colon]
		value := line[colon+1:]
		if len(value) > 0 && value[0] == ' ' {
			value = value[1:]
		}
		switch field {
		case "event":
			event = value
			eventCount++
		case "id":
			id = value
			idCount++
		case "data":
			data = []byte(value)
			dataCount++
		default:
			return ActivityFrame{}, protocolFailure()
		}
	}
	if eventCount != 1 {
		return ActivityFrame{}, protocolFailure()
	}
	if idCount > 1 || dataCount > 1 {
		return ActivityFrame{}, protocolFailure()
	}
	if event != "handshake" && event != "activity" {
		return ActivityFrame{}, protocolFailure()
	}
	if event == "handshake" {
		if handshakeSeen {
			return ActivityFrame{}, protocolFailure()
		}
		if id != "" {
			return ActivityFrame{}, protocolFailure()
		}
		if dataCount == 0 || len(data) == 0 {
			return ActivityFrame{}, protocolFailure()
		}
	} else {
		if !handshakeSeen {
			return ActivityFrame{}, protocolFailure()
		}
		if id == "" {
			return ActivityFrame{}, protocolFailure()
		}
		if dataCount == 0 || len(data) == 0 {
			return ActivityFrame{}, protocolFailure()
		}
	}
	if len(data) > maxSSEDataBytes {
		return ActivityFrame{}, protocolFailure()
	}
	if !json.Valid(data) {
		return ActivityFrame{}, protocolFailure()
	}
	return ActivityFrame{Event: event, ID: id, Data: data}, nil
}

func validateHandshakeData(instanceID, afterCursor string, data []byte) error {
	var handshake struct {
		InstanceID  string `json:"instanceId"`
		ObservedAt  string `json:"observedAt"`
		AfterCursor string `json:"afterCursor"`
	}
	if err := json.Unmarshal(data, &handshake); err != nil {
		return protocolFailure()
	}
	if _, err := parseUUID(handshake.InstanceID); err != nil {
		return protocolFailure()
	}
	if handshake.InstanceID != instanceID {
		return &InstanceMismatch{Actual: handshake.InstanceID}
	}
	if handshake.AfterCursor != afterCursor {
		return protocolFailure()
	}
	observedAt, err := time.Parse(time.RFC3339Nano, handshake.ObservedAt)
	if err != nil || observedAt.IsZero() {
		return protocolFailure()
	}
	cursor, err := strconv.ParseUint(handshake.AfterCursor, 10, 64)
	if err != nil || strconv.FormatUint(cursor, 10) != handshake.AfterCursor {
		return protocolFailure()
	}
	return nil
}

func validateActivityData(instanceID, id string, data []byte) error {
	var envelope struct {
		InstanceID string `json:"instanceId"`
		Cursor     string `json:"cursor"`
	}
	if err := json.Unmarshal(data, &envelope); err != nil {
		return protocolFailure()
	}
	if _, err := parseUUID(envelope.InstanceID); err != nil {
		return protocolFailure()
	}
	if envelope.InstanceID != instanceID {
		return &InstanceMismatch{Actual: envelope.InstanceID}
	}
	if envelope.Cursor != id {
		return protocolFailure()
	}
	return nil
}
