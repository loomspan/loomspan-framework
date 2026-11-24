package mcpadapter

import (
	"context"
	"fmt"
	"time"

	"github.com/google/jsonschema-go/jsonschema"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/observability"
	"github.com/modelcontextprotocol/go-sdk/mcp"
)

const GetInstanceToolName = "LOOMSPAN_get_instance"

type instanceResult struct {
	ObservedAt time.Time                            `json:"observedAt"`
	InstanceID string                               `json:"instanceId"`
	Namespaces []observability.NamespacePublication `json:"namespaces"`
}

func addInstanceTool(server *mcp.Server, options ServerOptions) {
	addValidatedTool(server, &mcp.Tool{
		Name:        GetInstanceToolName,
		Description: "Read instance facts.",
		Annotations: readOnlyAnnotations,
	}, instanceOutputSchema(), func(ctx context.Context, _ *mcp.CallToolRequest, _ emptyInput) (*mcp.CallToolResult, toolEnvelope[instanceResult], error) {
		return handleGetInstance(ctx, options)
	})
}

func handleGetInstance(ctx context.Context, options ServerOptions) (*mcp.CallToolResult, toolEnvelope[instanceResult], error) {
	scope, domain := captureScope(options)
	if domain != nil {
		return checkedDomainFailure[instanceResult](ctx, options, domain)
	}
	ctx = diagnostics.WithScope(ctx, string(scope.ID))
	if options.Observability == nil {
		return checkedDomainFailure[instanceResult](ctx, options, unavailableInspectionError(string(scope.ID)))
	}
	status, domain := options.Observability.GetInstance(ctx, scope)
	if domain != nil {
		return checkedDomainFailure[instanceResult](ctx, options, domain)
	}
	result := instanceResult{ObservedAt: status.ObservedAt.UTC(), InstanceID: status.InstanceID, Namespaces: status.Namespaces}
	text := instanceText(result)
	if !fitsOrdinaryResponse(result, text) {
		domain := consolecore.NewError(consolecore.CodeLimitExceeded, "The complete namespace inventory exceeds the safe response budget.", string(scope.ID), consolecore.Details{LimitName: "responseBytes", LimitValue: defaultTraceResultBudget}, nil)
		return checkedDomainFailure[instanceResult](ctx, options, domain)
	}
	if domain := publicationDomain(options, scope); domain != nil {
		return checkedDomainFailure[instanceResult](ctx, options, domain)
	}
	if err := authenticationGenerationError(ctx, options); err != nil {
		return nil, toolEnvelope[instanceResult]{}, err
	}
	return successResult(result, text)
}

func instanceText(result instanceResult) string {
	var writer lineWriter
	appendCommon(&writer, result.ObservedAt)
	writer.quoted("instanceId", result.InstanceID)
	writer.integer("namespaceCount", int64(len(result.Namespaces)))
	for index, namespace := range result.Namespaces {
		prefix := fmt.Sprintf("namespaces[%d].", index)
		writer.quoted(prefix+"namespace", namespace.Namespace)
		writer.quoted(prefix+"generationId", namespace.GenerationID)
		writer.quoted(prefix+"tracePersistencePolicy", namespace.TracePersistencePolicy)
		if namespace.PublicationVersion == nil {
			writer.lines = append(writer.lines, prefix+"publicationVersion: null")
		} else {
			writer.quoted(prefix+"publicationVersion", *namespace.PublicationVersion)
		}
	}
	return writer.String()
}

func instanceOutputSchema() *jsonschema.Schema {
	namespace := compactObject([]string{"namespace", "generationId", "publicationVersion", "tracePersistencePolicy"}, map[string]*jsonschema.Schema{
		"namespace": compactString(), "generationId": compactString(), "publicationVersion": {Types: []string{"string", "null"}},
		"tracePersistencePolicy": {Enum: []any{"NEVER", "ONERROR", "ALWAYS"}},
	}, true)
	return compactEnvelopeSchema(compactObject([]string{"observedAt", "instanceId", "namespaces"}, map[string]*jsonschema.Schema{
		"observedAt": compactString(), "instanceId": compactString(), "namespaces": compactArray(namespace),
	}, true))
}
