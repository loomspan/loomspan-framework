package ai.loomspan.internal.runtime.step;

import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.core.PlanStatus;
import ai.loomspan.internal.core.PlanTask;
import ai.loomspan.internal.core.PlanTaskStatus;
import ai.loomspan.internal.runtime.tool.BoundCapability;
import ai.loomspan.internal.skill.YamlSkillManifest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static ai.loomspan.testkit.TestBoundCapabilities.capability;
import static ai.loomspan.testkit.TestBoundCapabilities.contractAware;

class StepPromptBuilderTest {

    private static BoundCapability mockTool(String name) {
        return capability(name);
    }

    private static BoundCapability mockTool(String name, String inputSchema) {
        return capability(name, inputSchema);
    }

    private static BoundCapability contractAwareTool(String name, String inputSchema, String contractSchema) {
        return contractAware(name, inputSchema, contractSchema);
    }

    @Test
    void buildStepPromptContainsObjective() {
        ExecutionPlan plan = createTwoTaskPlan();
        String prompt = buildPromptForCurrentMode(
                plan, "Check for duplicate invoices", 1, null, null,
                List.of(mockTool("invoiceParser")), false, null);
        assertThat(prompt).contains("Check for duplicate invoices");
    }

    @Test
    void buildStepPromptRemovesSkillNameFromMissionContext() {
        ExecutionPlan plan = createTwoTaskPlan();
        String prompt = buildPromptForCurrentMode(
                plan,
                "Execute YAML skill 'duplicateInvoiceChecker' using the provided mission input object.",
                Map.of("payload", "x"),
                1,
                null,
                null,
                List.of(),
                false,
                null);
        assertThat(prompt).doesNotContain("duplicateInvoiceChecker");
        assertThat(prompt).contains("Use the provided mission inputs.");
    }

    @Test
    void buildStepUserMessageDoesNotDuplicateCanonicalMissionInput() {
        ExecutionPlan plan = createTwoTaskPlan();

        String userMessage = StepPromptBuilder.buildStepUserMessage(
                plan,
                "Execute YAML skill 'duplicateInvoiceChecker' using the provided mission input object.",
                Map.of("payload", "x"));

        assertThat(userMessage).contains("Mission objective:");
        assertThat(userMessage).contains("Use the provided mission inputs.");
        assertThat(userMessage).contains("\"payload\" : \"x\"");
        assertThat(countOccurrences(userMessage, "Canonical mission input:")).isEqualTo(1);
    }

    @Test
    void buildStepPromptContainsStepNumber() {
        ExecutionPlan plan = createTwoTaskPlan();
        String prompt = buildPromptForCurrentMode(
                plan, "objective", 5, null, null, List.of(), false, null);
        assertThat(prompt).contains("Step: 5");
    }

    @Test
    void assignedPromptShowsAcceptedPlanWithoutReadyTaskChoice() {
        ExecutionPlan plan = createTwoTaskPlan();
        String prompt = buildPromptForCurrentMode(
                plan, "objective", 1, null, null, List.of(), false, null);
        assertThat(prompt).contains("ACCEPTED PLAN CONTEXT", "ASSIGNED TASK", "ID: t-1", "Parse invoice");
        assertThat(prompt).doesNotContain("READY TASKS");
    }

    @Test
    void assignedPromptDoesNotExposeDependenciesAsSchedulingChoices() {
        ExecutionPlan plan = createTwoTaskPlan();
        String prompt = buildPromptForCurrentMode(
                plan, "objective", 1, null, null, List.of(), false, null);
        assertThat(prompt).contains("[PENDING] t-2");
        assertThat(prompt).doesNotContain("PENDING TASKS WAITING ON DEPENDENCIES", "waiting on:", "dependsOn");
    }

    @Test
    void assignedPromptShowsFailedPlanContextWithoutLifecycleNote() {
        ExecutionPlan plan = createTwoTaskPlan()
                .updateTask("t-2", task -> task.fail("tool failed"));
        String prompt = buildPromptForCurrentMode(
                plan, "objective", 1, null, null, List.of(), false, null);
        assertThat(prompt).contains("[FAILED] t-2");
        assertThat(prompt).doesNotContain("FAILED TASKS", "tool failed");
    }

    @Test
    void assignedPromptShowsCompletedPlanContext() {
        ExecutionPlan plan = createTwoTaskPlan()
                .updateTask("t-1", task -> task.complete("parsed successfully"));
        String prompt = buildPromptForCurrentMode(
                plan, "objective", 2, null, null, List.of(), false, null);
        assertThat(prompt).contains("[COMPLETED] t-1");
        assertThat(prompt).doesNotContain("COMPLETED TASKS");
    }

    @Test
    void buildStepPromptIncludesLastToolResultTruncated() {
        ExecutionPlan plan = createTwoTaskPlan();
        String longResult = "X".repeat(2000);
        String prompt = buildPromptForCurrentMode(
                plan, "objective", 2, longResult, null, List.of(), false, null);
        assertThat(prompt).contains("LAST TOOL RESULT");
        assertThat(prompt).contains("truncated");
        assertThat(prompt.length()).isLessThan(3000);
    }

    @Test
    void buildStepPromptIncludesExecutionSummary() {
        ExecutionPlan plan = createTwoTaskPlan();
        String summary = "Step 1: Called invoiceParser for t-1 -> parsed vendor=Acme";
        String prompt = buildPromptForCurrentMode(
                plan, "objective", 2, null, summary, List.of(), false, null);
        assertThat(prompt).contains("EXECUTION SUMMARY");
        assertThat(prompt).contains("invoiceParser");
    }

    @Test
    void buildStepPromptContainsActionContract() {
        ExecutionPlan plan = createTwoTaskPlan();
        String prompt = buildPromptForCurrentMode(
                plan, "objective", 1, null, null, List.of(), false, null);
        assertThat(prompt).contains("CALL_TOOL");
        assertThat(prompt).contains("stepAction");
        assertThat(prompt).contains("valid JSON");
        assertThat(prompt).contains("Return CALL_TOOL for exactly the assigned task and tool shown above.");
        assertThat(prompt).doesNotContain("READY tasks");
    }

    @Test
    void buildStepPromptHighlightsExactToolBindingForSingleReadyTask() {
        ExecutionPlan plan = createTwoTaskPlan()
                .updateTask("t-1", task -> task.complete("parsed"));
        String prompt = buildPromptForCurrentMode(
                plan, "objective", 2, "{\"vendor\":\"Acme\"}", null, List.of(), false, null);
        assertThat(prompt).contains("ASSIGNED TASK", "ID: t-2", "Exact capability/tool: expenseLookup");
        assertThat(prompt).contains("\"taskId\": \"t-2\"", "\"toolName\": \"expenseLookup\"");
        assertThat(prompt).contains("Do not call the parent mission skill");
    }

    @Test
    void buildStepPromptShowsConcreteToolArgumentShape() {
        ExecutionPlan plan = createTwoTaskPlan();
        String prompt = buildPromptForCurrentMode(
                plan,
                "objective",
                1,
                null,
                null,
                List.of(mockTool("invoiceParser", """
                        {
                          "type": "object",
                          "properties": {
                            "payload": { "type": "string" }
                          },
                          "required": ["payload"],
                          "additionalProperties": false
                        }
                        """)),
                false,
                null);

        assertThat(prompt).contains("TOOL ARGUMENT SHAPE");
        assertThat(prompt).contains("Task t-1 / tool invoiceParser");
        assertThat(prompt).contains("\"payload\": \"<string>\"");
    }

    @Test
    void buildStepPromptUsesContractAwareToolGuidanceEvenWhenToolSchemaIsGeneric() {
        ExecutionPlan plan = createTwoTaskPlan();

        String prompt = buildPromptForCurrentMode(
                plan,
                "objective",
                1,
                null,
                null,
                List.of(contractAwareTool("invoiceParser", """
                        {
                          "type": "object",
                          "properties": {},
                          "additionalProperties": true
                        }
                        """, """
                        {
                          "type": "object",
                          "properties": {
                            "payload": { "type": "string" }
                          },
                          "required": ["payload"],
                          "additionalProperties": false
                        }
                        """)),
                false,
                null);

        assertThat(prompt).contains("TOOL ARGUMENT SHAPE");
        assertThat(prompt).contains("\"payload\": \"<string>\"");
    }

    @Test
    void buildStepPromptVerboseGuidanceIncludesNestedFieldRules() {
        ExecutionPlan plan = createTwoTaskPlan();

        String prompt = buildPromptForCurrentMode(
                plan,
                "objective",
                null,
                1,
                null,
                null,
                List.of(mockTool("invoiceParser", """
                        {
                          "type": "object",
                          "properties": {
                            "invoiceId": { "type": "string" },
                            "options": {
                              "type": "object",
                              "properties": {
                                "includeHistory": { "type": "boolean" }
                              },
                              "required": ["includeHistory"],
                              "additionalProperties": false
                            }
                          },
                          "required": ["invoiceId"],
                          "additionalProperties": false
                        }
                        """)),
                false,
                true,
                null);

        assertThat(prompt).contains("Required fields: invoiceId");
        assertThat(prompt).contains("Required fields: options.includeHistory");
        assertThat(prompt).contains("`options.includeHistory` must be a boolean");
        assertThat(prompt).contains("Do not add fields under `options` beyond those shown above.");
    }

    @Test
    void buildStepPromptDoesNotInventClosedNestedObjectRulesWhenKeywordIsOmitted() {
        ExecutionPlan plan = createTwoTaskPlan();

        String prompt = buildPromptForCurrentMode(
                plan,
                "objective",
                null,
                1,
                null,
                null,
                List.of(mockTool("invoiceParser", """
                        {
                          "type": "object",
                          "properties": {
                            "options": {
                              "type": "object",
                              "properties": {
                                "includeHistory": { "type": "boolean" }
                              }
                            }
                          },
                          "required": ["options"],
                          "additionalProperties": false
                        }
                        """)),
                false,
                true,
                null);

        assertThat(prompt).contains("`options.includeHistory` must be a boolean");
        assertThat(prompt).doesNotContain("Do not add fields under `options` beyond those shown above.");
    }

    @Test
    void buildStepPromptShowsTypedMapArgumentShape() {
        ExecutionPlan plan = createTwoTaskPlan();

        String prompt = buildPromptForCurrentMode(
                plan,
                "objective",
                1,
                null,
                null,
                List.of(mockTool("invoiceParser", """
                        {
                          "type": "object",
                          "additionalProperties": {
                            "type": "string"
                          }
                        }
                        """)),
                false,
                null);

        assertThat(prompt).contains("TOOL ARGUMENT SHAPE");
        assertThat(prompt).contains("\"<key>\": \"<string>\"");
    }

    @Test
    void buildStepPromptUsesVerboseGuidanceForComplexTypedMapValues() {
        ExecutionPlan plan = createTwoTaskPlan();

        String prompt = buildPromptForCurrentMode(
                plan,
                "objective",
                1,
                null,
                null,
                List.of(mockTool("invoiceParser", """
                        {
                          "type": "object",
                          "additionalProperties": {
                            "type": "object",
                            "properties": {
                              "id": { "type": "string" },
                              "metadata": {
                                "type": "object",
                                "properties": {
                                  "enabled": { "type": "boolean" }
                                },
                                "required": ["enabled"],
                                "additionalProperties": false
                              }
                            },
                            "required": ["id"],
                            "additionalProperties": false
                          }
                        }
                        """)),
                false,
                null);

        assertThat(prompt).contains("Required fields: <key>.id");
        assertThat(prompt).contains("`<key>.metadata.enabled` must be a boolean");
        assertThat(prompt).contains("Do not add fields under `<key>` beyond those shown above.");
    }

    @Test
    void buildStepPromptShowsGenericArrayItemsWhenItemsSchemaIsOmitted() {
        ExecutionPlan plan = createTwoTaskPlan();

        String prompt = buildPromptForCurrentMode(
                plan,
                "objective",
                1,
                null,
                null,
                List.of(mockTool("invoiceParser", """
                        {
                          "type": "object",
                          "properties": {
                            "values": {
                              "type": "array"
                            }
                          },
                          "required": ["values"],
                          "additionalProperties": false
                        }
                        """)),
                false,
                null);

        assertThat(prompt).contains("TOOL ARGUMENT SHAPE");
        assertThat(prompt).contains("\"values\": [ \"<value>\" ]");
    }

    @Test
    void buildStepPromptDescribesUnconstrainedValuesWithoutObjectOrNoArgumentClaims()
    {
        ExecutionPlan plan = createTwoTaskPlan();
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "value": {},
                    "options": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": {}
                      }
                    }
                  },
                  "required": ["value", "options"],
                  "additionalProperties": false
                }
                """;

        String compact = buildPromptForCurrentMode(
                plan, "objective", 1, null, null, List.of(mockTool("invoiceParser", schema)), false, null);
        String verbose = buildPromptForCurrentMode(
                plan, "objective", null, 1, null, null,
                List.of(mockTool("invoiceParser", schema)), false, true, null);

        assertThat(compact).contains("\"value\": <any JSON value>");
        assertThat(compact).contains("\"<key>\": <any JSON value>");
        assertThat(compact).doesNotContain("This tool takes no arguments");
        assertThat(verbose).contains("`value` must be any JSON value");
        assertThat(verbose).contains("`options[].<key>` must be any JSON value");
        assertThat(verbose).doesNotContain("`options[].<key>` must be a object");
        assertThat(verbose).doesNotContain("This tool takes no arguments");
    }

    @Test
    void assignedPromptListsOnlyItsExactTool() {
        ExecutionPlan plan = createTwoTaskPlan();
        String prompt = buildPromptForCurrentMode(
                plan, "objective", 1, null, null,
                List.of(mockTool("invoiceParser"), mockTool("expenseLookup")), false, null);
        assertThat(prompt).contains("AVAILABLE TOOL FOR THIS ASSIGNMENT", "invoiceParser");
        assertThat(prompt).doesNotContain("- expenseLookup");
    }

    @Test
    void assignedPromptShowsOnlyExactTaskAndToolAndOmitsFinalChoice()
    {
        ExecutionPlan plan = createTwoTaskPlan()
                .updateTask("t-1", task -> task.bindInProgress("Starting tool invoiceParser"));
        PlanTask assigned = plan.findTask("t-1").orElseThrow();

        String prompt = StepPromptBuilder.buildAssignedStepPrompt(
                plan, assigned, "objective", Map.of("invoiceId", "INV-1"), 1,
                "prior-result", "prior-summary",
                List.of(mockTool("invoiceParser"), mockTool("expenseLookup")), false);

        assertThat(prompt)
                .contains("ASSIGNED TASK", "ID: t-1", "Exact capability/tool: invoiceParser")
                .contains("prior-result", "prior-summary")
                .contains("\"taskId\": \"t-1\"", "\"toolName\": \"invoiceParser\"")
                .doesNotContain("Exact capability/tool: expenseLookup")
                .doesNotContain("FINAL_RESPONSE")
                .doesNotContain("parallelGroup", "dependsOn", "Starting tool invoiceParser");
        assertThat(countOccurrences(prompt, "--- ASSIGNED TASK ---")).isEqualTo(1);
    }

    @Test
    void assignedPromptNamesAcceptedToolEvenWhenVisibleListIsEmpty() {
        ExecutionPlan plan = createTwoTaskPlan();
        String prompt = buildPromptForCurrentMode(
                plan, "objective", 1, null, null, List.of(), false, null);
        assertThat(prompt).contains("AVAILABLE TOOL FOR THIS ASSIGNMENT", "- invoiceParser");
    }

    @Test
    void buildStepPromptForCompletedPlanRequiresFinalResponseOnly() {
        ExecutionPlan plan = createTwoTaskPlan()
                .updateTask("t-1", task -> task.complete("parsed"))
                .updateTask("t-2", task -> task.complete("matched"));
        String prompt = buildPromptForCurrentMode(
                plan, "objective", 3, null, "Step 2 complete", List.of(mockTool("invoiceParser")), true, null);
        assertThat(prompt).contains("All required plan tasks are already COMPLETE");
        assertThat(prompt).contains("You must return a FINAL_RESPONSE action");
        assertThat(prompt).doesNotContain("Option 1 - Call a tool for a ready task");
        assertThat(prompt).doesNotContain("\"stepAction\": \"CALL_TOOL\"");
    }

    @Test
    void buildStepPromptShowsOutputSchemaInFinalResponseMode() {
        ExecutionPlan plan = createTwoTaskPlan()
                .updateTask("t-1", task -> task.complete("parsed"))
                .updateTask("t-2", task -> task.complete("matched"));
        String prompt = buildPromptForCurrentMode(
                plan, "objective", 3, null, "Step 2 complete", List.of(mockTool("invoiceParser")), true, duplicateInvoiceOutputSchema());
        assertThat(prompt).contains("REQUIRED FINAL RESPONSE SHAPE");
        assertThat(prompt).contains("Property semantics:");
        assertThat(prompt).contains("$ — object, non-null, additionalProperties=false");
        assertThat(prompt).contains("$.isDuplicate — boolean, required, non-null");
        assertThat(prompt).contains("$.reasoning — string, required, non-null");
        assertThat(prompt).doesNotContain("invoiceParser and expenseLookup");
        assertThat(prompt).doesNotContain("\"evidence\"");
        assertThat(prompt).doesNotContain("Required top-level fields:");
    }

    @Test
    void buildStepPromptListsNullableOutputFields() {
        ExecutionPlan plan = createTwoTaskPlan()
                .updateTask("t-1", task -> task.complete("parsed"))
                .updateTask("t-2", task -> task.complete("matched"));
        YamlSkillManifest.OutputSchemaManifest schema = duplicateInvoiceOutputSchema();
        schema.getProperties().get("invoiceDate").setNullable(true);
        schema.getProperties().get("reasoning").setNullable(true);

        String prompt = buildPromptForCurrentMode(
                plan, "objective", 3, null, "Step 2 complete", List.of(mockTool("invoiceParser")), true, schema);

        assertThat(prompt).contains("$.invoiceDate — string, required, nullable");
        assertThat(prompt).contains("$.reasoning — string, required, nullable");
        assertThat(prompt).contains("$.isDuplicate — boolean, required, non-null");
        assertThat(prompt).doesNotContain("Nullable fields may be JSON null:");
    }

    @Test
    void buildStepPromptOmitsOutputContractUntilFinalResponseOnly()
    {
        String prompt = buildPromptForCurrentMode(
                createTwoTaskPlan(), "objective", 1, null, null,
                List.of(mockTool("invoiceParser"), mockTool("expenseLookup")), false, duplicateInvoiceOutputSchema());

        assertThat(prompt).doesNotContain("REQUIRED FINAL RESPONSE SHAPE");
        assertThat(prompt).doesNotContain("Property semantics:");
        assertThat(prompt).doesNotContain("Output contract:");
    }

    private static String buildPromptForCurrentMode(ExecutionPlan plan,
            String objective,
            int stepNumber,
            String lastToolResult,
            String executionSummary,
            List<BoundCapability> visibleTools,
            boolean finalResponseOnly,
            YamlSkillManifest.OutputSchemaManifest outputSchema)
    {
        return buildPromptForCurrentMode(plan, objective, null, stepNumber, lastToolResult, executionSummary,
                visibleTools, finalResponseOnly, false, outputSchema);
    }

    private static String buildPromptForCurrentMode(ExecutionPlan plan,
            String objective,
            Map<String, Object> missionInput,
            int stepNumber,
            String lastToolResult,
            String executionSummary,
            List<BoundCapability> visibleTools,
            boolean finalResponseOnly,
            YamlSkillManifest.OutputSchemaManifest outputSchema)
    {
        return buildPromptForCurrentMode(plan, objective, missionInput, stepNumber, lastToolResult, executionSummary,
                visibleTools, finalResponseOnly, false, outputSchema);
    }

    private static String buildPromptForCurrentMode(ExecutionPlan plan,
            String objective,
            Map<String, Object> missionInput,
            int stepNumber,
            String lastToolResult,
            String executionSummary,
            List<BoundCapability> visibleTools,
            boolean finalResponseOnly,
            boolean forceVerboseToolArgumentGuidance,
            YamlSkillManifest.OutputSchemaManifest outputSchema)
    {
        if (finalResponseOnly)
        {
            return StepPromptBuilder.buildFinalResponsePrompt(
                    plan, objective, missionInput, stepNumber, lastToolResult, executionSummary, outputSchema);
        }
        PlanTask assigned = plan.readyTasks().getFirst();
        ExecutionPlan admitted = plan.updateTask(assigned.taskId(),
                task -> task.bindInProgress("Assigned by test coordinator"));
        return StepPromptBuilder.buildAssignedStepPrompt(
                admitted, assigned, objective, missionInput, stepNumber, lastToolResult, executionSummary,
                visibleTools, forceVerboseToolArgumentGuidance);
    }

    private YamlSkillManifest.OutputSchemaManifest duplicateInvoiceOutputSchema() {
        YamlSkillManifest.OutputSchemaManifest schema = new YamlSkillManifest.OutputSchemaManifest();
        schema.setType("object");
        schema.setAdditionalProperties(false);

        YamlSkillManifest.OutputSchemaManifest isDuplicate = new YamlSkillManifest.OutputSchemaManifest();
        isDuplicate.setType("boolean");
        isDuplicate.setEvidence("invoiceParser and expenseLookup");
        YamlSkillManifest.OutputSchemaManifest vendorName = new YamlSkillManifest.OutputSchemaManifest();
        vendorName.setType("string");
        YamlSkillManifest.OutputSchemaManifest totalAmount = new YamlSkillManifest.OutputSchemaManifest();
        totalAmount.setType("number");
        YamlSkillManifest.OutputSchemaManifest invoiceDate = new YamlSkillManifest.OutputSchemaManifest();
        invoiceDate.setType("string");
        YamlSkillManifest.OutputSchemaManifest reasoning = new YamlSkillManifest.OutputSchemaManifest();
        reasoning.setType("string");

        Map<String, YamlSkillManifest.OutputSchemaManifest> properties = new java.util.LinkedHashMap<>();
        properties.put("isDuplicate", isDuplicate);
        properties.put("vendorName", vendorName);
        properties.put("totalAmount", totalAmount);
        properties.put("invoiceDate", invoiceDate);
        properties.put("reasoning", reasoning);
        schema.setProperties(properties);
        schema.setRequired(List.of("isDuplicate", "vendorName", "totalAmount", "invoiceDate", "reasoning"));
        return schema;
    }

    private ExecutionPlan createTwoTaskPlan() {
        PlanTask task1 = new PlanTask("t-1", "Parse invoice", PlanTaskStatus.PENDING,
                "invoiceParser", "Extract vendor, amount, date",
                List.of(), List.of("parsedInvoice"), null, null);
        PlanTask task2 = new PlanTask("t-2", "Look up expenses", PlanTaskStatus.PENDING,
                "expenseLookup", "Find matching expenses",
                List.of("t-1"), List.of("expenses"), null, null);
        return new ExecutionPlan("plan-1", "duplicateInvoiceChecker", Instant.now(),
                PlanStatus.VALID, List.of(task1, task2));
    }

    private int countOccurrences(String value, String token) {
        return value.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }
}
