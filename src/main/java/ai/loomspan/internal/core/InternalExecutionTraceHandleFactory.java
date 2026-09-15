package ai.loomspan.internal.core;

import ai.loomspan.internal.runtime.observation.ExecutionObservationHandle;

import java.time.Clock;

@FunctionalInterface
interface InternalExecutionTraceHandleFactory
{
    ExecutionTraceHandle create(
            String sessionId,
            String entrySkill,
            TracePersistencePolicy persistencePolicy,
            Clock clock,
            ExecutionObservationHandle observationHandle);
}
