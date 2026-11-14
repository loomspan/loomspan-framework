# Actual-source binding checks only; the full application/public surface is a separate check.
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path "$PSScriptRoot/../..").Path
$taskTarget = [IO.Path]::GetFullPath((Join-Path $taskRoot 'target/bindings'))
if (-not $taskTarget.StartsWith([IO.Path]::GetFullPath((Join-Path $taskRoot 'target')) + [IO.Path]::DirectorySeparatorChar)) {
 throw 'Binding test output escaped the workspace target directory.'
}
if (Test-Path -LiteralPath $taskTarget) { Remove-Item -LiteralPath $taskTarget -Recurse -Force }
$taskMain = Join-Path $taskTarget 'src/main/java'
$taskTests = Join-Path $taskTarget 'src/test/java'
New-Item -ItemType Directory -Force $taskMain, $taskTests | Out-Null
$taskSources = @(
 'ai/loomspan/internal/schema/CanonicalSchema.java',
 'ai/loomspan/internal/schema/StandardSchemaCompiler.java',
 'ai/loomspan/internal/serialization/LoomspanJacksonCodecs.java',
 'ai/loomspan/internal/runtime/input/AcceptedResultDecoder.java',
 'ai/loomspan/internal/runtime/input/DeepInputValues.java',
 'ai/loomspan/internal/runtime/input/ObjectFieldPath.java',
 'ai/loomspan/internal/runtime/input/ChildInputBinding.java',
 'ai/loomspan/internal/runtime/input/ChildInputBindingProjection.java',
 'ai/loomspan/internal/runtime/input/ChildInputBindingDeclarations.java',
 'ai/loomspan/internal/runtime/input/ChildInputBindingAssembler.java',
 'ai/loomspan/internal/runtime/input/SkillInputContract.java',
 'ai/loomspan/internal/runtime/input/SkillInputContractResolver.java',
 'ai/loomspan/internal/runtime/input/SkillInputValidator.java',
 'ai/loomspan/internal/runtime/input/SkillInputValidationIssue.java',
 'ai/loomspan/internal/runtime/input/SkillInputValidationResult.java',
 'ai/loomspan/internal/outputschema/OutputBindingProjection.java',
 'ai/loomspan/internal/outputschema/OutputBindingComposition.java',
 'ai/loomspan/internal/outputschema/OutputSchemaValidationIssue.java',
 'ai/loomspan/internal/outputschema/OutputSchemaValidationResult.java',
 'ai/loomspan/internal/outputschema/OutputSchemaFailureMode.java',
 'ai/loomspan/internal/outputschema/OutputSchemaOutcome.java',
 'ai/loomspan/internal/outputschema/OutputSchemaOutcomeStatus.java',
 'ai/loomspan/internal/linter/LinterOutcome.java',
 'ai/loomspan/internal/linter/LinterOutcomeStatus.java',
 'ai/loomspan/internal/core/SkillIdentity.java',
 'ai/loomspan/internal/core/MissionContext.java',
 'ai/loomspan/internal/core/MissionLifecycle.java',
 'ai/loomspan/internal/core/MissionWriteRevokedException.java',
 'ai/loomspan/internal/core/FrameworkShutdownException.java',
 'ai/loomspan/internal/core/BranchDiagnosticDelta.java',
 'ai/loomspan/internal/core/ExecutionPlan.java',
 'ai/loomspan/internal/core/PlanTask.java',
 'ai/loomspan/internal/core/PlanTaskStatus.java',
 'ai/loomspan/internal/core/PlanStatus.java',
 'ai/loomspan/internal/runtime/step/AssignedTaskExecution.java',
 'ai/loomspan/internal/runtime/step/AssignedTaskOutcome.java',
 'ai/loomspan/internal/runtime/planning/PlanInputBindingDependencyValidator.java',
 'ai/loomspan/internal/runtime/planning/PlanOutputBindingProducerValidator.java',
 'ai/loomspan/internal/runtime/evidence/EvidenceContract.java',
 'ai/loomspan/internal/runtime/evidence/EvidenceExpression.java',
 'ai/loomspan/internal/runtime/evidence/EvidenceExpressionParser.java',
 'ai/loomspan/internal/runtime/evidence/EvidenceRequirement.java',
 'ai/loomspan/internal/runtime/evidence/EvidenceCoverageValidator.java',
 'ai/loomspan/internal/runtime/evidence/EvidenceCoverageResult.java',
 'ai/loomspan/internal/runtime/evidence/EvidenceCoverageIssue.java',
 'ai/loomspan/internal/runtime/evidence/EvidenceBackedOutputValidator.java',
 'ai/loomspan/internal/skill/AllowedSkillConstraint.java',
 'ai/loomspan/internal/skill/BindingSchemaCompatibility.java',
 'ai/loomspan/internal/skill/SkillDependencyValidator.java',
 'ai/loomspan/internal/skill/SkillAdmissionSnapshot.java',
 'ai/loomspan/internal/core/CapabilityMetadata.java',
 'ai/loomspan/internal/core/CapabilityToolDescriptor.java',
 'ai/loomspan/internal/core/CapabilityInvoker.java',
 'ai/loomspan/internal/core/SkillSource.java',
 'ai/loomspan/internal/core/SkillExecutionDescriptor.java',
 'ai/loomspan/internal/core/SkillExecutorRegistration.java',
 'ai/loomspan/internal/security/SkillAccessPolicy.java',
 'ai/loomspan/api/SkillValidationIssue.java',
 'ai/loomspan/api/SkillExecutorInvocation.java',
 'ai/loomspan/api/NamespaceVisibility.java',
 'ai/loomspan/api/SkillKind.java',
 'ai/loomspan/internal/skill/YamlSkillDefinition.java',
 'ai/loomspan/internal/skill/YamlSkillManifest.java',
 'ai/loomspan/internal/skill/YamlSkillSource.java',
 'ai/loomspan/internal/skill/EffectiveSkillExecutionConfiguration.java',
 'ai/loomspan/internal/util/ImmutableContainers.java',
 'ai/loomspan/autoconfigure/AiDriver.java'
)
$taskTestSources = @(
 'ai/loomspan/internal/runtime/input/AcceptedResultDecoderTest.java',
 'ai/loomspan/internal/runtime/input/ChildInputBindingTest.java',
 'ai/loomspan/internal/runtime/input/ChildInputBindingDeclarationsTest.java',
 'ai/loomspan/internal/runtime/input/ChildInputBindingProjectionTest.java',
 'ai/loomspan/internal/runtime/input/ChildInputBindingAssemblerTest.java',
 'ai/loomspan/internal/outputschema/OutputBindingCompositionTest.java',
 'ai/loomspan/internal/outputschema/OutputBindingIsolationTest.java',
 'ai/loomspan/internal/skill/BindingSchemaCompatibilityTest.java',
 'ai/loomspan/internal/skill/SkillDependencyBindingValidationTest.java',
 'ai/loomspan/internal/runtime/planning/PlanInputBindingDependencyValidatorTest.java',
 'ai/loomspan/internal/runtime/planning/PlanOutputBindingProducerValidatorTest.java'
)
foreach ($taskBoundary in @(@('src/main/java', $taskMain, $taskSources), @('src/test/java', $taskTests, $taskTestSources))) {
 foreach ($taskPath in $taskBoundary[2]) {
  $taskDestination = Join-Path $taskBoundary[1] $taskPath
  New-Item -ItemType Directory -Force (Split-Path $taskDestination) | Out-Null
  Copy-Item -LiteralPath (Join-Path (Join-Path $taskRoot $taskBoundary[0]) $taskPath) -Destination $taskDestination
 }
}

# Unrelated signatures only. Every binding/schema/decoder algorithm and mission state/fence
# above is an exact copy. These signatures cannot certify full session/catalog/TypeSafe integration.
function Write-TaskSignature($taskPath, $taskContent) {
 $taskDestination = Join-Path $taskMain $taskPath
 New-Item -ItemType Directory -Force (Split-Path $taskDestination) | Out-Null
 Set-Content -LiteralPath $taskDestination -Value $taskContent
}
Write-TaskSignature 'ai/loomspan/internal/core/LoomspanSession.java' @'
package ai.loomspan.internal.core;
public class LoomspanSession {
 public FrameworkExecutionLifecycle.AdmittedRoot admittedRoot() { return null; }
}
'@
Write-TaskSignature 'ai/loomspan/internal/core/FrameworkExecutionLifecycle.java' @'
package ai.loomspan.internal.core;
public final class FrameworkExecutionLifecycle {
 public static class AdmittedRoot { public void register(MissionLifecycle lifecycle) { throw new UnsupportedOperationException("signature only"); } }
}
'@
Write-TaskSignature 'ai/loomspan/internal/core/ExecutionBinding.java' @'
package ai.loomspan.internal.core;
public interface ExecutionBinding {
 LoomspanSession session(); MissionContext mission(); PhysicalBranchContext branch();
 default MissionContext requireMission() { throw new UnsupportedOperationException("signature only"); }
}
'@
Write-TaskSignature 'ai/loomspan/internal/core/PhysicalBranchContext.java' @'
package ai.loomspan.internal.core;
public interface PhysicalBranchContext { BranchDiagnosticDelta diagnosticDelta(); }
'@
Write-TaskSignature 'ai/loomspan/internal/typesafe/TypeSafeDeclaration.java' @'
package ai.loomspan.internal.typesafe;
public final class TypeSafeDeclaration {
 public static TypeSafeDeclaration compile(tools.jackson.databind.JsonNode node) { throw new UnsupportedOperationException("signature only"); }
 public ai.loomspan.internal.schema.CanonicalSchema outputSchema() { throw new UnsupportedOperationException("signature only"); }
}
'@
Write-TaskSignature 'ai/loomspan/internal/skill/YamlSkillCatalog.java' @'
package ai.loomspan.internal.skill;
public final class YamlSkillCatalog {
 public record CheckedDocuments(java.util.List<YamlSkillDefinition> definitions, java.util.List<ai.loomspan.api.SkillValidationIssue> issues) { }
}
'@
Write-TaskSignature 'ai/loomspan/internal/skill/SkillGeneration.java' @'
package ai.loomspan.internal.skill;
public interface SkillGeneration {
 java.util.List<ai.loomspan.internal.core.CapabilityMetadata> capabilities();
 java.util.Map<String,YamlSkillDefinition> definitions();
 ai.loomspan.api.NamespaceVisibility visibility();
 java.util.Map<String,ai.loomspan.internal.core.SkillExecutorRegistration> executors();
 java.util.Map<String,Object> executorConfiguration();
 String id(); String namespace();
 ai.loomspan.internal.core.CapabilityMetadata capability(String name);
}
'@
$taskFactory = Join-Path $taskTests 'ai/loomspan/internal/core/TestLoomspanSessions.java'
New-Item -ItemType Directory -Force (Split-Path $taskFactory) | Out-Null
Set-Content -LiteralPath $taskFactory @'
package ai.loomspan.internal.core;
public final class TestLoomspanSessions {
 public static LoomspanSession withId(String id, String entrySkill, int depth) { return new LoomspanSession(); }
}
'@
[xml]$taskPom = Get-Content (Join-Path $taskRoot 'pom.xml') -Raw
$taskNs = New-Object System.Xml.XmlNamespaceManager($taskPom.NameTable)
$taskNs.AddNamespace('m', 'http://maven.apache.org/POM/4.0.0')
$taskBuild = $taskPom.SelectSingleNode('/m:project/m:build', $taskNs)
foreach ($taskEntry in @(@('sourceDirectory', $taskMain), @('testSourceDirectory', $taskTests))) {
 $taskNode = $taskPom.CreateElement($taskEntry[0], $taskPom.DocumentElement.NamespaceURI)
 $taskNode.InnerText = $taskEntry[1]
 [void]$taskBuild.AppendChild($taskNode)
}
$taskPom.Save((Join-Path $taskTarget 'pom.xml'))
Write-Host ('Exact actual-source boundary: {0} production files, {1} test files.' -f $taskSources.Count, $taskTestSources.Count)
Write-Host 'Unrelated signatures substituted: LoomspanSession, FrameworkExecutionLifecycle, ExecutionBinding, PhysicalBranchContext, TypeSafeDeclaration, YamlSkillCatalog.CheckedDocuments, SkillGeneration; synthetic TestLoomspanSessions factory.'
Write-Host 'Actual dependency validator entry tested with direct definitions and no external generations; full YAML publication/catalog/generation paths remain separate.'
Write-Host 'Real MissionContext, MissionLifecycle and all binding/schema/decoder algorithms retained. Full application/public API/provider request checks remain separate.'
& mvn -o -f (Join-Path $taskTarget 'pom.xml') clean test
exit $LASTEXITCODE
