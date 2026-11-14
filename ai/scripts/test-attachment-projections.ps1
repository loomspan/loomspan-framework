# Focused evidence against actual projector/mapper sources, not a root build or public invocation check.
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path "$PSScriptRoot/../..").Path
$taskTarget = Join-Path $taskRoot 'target/attachment-projections'
$taskMain = Join-Path $taskTarget 'src/main/java'
$taskTests = Join-Path $taskTarget 'src/test/java'
New-Item -ItemType Directory -Force $taskMain, $taskTests | Out-Null

# Copy the exact source boundary so javac cannot implicitly compile unrelated migration consumers.
$taskSources = @(
 'ai/loomspan/internal/runtime/trace/ExecutionJournalProjector.java',
 'ai/loomspan/internal/skillapi/SkillExecutionViewMapper.java',
 'ai/loomspan/internal/serialization/LoomspanJacksonCodecs.java',
 'ai/loomspan/internal/core/TraceRecord.java',
 'ai/loomspan/internal/core/TraceRecordType.java',
 'ai/loomspan/internal/core/TraceFrameType.java',
 'ai/loomspan/internal/core/JournalEntry.java',
 'ai/loomspan/internal/core/JournalEntryType.java',
 'ai/loomspan/internal/core/JournalLevel.java',
 'ai/loomspan/internal/core/ExecutionJournal.java',
 'ai/loomspan/internal/core/ExecutionTraceHandle.java',
 'ai/loomspan/internal/core/ExecutionTrace.java',
 'ai/loomspan/internal/core/ExecutionFrame.java',
 'ai/loomspan/internal/core/OperationType.java',
 'ai/loomspan/internal/core/SkillIdentity.java',
 'ai/loomspan/internal/core/TracePersistencePolicy.java',
 'ai/loomspan/internal/core/TraceCompletion.java',
 'ai/loomspan/internal/core/TraceOutcome.java',
 'ai/loomspan/internal/runtime/usage/SessionUsageSnapshot.java',
 'ai/loomspan/internal/runtime/usage/ModelUsageRecord.java',
 'ai/loomspan/internal/runtime/usage/UsagePrecision.java',
 'ai/loomspan/api/SkillExecutionEvent.java',
 'ai/loomspan/api/SkillExecutionView.java'
)
foreach ($taskPath in $taskSources) {
 $taskDestination = Join-Path $taskMain $taskPath
 New-Item -ItemType Directory -Force (Split-Path $taskDestination) | Out-Null
 Copy-Item -LiteralPath (Join-Path "$taskRoot/src/main/java" $taskPath) -Destination $taskDestination
}
foreach ($taskPath in @('ai/loomspan/internal/runtime/trace/ExecutionJournalProjectorTest.java', 'ai/loomspan/internal/skillapi/SkillExecutionViewMapperTest.java')) {
 $taskDestination = Join-Path $taskTests $taskPath
 New-Item -ItemType Directory -Force (Split-Path $taskDestination) | Out-Null
 Copy-Item -LiteralPath (Join-Path "$taskRoot/src/test/java" $taskPath) -Destination $taskDestination
}

# These signatures substitute unrelated session/artifact collaborators only. No projector, mapper,
# journal, trace record, handle, or public event/view algorithm is replaced. Session mapping tests
# mock the two declared methods; this cannot certify SkillTemplate's full invocation lifecycle.
Set-Content (Join-Path $taskMain 'ai/loomspan/internal/core/LoomspanSession.java') @'
package ai.loomspan.internal.core;
public class LoomspanSession {
 public String getSessionId() { throw new UnsupportedOperationException("signature only"); }
 public java.util.Optional<ExecutionJournal> getFinalizedExecutionJournal() { throw new UnsupportedOperationException("signature only"); }
}
'@
Set-Content (Join-Path $taskMain 'ai/loomspan/internal/core/FinalizedTraceArtifact.java') @'
package ai.loomspan.internal.core;
public final class FinalizedTraceArtifact {}
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
Write-Host 'Actual journal projector, observer mapper, trace handle/records and public event/view sources; only unrelated session/artifact signatures substituted. Full invocation remains a separate check.'
& mvn -o -f (Join-Path $taskTarget 'pom.xml') clean test
exit $LASTEXITCODE
