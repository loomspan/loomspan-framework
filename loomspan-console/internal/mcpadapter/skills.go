package mcpadapter

import (
	"context"
	"fmt"
	"strings"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/observability"
	"github.com/modelcontextprotocol/go-sdk/mcp"
)

const (
	ListSkillsToolName = "LOOMSPAN_list_skills"
	GetSkillToolName   = "LOOMSPAN_get_skill"
)

type listSkillsInput struct {
	PageSize     int    `json:"pageSize,omitempty" jsonschema:"1-64; default 16"`
	Continuation string `json:"continuation,omitempty" jsonschema:"Continuation"`
}

type getSkillInput struct {
	RegisteredName string `json:"registeredName"`
}

func addSkillTools(server *mcp.Server, options ServerOptions) {
	addValidatedTool(server, &mcp.Tool{
		Name:        ListSkillsToolName,
		Description: "List skills.",
		Annotations: readOnlyAnnotations, InputSchema: pageInputSchema[listSkillsInput](),
	}, skillListOutputSchema(), func(ctx context.Context, _ *mcp.CallToolRequest, input listSkillsInput) (*mcp.CallToolResult, toolEnvelope[skillListResult], error) {
		return handleListSkills(ctx, options, input)
	})
	addValidatedTool(server, &mcp.Tool{
		Name:        GetSkillToolName,
		Description: "Inspect callable provenance and YAML.",
		Annotations: readOnlyAnnotations, InputSchema: nonblankInputSchema[getSkillInput]("registeredName"),
	}, skillDetailOutputSchema(), func(ctx context.Context, _ *mcp.CallToolRequest, input getSkillInput) (*mcp.CallToolResult, toolEnvelope[skillDetailResult], error) {
		return handleGetSkill(ctx, options, input)
	})
}

func handleListSkills(ctx context.Context, options ServerOptions, input listSkillsInput) (*mcp.CallToolResult, toolEnvelope[skillListResult], error) {
	scope, domain := captureScope(options)
	if domain != nil {
		return checkedDomainFailure[skillListResult](ctx, options, domain)
	}
	ctx = diagnostics.WithScope(ctx, string(scope.ID))
	if options.Observability == nil {
		return checkedDomainFailure[skillListResult](ctx, options, unavailableInspectionError(string(scope.ID)))
	}
	cursor := ""
	if input.Continuation != "" {
		cursor, domain = decodeContinuation(input.Continuation, continuationSkills, string(scope.ID), "")
		if domain != nil {
			return checkedDomainFailure[skillListResult](ctx, options, domain)
		}
	}
	pageSize := input.PageSize
	if pageSize == 0 {
		pageSize = defaultMCPListPageSize
	}
	page, domain := options.Observability.ListSkills(ctx, scope, observability.ListRequest{Cursor: cursor, PageSize: pageSize})
	if domain != nil {
		return checkedDomainFailure[skillListResult](ctx, options, domain)
	}
	items := make([]skillSummaryDTO, 0, len(page.Items))
	for _, item := range page.Items {
		items = append(items, skillSummaryDTO{RegisteredName: item.RegisteredName, Kind: item.Kind, Declaration: item.Declaration, ExecutorName: item.ExecutorName, SourcePath: item.SourcePath, BeanName: item.BeanName, Method: item.Method})
	}
	continuation := ""
	if page.HasMore && page.NextCursor != nil {
		continuation, domain = encodeContinuationDomain(continuationSkills, string(scope.ID), *page.NextCursor, "")
		if domain != nil {
			return checkedDomainFailure[skillListResult](ctx, options, domain)
		}
	}
	result := skillListResult{
		ObservedAt: page.ObservedAt.UTC(), Items: items, HasMore: page.HasMore, Continuation: continuation,
	}
	if domain := publicationDomain(options, scope); domain != nil {
		return checkedDomainFailure[skillListResult](ctx, options, domain)
	}
	if err := authenticationGenerationError(ctx, options); err != nil {
		return nil, toolEnvelope[skillListResult]{}, err
	}
	return successResult(result, skillListText(result))
}

func handleGetSkill(ctx context.Context, options ServerOptions, input getSkillInput) (*mcp.CallToolResult, toolEnvelope[skillDetailResult], error) {
	scope, domain := captureScope(options)
	if domain != nil {
		return checkedDomainFailure[skillDetailResult](ctx, options, domain)
	}
	ctx = diagnostics.WithScope(ctx, string(scope.ID))
	if options.Observability == nil {
		return checkedDomainFailure[skillDetailResult](ctx, options, unavailableInspectionError(string(scope.ID)))
	}
	detail, domain := options.Observability.GetSkill(ctx, scope, input.RegisteredName)
	if domain != nil {
		return checkedDomainFailure[skillDetailResult](ctx, options, domain)
	}
	result := skillDetailResult{
		ObservedAt: options.Now().UTC(),
		Skill: skillDetailDTO{
			RegisteredName: detail.RegisteredName, Kind: detail.Kind, Declaration: detail.Declaration, ExecutorName: detail.ExecutorName, SourcePath: detail.SourcePath, BeanName: detail.BeanName, Method: detail.Method, YAML: detail.Yaml, OutputSchema: detail.OutputSchema,
		},
	}
	if domain := publicationDomain(options, scope); domain != nil {
		return checkedDomainFailure[skillDetailResult](ctx, options, domain)
	}
	if err := authenticationGenerationError(ctx, options); err != nil {
		return nil, toolEnvelope[skillDetailResult]{}, err
	}
	return successResult(result, skillDetailText(result))
}

func skillListText(result skillListResult) string {
	var writer lineWriter
	appendCommon(&writer, result.ObservedAt)
	writer.integer("count", int64(len(result.Items)))
	writer.boolean("hasMore", result.HasMore)
	writer.continuation(result.Continuation)
	for index, item := range result.Items {
		prefix := fmt.Sprintf("items[%d].", index)
		writer.quoted(prefix+"registeredName", item.RegisteredName)
		writer.quoted(prefix+"kind", item.Kind)
		writer.quoted(prefix+"declaration", item.Declaration)
		if item.ExecutorName != "" {
			writer.quoted(prefix+"executorName", item.ExecutorName)
		}
		if item.Kind == "JAVA" {
			writer.quoted(prefix+"beanName", item.BeanName)
			writer.quoted(prefix+"method", item.Method)
		}
		if item.Declaration == "YAML" {
			writer.quoted(prefix+"sourcePath", item.SourcePath)
		}
	}
	return writer.String()
}

func skillDetailText(result skillDetailResult) string {
	var writer lineWriter
	appendCommon(&writer, result.ObservedAt)
	writer.quoted("skill.registeredName", result.Skill.RegisteredName)
	writer.quoted("skill.kind", result.Skill.Kind)
	writer.quoted("skill.declaration", result.Skill.Declaration)
	if result.Skill.ExecutorName != "" {
		writer.quoted("skill.executorName", result.Skill.ExecutorName)
	}
	if result.Skill.OutputSchema != nil {
		writer.quoted("skill.outputSchema", *result.Skill.OutputSchema)
	}
	if result.Skill.Kind == "JAVA" {
		writer.quoted("skill.beanName", result.Skill.BeanName)
		writer.quoted("skill.method", result.Skill.Method)
	}
	if result.Skill.Declaration != "YAML" {
		return writer.String()
	}
	writer.quoted("skill.sourcePath", result.Skill.SourcePath)
	text := writer.String() + "yaml:\n" + result.Skill.YAML
	if !strings.HasSuffix(text, "\n") {
		text += "\n"
	}
	return text
}

func encodeContinuationDomain(kind continuationKind, scopeID, cursor, sessionID string) (string, *consolecore.Error) {
	encoded, err := encodeContinuation(kind, scopeID, cursor, sessionID)
	if err == nil {
		return encoded, nil
	}
	return "", consolecore.NewError(consolecore.CodeConsoleError, "The Console continuation could not be created.", scopeID, consolecore.Details{}, err)
}
