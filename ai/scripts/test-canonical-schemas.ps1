# Focused production-source evidence only; this does not certify the root project.
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path "$PSScriptRoot/../..").Path
$taskTarget = Join-Path $taskRoot 'target/canonical-foundation'
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
foreach ($taskPattern in @('ai/loomspan/internal/schema/*.java','ai/loomspan/internal/serialization/LoomspanJacksonCodecs.java','ai/loomspan/internal/serialization/YamlValues.java','ai/loomspan/internal/serialization/SkillDocumentValues.java')) {
 $taskInclude = $taskPom.CreateElement('include', $taskPom.DocumentElement.NamespaceURI); $taskInclude.InnerText = $taskPattern; [void]$taskIncludes.AppendChild($taskInclude)
}
[void]$taskCompiler.AppendChild($taskIncludes)
$taskTestIncludes = $taskPom.CreateElement('testIncludes', $taskPom.DocumentElement.NamespaceURI)
foreach ($taskPattern in @('ai/loomspan/internal/schema/*.java','ai/loomspan/internal/serialization/SkillDocumentValuesTest.java','ai/loomspan/internal/serialization/YamlValuesTest.java')) {
 $taskInclude = $taskPom.CreateElement('testInclude', $taskPom.DocumentElement.NamespaceURI); $taskInclude.InnerText = $taskPattern; [void]$taskTestIncludes.AppendChild($taskInclude)
}
[void]$taskCompiler.AppendChild($taskTestIncludes)
$taskPom.Save((Join-Path $taskTarget 'pom.xml'))
Write-Host 'Restricted foundation compilation and tests against actual repository source; root build is separate.'
& mvn -o -f (Join-Path $taskTarget 'pom.xml') clean test
exit $LASTEXITCODE
