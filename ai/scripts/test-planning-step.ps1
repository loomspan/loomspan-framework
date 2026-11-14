# Actual planner/engine checks; unrelated TypeSafe/catalog restoration belongs to PR 30.3.4.
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path "$PSScriptRoot/../..").Path
$taskTarget = [IO.Path]::GetFullPath((Join-Path $taskRoot 'target/planning-step'))
$taskParent = [IO.Path]::GetFullPath((Join-Path $taskRoot 'target')) + [IO.Path]::DirectorySeparatorChar
if (-not $taskTarget.StartsWith($taskParent)) { throw 'Output escaped workspace target.' }
if (Test-Path -LiteralPath $taskTarget) { Remove-Item -LiteralPath $taskTarget -Recurse -Force }
$taskMain = Join-Path $taskTarget 'src/main/java'
$taskTests = Join-Path $taskTarget 'src/test/java'
New-Item -ItemType Directory -Force $taskMain, $taskTests | Out-Null
Copy-Item -Path (Join-Path $taskRoot 'src/main/java/*') -Destination $taskMain -Recurse
foreach ($taskResources in @('src/main/resources','src/main/resources-filtered','src/test/resources')) {
 if (Test-Path -LiteralPath (Join-Path $taskRoot $taskResources)) { Copy-Item -LiteralPath (Join-Path $taskRoot $taskResources) -Destination (Join-Path $taskTarget (Split-Path $taskResources)) -Recurse }
}
# Only these unrelated collaborators are replaced. Every behavior fails if reached.
Set-Content -LiteralPath (Join-Path $taskMain 'ai/loomspan/internal/typesafe/TypeSafeDeclaration.java') @'
package ai.loomspan.internal.typesafe;
public final class TypeSafeDeclaration {
 public static TypeSafeDeclaration compile(tools.jackson.databind.JsonNode node) { throw new UnsupportedOperationException("unrelated TypeSafe boundary"); }
 public ai.loomspan.internal.schema.CanonicalSchema outputSchema() { throw new UnsupportedOperationException("unrelated TypeSafe boundary"); }
 public ResolvedRequest resolve(Object input) { throw new UnsupportedOperationException("unrelated TypeSafe boundary"); }
 public record ResolvedRequest(String jsonText) { public tools.jackson.databind.JsonNode json() { throw new UnsupportedOperationException("unrelated TypeSafe boundary"); } }
}
'@
Set-Content -LiteralPath (Join-Path $taskMain 'ai/loomspan/internal/skill/YamlSkillCatalog.java') @'
package ai.loomspan.internal.skill;
import java.util.*;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.api.*;
public class YamlSkillCatalog {
 public YamlSkillCatalog(LoomspanProperties properties) { }
 public YamlSkillCatalog(LoomspanProperties properties, LoomspanProperties.Skills skills) { }
 public YamlSkillCatalog(LoomspanProperties properties, org.springframework.core.io.support.ResourcePatternResolver resolver, tools.jackson.databind.ObjectMapper mapper) { }
 public void afterPropertiesSet() { throw new UnsupportedOperationException("unrelated catalog boundary"); }
 public void loadSupplied(List<SkillDocument> documents) { throw new UnsupportedOperationException("unrelated catalog boundary"); }
 CheckedDocuments checkedConfigured(boolean logging) { throw new UnsupportedOperationException("unrelated catalog boundary"); }
 CheckedDocuments checkedSupplied(String namespace, List<SkillDocument> docs, boolean logging) { throw new UnsupportedOperationException("unrelated catalog boundary"); }
 public List<YamlSkillDefinition> getSkills() { throw new UnsupportedOperationException("unrelated catalog boundary"); }
 public YamlSkillDefinition getSkill(String name) { throw new UnsupportedOperationException("unrelated catalog boundary"); }
 public record CheckedDocuments(List<YamlSkillDefinition> definitions, List<SkillValidationIssue> issues) { }
}
'@
$taskTestSources = @(
 'ai/loomspan/internal/runtime/planning/PlanningServiceTest.java',
 'ai/loomspan/internal/runtime/trace/ExecutionTraceContractTest.java',
 'ai/loomspan/internal/runtime/planning/PlanningOutputContractTest.java',
 'ai/loomspan/internal/runtime/planning/PlanningServiceTestFactory.java',
 'ai/loomspan/internal/runtime/planning/PlanStructureValidatorTest.java',
 'ai/loomspan/internal/runtime/planning/EvidencePlanningIntegrationTest.java',
 'ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngineTest.java',
 'ai/loomspan/internal/runtime/step/StepActionValidatorTest.java',
 'ai/loomspan/internal/runtime/step/StepActionCorrectionTest.java',
 'ai/loomspan/internal/runtime/step/StepActionSelectionTest.java',
 'ai/loomspan/internal/runtime/step/StepPromptBuilderTest.java',
 'ai/loomspan/internal/runtime/input/SkillInputPromptRendererTest.java',
 'ai/loomspan/internal/runtime/SimpleChatClient.java',
 'ai/loomspan/internal/springai/OrdinaryNativeOutputIntegrationTest.java',
 'ai/loomspan/internal/springai/PlanningNativeOutputIntegrationTest.java',
 'ai/loomspan/internal/runtime/attachment/OrdinaryAttachmentFixture.java'
)
$taskTestSources += @(Get-ChildItem (Join-Path $taskRoot 'src/test/java/ai/loomspan/testkit') -Filter '*.java' | ForEach-Object { 'ai/loomspan/testkit/' + $_.Name })
$taskTestSources += @(Get-ChildItem (Join-Path $taskRoot 'src/test/java/ai/loomspan/internal/core') -Filter 'Test*.java' | ForEach-Object { 'ai/loomspan/internal/core/' + $_.Name })
$taskTestSources += @(Get-ChildItem (Join-Path $taskRoot 'src/test/java/ai/loomspan/internal/runtime/evidence') -Filter 'Test*.java' | ForEach-Object { 'ai/loomspan/internal/runtime/evidence/' + $_.Name })
foreach ($taskSource in $taskTestSources) {
 $taskFrom = Join-Path $taskRoot ('src/test/java/' + $taskSource)
 if (-not (Test-Path -LiteralPath $taskFrom)) { throw "Missing test $taskSource" }
 $taskTo = Join-Path $taskTests $taskSource
 New-Item -ItemType Directory -Force (Split-Path $taskTo) | Out-Null
 Copy-Item -LiteralPath $taskFrom -Destination $taskTo
}
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
Write-Host 'Actual sources: ALL production Java files except throwing TypeSafeDeclaration and YamlSkillCatalog signatures; selected tests and testkit files listed in script. No planner/engine/acceptance/session algorithms replaced. Full root/catalog/public surface remain separate.'
& mvn -o -B -f (Join-Path $taskTarget 'pom.xml') clean test '-DfailIfNoTests=true' @args
exit $LASTEXITCODE
