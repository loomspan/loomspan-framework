package mcpadapter

import (
	"context"
	"fmt"
	"time"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
	"github.com/modelcontextprotocol/go-sdk/mcp"
)

const RuntimeToolName = "LOOMSPAN_get_runtime"

type StatusProvider func() consolecore.StatusSnapshot

type emptyInput struct{}

type RuntimeOutput struct {
	ObservedAt           time.Time                   `json:"observedAt"`
	TargetSelection      consolecore.Selection       `json:"targetSelection"`
	TargetConnection     consolecore.Connection      `json:"targetConnection"`
	TargetAuthentication consolecore.Authentication  `json:"targetAuthentication"`
	JavaGoCompatibility  consolecore.Compatibility   `json:"javaGoCompatibility"`
	RuntimeIdentity      consolecore.RuntimeIdentity `json:"runtimeIdentity"`
	LiveMonitoring       consolecore.LiveMonitoring  `json:"liveMonitoring"`
}

func addRuntimeTool(server *mcp.Server, provider StatusProvider, credentials authenticator) {
	addValidatedTool(server, &mcp.Tool{
		Name: RuntimeToolName, Description: "Return current target, compatibility, identity, and monitoring status.",
		Annotations: readOnlyAnnotations,
	}, runtimeOutputSchema(),
		func(ctx context.Context, _ *mcp.CallToolRequest, _ emptyInput) (*mcp.CallToolResult, RuntimeOutput, error) {
			output, err := buildRuntimeOutput(ctx, provider, credentials)
			if err != nil {
				return nil, RuntimeOutput{}, err
			}
			return &mcp.CallToolResult{Content: []mcp.Content{&mcp.TextContent{Text: runtimeText(output)}}}, output, nil
		})
}

func buildRuntimeOutput(ctx context.Context, provider StatusProvider, credentials authenticator) (RuntimeOutput, error) {
	status := provider()
	if err := status.Validate(); err != nil {
		return RuntimeOutput{}, fmt.Errorf("INTERNAL: runtime status is unavailable")
	}
	if generation, ok := admittedGeneration(ctx); ok && credentials.Snapshot().Generation != generation {
		return RuntimeOutput{}, diagnostics.Annotate(fmt.Errorf("INTERNAL: MCP authentication generation changed"), diagnostics.Facts{Expected: true, Cause: "authentication_generation"})
	}
	return RuntimeOutput{ObservedAt: status.ObservedAt, TargetSelection: status.TargetSelection, TargetConnection: status.TargetConnection, TargetAuthentication: status.TargetAuthentication, JavaGoCompatibility: status.JavaGoCompatibility, RuntimeIdentity: status.RuntimeIdentity, LiveMonitoring: status.LiveMonitoring}, nil
}

func runtimeText(output RuntimeOutput) string {
	var writer lineWriter
	writer.quoted("targetSelection", string(output.TargetSelection))
	writer.quoted("targetConnection", string(output.TargetConnection))
	writer.quoted("targetAuthentication", string(output.TargetAuthentication))
	writer.quoted("javaGoCompatibility", string(output.JavaGoCompatibility))
	writer.quoted("runtimeIdentity", string(output.RuntimeIdentity))
	writer.quoted("liveMonitoring", string(output.LiveMonitoring))
	writer.time("observedAt", output.ObservedAt)
	return writer.String()
}
