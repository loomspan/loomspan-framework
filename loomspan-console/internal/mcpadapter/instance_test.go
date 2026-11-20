package mcpadapter

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
	"github.com/modelcontextprotocol/go-sdk/mcp"
)

func instanceWire(version any, generation string) []byte {
	encoded, _ := json.Marshal(map[string]any{
		"instanceId": mcpTestInstanceID, "consoleCompatibilityVersion": "1.0.0-beta.10-SNAPSHOT",
		"observedAt": "2026-08-13T20:30:00.000000123Z", "liveMonitoringAvailable": true,
		"registeredSkillCount": 0, "activeExecutionCount": 0, "catalogedTraceCount": 0,
		"tracePersistencePolicy": "PERSISTENT", "completionGraceTtl": "PT2M", "traceCatalogMetadataTtl": "PT168H",
		"namespaces": []any{map[string]any{"namespace": "empty", "generationId": generation, "publicationVersion": version}},
	})
	return encoded
}

func TestInstanceReadPreservesExactLabelsAndOmissionBeforeExecution(t *testing.T) {
	for _, label := range []any{nil, "", "  P7 ☃\n<b>  "} {
		t.Run(fmt.Sprintf("%q", label), func(t *testing.T) {
			options := newMCPTestOptions(t, func(endpoint string) ([]byte, error) {
				if !strings.HasSuffix(endpoint, "/instance") {
					t.Fatalf("unexpected %s", endpoint)
				}
				return instanceWire(label, "g7"), nil
			})
			result, envelope, err := handleGetInstance(context.Background(), options)
			if err != nil || result.IsError || envelope.Result == nil {
				t.Fatalf("result=%#v envelope=%#v err=%v", result, envelope, err)
			}
			namespace := envelope.Result.Namespaces[0]
			if namespace.GenerationID != "g7" || envelope.Result.InstanceID != mcpTestInstanceID {
				t.Fatalf("wrong facts %#v", envelope.Result)
			}
			if label == nil {
				if namespace.PublicationVersion != nil || !strings.Contains(result.Content[0].(*mcp.TextContent).Text, "publicationVersion: null") {
					t.Fatal("omission lost")
				}
			} else if namespace.PublicationVersion == nil || *namespace.PublicationVersion != label.(string) {
				t.Fatalf("label lost %#v", namespace)
			}
			encodedLabel, _ := json.Marshal(label)
			if !containsLine(result.Content[0].(*mcp.TextContent).Text, "namespaces[0].publicationVersion: "+string(encodedLabel)) {
				t.Fatal("text and structured publication labels disagree")
			}
			encoded, _ := json.Marshal(envelope)
			if !strings.Contains(string(encoded), `"observedAt":"2026-08-13T20:30:00.000000123Z"`) {
				t.Fatalf("upstream observation time lost: %s", encoded)
			}
			validateTypedEnvelope(t, "instance", instanceOutputSchema(), *envelope.Result)
		})
	}
}

func TestInstanceSDKFreshSessionsObservePublicationAndRejectUnknownInput(t *testing.T) {
	label, generation := "P7", "g7"
	options := newMCPTestOptions(t, func(string) ([]byte, error) { return instanceWire(label, generation), nil })
	server := NewServer(options)
	endpoint := httptest.NewServer(server.Handler())
	defer endpoint.Close()
	connect := func() *mcp.ClientSession {
		client := mcp.NewClient(&mcp.Implementation{Name: "test", Version: "1"}, nil)
		transport := &http.Client{Transport: authTransport{base: http.DefaultTransport, host: "127.0.0.1:7345", key: "mcp-secret"}}
		session, err := client.Connect(context.Background(), &mcp.StreamableClientTransport{Endpoint: endpoint.URL, HTTPClient: transport, DisableStandaloneSSE: true}, nil)
		if err != nil {
			t.Fatal(err)
		}
		t.Cleanup(func() { _ = session.Close() })
		return session
	}
	for range 2 {
		session := connect()
		result, err := session.CallTool(context.Background(), &mcp.CallToolParams{Name: GetInstanceToolName, Arguments: map[string]any{}})
		if err != nil || result.IsError {
			t.Fatalf("call %v %v", result, err)
		}
		encoded, _ := json.Marshal(result.StructuredContent)
		if !strings.Contains(string(encoded), `"publicationVersion":"`+label+`"`) || !strings.Contains(string(encoded), `"generationId":"`+generation+`"`) {
			t.Fatalf("stale facts %s", encoded)
		}
		label, generation = "P8", "g8"
	}
	invalid, err := connect().CallTool(context.Background(), &mcp.CallToolParams{Name: GetInstanceToolName, Arguments: map[string]any{"namespace": "empty"}})
	if err == nil && !invalid.IsError {
		t.Fatal("accepted unknown argument")
	}
}

func TestInstanceReadRejectsOversizedInventoryAndTargetChange(t *testing.T) {
	options := newMCPTestOptions(t, func(string) ([]byte, error) {
		return instanceWire(strings.Repeat("p", defaultTraceResultBudget), "g"), nil
	})
	result, envelope, err := handleGetInstance(context.Background(), options)
	if err != nil || !result.IsError || envelope.Error == nil || envelope.Error.Code != consolecore.CodeLimitExceeded {
		t.Fatalf("oversized result %#v %#v %v", result, envelope, err)
	}
	options = newMCPTestOptions(t, func(string) ([]byte, error) { return []byte(`{"namespaces":null}`), nil })
	result, envelope, err = handleGetInstance(context.Background(), options)
	if err != nil || !result.IsError || envelope.Error == nil {
		t.Fatalf("invalid result %#v %#v %v", result, envelope, err)
	}
	options.Target = nil
	result, envelope, err = handleGetInstance(context.Background(), options)
	if err != nil || !result.IsError || envelope.Error == nil {
		t.Fatalf("missing target %#v %#v %v", result, envelope, err)
	}
}

func TestInstanceReadDropsOldTargetAndOldAuthentication(t *testing.T) {
	started := make(chan struct{})
	client := &mcpTestTargetClient{getContext: func(ctx context.Context, _ string) ([]byte, error) {
		close(started)
		<-ctx.Done()
		return nil, ctx.Err()
	}}
	options := newMCPTestOptionsWithClient(t, client)
	completed := make(chan toolEnvelope[instanceResult], 1)
	go func() {
		_, envelope, _ := handleGetInstance(context.Background(), options)
		completed <- envelope
	}()
	<-started
	if err := options.Target.Select("http://127.0.0.1:8081"); err != nil {
		t.Fatal(err)
	}
	select {
	case envelope := <-completed:
		if envelope.Result != nil || envelope.Error == nil || envelope.Error.Code != consolecore.CodeTargetChanged {
			t.Fatalf("old target result %#v", envelope)
		}
	case <-time.After(time.Second):
		t.Fatal("old target request did not finish")
	}
	options = newMCPTestOptions(t, func(string) ([]byte, error) { return instanceWire("P7", "g7"), nil })
	ctx := context.WithValue(context.Background(), generationKey{}, uint64(2))
	_, envelope, err := handleGetInstance(ctx, options)
	if err == nil || envelope.Result != nil {
		t.Fatalf("old authentication returned result %#v %v", envelope, err)
	}
}

func TestInstanceReadReturnsCompleteEmptyInventory(t *testing.T) {
	var wire map[string]any
	if err := json.Unmarshal(instanceWire(nil, "g7"), &wire); err != nil {
		t.Fatal(err)
	}
	wire["namespaces"] = []any{}
	encoded, err := json.Marshal(wire)
	if err != nil {
		t.Fatal(err)
	}
	options := newMCPTestOptions(t, func(string) ([]byte, error) { return encoded, nil })
	result, envelope, err := handleGetInstance(context.Background(), options)
	if err != nil || result.IsError || envelope.Result == nil || envelope.Result.Namespaces == nil || len(envelope.Result.Namespaces) != 0 {
		t.Fatalf("result=%#v envelope=%#v err=%v", result, envelope, err)
	}
	if !containsLine(result.Content[0].(*mcp.TextContent).Text, "namespaceCount: 0") {
		t.Fatalf("text disagrees: %#v", result.Content)
	}
}
