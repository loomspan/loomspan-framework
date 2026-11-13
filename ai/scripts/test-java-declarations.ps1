# Focused production-source evidence only; this does not certify the root project.
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path "$PSScriptRoot/../..").Path
$taskTarget = Join-Path $taskRoot 'target/java-declarations'
New-Item -ItemType Directory -Force $taskTarget | Out-Null
[xml]$taskPom = Get-Content (Join-Path $taskRoot 'pom.xml') -Raw
$taskNs = New-Object System.Xml.XmlNamespaceManager($taskPom.NameTable)
$taskNs.AddNamespace('m', 'http://maven.apache.org/POM/4.0.0')
$taskBuild = $taskPom.SelectSingleNode('/m:project/m:build', $taskNs)
$taskSource = $taskPom.CreateElement('sourceDirectory', $taskPom.DocumentElement.NamespaceURI)
$taskSource.InnerText = (Join-Path $taskRoot 'src/main/java')
[void]$taskBuild.AppendChild($taskSource)
$taskResource = $taskBuild.SelectSingleNode('m:testResources', $taskNs)
if ($null -eq $taskResource) {
 $taskResource = $taskPom.CreateElement('testResources', $taskPom.DocumentElement.NamespaceURI)
 [void]$taskBuild.AppendChild($taskResource)
}
$taskResourceItem = $taskPom.CreateElement('testResource', $taskPom.DocumentElement.NamespaceURI)
$taskResourceDirectory = $taskPom.CreateElement('directory', $taskPom.DocumentElement.NamespaceURI)
$taskResourceDirectory.InnerText = (Join-Path $taskRoot 'src/test/resources')
[void]$taskResourceItem.AppendChild($taskResourceDirectory)
[void]$taskResource.AppendChild($taskResourceItem)
$taskTests = $taskPom.CreateElement('testSourceDirectory', $taskPom.DocumentElement.NamespaceURI)
$taskTests.InnerText = (Join-Path $taskRoot 'src/test/java')
[void]$taskBuild.AppendChild($taskTests)
$taskCompiler = $taskBuild.SelectSingleNode('m:plugins/m:plugin[m:artifactId="maven-compiler-plugin"]/m:configuration', $taskNs)
$taskIncludes = $taskPom.CreateElement('includes', $taskPom.DocumentElement.NamespaceURI)
foreach ($taskPattern in @('ai/loomspan/internal/schema/*.java','ai/loomspan/internal/serialization/LoomspanJacksonCodecs.java','ai/loomspan/internal/serialization/YamlValues.java','ai/loomspan/internal/serialization/SkillDocumentValues.java','ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGenerator.java','ai/loomspan/internal/core/SkillMethodBeanPostProcessor.java','ai/loomspan/internal/core/JavaSkillInvoker.java','ai/loomspan/internal/core/CapabilityMetadata.java','ai/loomspan/internal/core/CapabilityInvoker.java','ai/loomspan/internal/core/CapabilityToolDescriptor.java','ai/loomspan/internal/core/CapabilityCollisionException.java','ai/loomspan/internal/core/SkillIdentity.java','ai/loomspan/internal/core/SkillSource.java','ai/loomspan/internal/core/SkillExecutionDescriptor.java','ai/loomspan/internal/core/SkillExecutorRegistration.java','ai/loomspan/internal/core/SkillFailurePolicy.java','ai/loomspan/internal/runtime/input/SkillInputContract.java','ai/loomspan/internal/security/SkillAccessPolicy.java','ai/loomspan/internal/security/SkillAccessPolicyResolver.java','ai/loomspan/internal/security/Jsr250EnforcementVerifier.java','ai/loomspan/internal/security/ScopedAuthentication.java','ai/loomspan/internal/util/ImmutableContainers.java','ai/loomspan/internal/skill/EffectiveSkillExecutionConfiguration.java','ai/loomspan/autoconfigure/AiDriver.java','ai/loomspan/api/SkillMethod.java','ai/loomspan/api/SkillExecutor.java','ai/loomspan/api/SkillExecutorInvocation.java','ai/loomspan/api/SkillParam.java','ai/loomspan/api/SkillKind.java','ai/loomspan/api/RecoverableSkillException.java')) {
 $taskInclude = $taskPom.CreateElement('include', $taskPom.DocumentElement.NamespaceURI); $taskInclude.InnerText = $taskPattern; [void]$taskIncludes.AppendChild($taskInclude)
}
[void]$taskCompiler.AppendChild($taskIncludes)
$taskTestIncludes = $taskPom.CreateElement('testIncludes', $taskPom.DocumentElement.NamespaceURI)
foreach ($taskPattern in @('ai/loomspan/internal/schema/*.java','ai/loomspan/internal/serialization/LoomspanMethodInputSchemaGeneratorTest.java','ai/loomspan/internal/core/SkillMethodBeanPostProcessorTest.java','ai/loomspan/internal/core/JavaInputDeclarationTest.java')) {
 $taskInclude = $taskPom.CreateElement('testInclude', $taskPom.DocumentElement.NamespaceURI); $taskInclude.InnerText = $taskPattern; [void]$taskTestIncludes.AppendChild($taskInclude)
}
[void]$taskCompiler.AppendChild($taskTestIncludes)
$taskPom.Save((Join-Path $taskTarget 'pom.xml'))
Write-Host 'Restricted Java declaration compilation and tests against actual repository source; root build is separate.'
& mvn -o -f (Join-Path $taskTarget 'pom.xml') clean test
exit $LASTEXITCODE
