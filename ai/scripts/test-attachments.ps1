# Focused actual attachment/validation source evidence; no claim of full application execution.
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path "$PSScriptRoot/../..").Path
$taskTarget = Join-Path $taskRoot 'target/attachments'
$taskMain = Join-Path $taskTarget 'src/main/java'
$taskTests = Join-Path $taskTarget 'src/test/java'
New-Item -ItemType Directory -Force $taskMain, $taskTests | Out-Null
$taskSources = @(
 'ai/loomspan/internal/runtime/attachment/*.java',
 'ai/loomspan/internal/runtime/input/SkillInputContract.java',
 'ai/loomspan/internal/runtime/input/SkillInputContractResolver.java',
 'ai/loomspan/internal/runtime/input/SkillInputValidator.java',
 'ai/loomspan/internal/runtime/input/SkillInputValidationIssue.java',
 'ai/loomspan/internal/runtime/input/SkillInputValidationResult.java',
 'ai/loomspan/internal/schema/*.java',
 'ai/loomspan/internal/serialization/LoomspanJacksonCodecs.java',
 'ai/loomspan/internal/core/MissionInputMessageFormatter.java',
 'ai/loomspan/internal/util/ImmutableContainers.java',
 'ai/loomspan/internal/vfs/RefResolver.java',
 'ai/loomspan/internal/vfs/InputReferenceTraversal.java'
)
foreach ($taskPattern in $taskSources) {
 foreach ($taskFile in Get-Item (Join-Path "$taskRoot/src/main/java" $taskPattern)) {
  $taskRelative = $taskFile.FullName.Substring((Join-Path $taskRoot 'src/main/java').Length + 1)
  $taskDestination = Join-Path $taskMain $taskRelative
  New-Item -ItemType Directory -Force (Split-Path $taskDestination) | Out-Null
  Copy-Item -LiteralPath $taskFile.FullName -Destination $taskDestination
 }
}
$taskTestPath = 'ai/loomspan/internal/runtime/attachment/AttachmentMaterializationContractTest.java'
$taskDestination = Join-Path $taskTests $taskTestPath
New-Item -ItemType Directory -Force (Split-Path $taskDestination) | Out-Null
Copy-Item -LiteralPath (Join-Path "$taskRoot/src/test/java" $taskTestPath) -Destination $taskDestination

# Signature-only substitutes for unrelated session/catalog/generation dependencies. Tests mock
# session/catalog and supply real canonical contracts; all attachment and validation algorithms
# above are actual repository sources. Active generation configuration/invocation is not certified.
Set-Content (Join-Path $taskMain 'ai/loomspan/internal/core/LoomspanSession.java') @'
package ai.loomspan.internal.core;
public class LoomspanSession {}
'@
Set-Content (Join-Path $taskMain 'ai/loomspan/internal/core/ExecutionBindingScope.java') @'
package ai.loomspan.internal.core;
public final class ExecutionBindingScope {
 public static java.util.Optional<Binding> current() { return java.util.Optional.empty(); }
 public static class Binding { public Generation generation() { throw new UnsupportedOperationException("signature only"); } }
 public static class Generation { public Runtime runtime() { throw new UnsupportedOperationException("signature only"); } }
 public static class Runtime { public Properties properties() { throw new UnsupportedOperationException("signature only"); } }
 public static class Properties { public Session getSession() { throw new UnsupportedOperationException("signature only"); } }
 public static class Session { public Attachments getAttachments() { throw new UnsupportedOperationException("signature only"); } }
 public static class Attachments { public org.springframework.util.unit.DataSize getMaxSize() { throw new UnsupportedOperationException("signature only"); } }
}
'@
$taskDefinition = Join-Path $taskMain 'ai/loomspan/internal/skill/YamlSkillDefinition.java'
New-Item -ItemType Directory -Force (Split-Path $taskDefinition) | Out-Null
Set-Content $taskDefinition @'
package ai.loomspan.internal.skill;
public class YamlSkillDefinition {
 public boolean hasDeclaredInputSchema() { throw new UnsupportedOperationException("signature only"); }
 public ai.loomspan.internal.schema.CanonicalSchema inputSchema() { throw new UnsupportedOperationException("signature only"); }
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
Write-Host 'Actual materializer, descriptor, canonical compiler, admission/descriptor validator, formatter and reference traversal; session/catalog/generation signatures substituted. Root lifecycle remains separate.'
& mvn -o -f (Join-Path $taskTarget 'pom.xml') clean test
exit $LASTEXITCODE
