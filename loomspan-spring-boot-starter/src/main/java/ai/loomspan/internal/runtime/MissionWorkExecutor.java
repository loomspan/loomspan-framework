package ai.loomspan.internal.runtime;

import ai.loomspan.internal.core.*;
import ai.loomspan.internal.runtime.state.ExecutionStateService;
import ai.loomspan.internal.runtime.usage.SessionUsageService;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

/** Shared bounded mission work, cancellation, and late-write cleanup for Java and model execution. */
public final class MissionWorkExecutor
{
    private final ExecutionStateService executionStateService;
    private final Duration missionTimeout;
    private final ExecutorService missionExecutor;
    private final SessionUsageService sessionUsageService;

    public MissionWorkExecutor(ExecutionStateService executionStateService, Duration missionTimeout,
            ExecutorService missionExecutor, SessionUsageService sessionUsageService)
    {
        this.executionStateService = Objects.requireNonNull(executionStateService);
        this.missionTimeout = Objects.requireNonNull(missionTimeout);
        this.missionExecutor = Objects.requireNonNull(missionExecutor);
        this.sessionUsageService = Objects.requireNonNull(sessionUsageService);
    }

    public String execute(LoomspanSession session, String skillName, Callable<String> work)
    {
        Objects.requireNonNull(work, "work must not be null");
        ExecutionBinding capturedBinding = ExecutionBindingScope.requireCurrent();
        if (capturedBinding.session() != session)
            throw new IllegalArgumentException("Explicit session does not match the current execution binding.");
        PhysicalBranchContext branch = capturedBinding.branch();
        MissionLifecycle lifecycle = capturedBinding.requireMission().lifecycle();
        Callable<String> missionCall = () -> ExecutionBindingScope.callWith(capturedBinding, () -> {
            lifecycle.markOwningStarted();
            try
            {
                lifecycle.requireOpenForNewWork(capturedBinding);
                sessionUsageService.recordMissionStart(session, skillName);
                return work.call();
            }
            finally { lifecycle.owningReturned(); }
        });

        Future<String> mission;
        try
        {
            mission = missionExecutor.submit(missionCall);
            lifecycle.registerOwningFuture(mission);
        }
        catch (RuntimeException | Error ex)
        {
            lifecycle.beginCancellation(capturedBinding, ex, () -> executionStateService.recordFailure(
                    session, ex, Map.of("message", "Mission submission failed")));
            lifecycle.closeNow();
            throw ex;
        }
        try
        {
            return mission.get(missionTimeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException ex)
        {
            LoomspanMissionTimeoutException failure = new LoomspanMissionTimeoutException(
                    session.getSessionId(), skillName, missionTimeout, ex);
            MissionLifecycle.PrimaryCancellation primary = lifecycle.beginCancellation(capturedBinding, failure,
                    () -> executionStateService.recordFailure(session, failure, Map.of("message", "Mission execution timed out")));
            MissionLifecycle.Cutoff cutoff = lifecycle.awaitCutoff();
            cleanupFramesPreservingPrimary(session, branch, capturedBinding.requireMission(), cutoff, primary.cause());
            throw propagatePrimary(primary.cause());
        }
        catch (InterruptedException ex)
        {
            LoomspanMissionTimeoutException failure = new LoomspanMissionTimeoutException(
                    session.getSessionId(), skillName, missionTimeout, ex);
            MissionLifecycle.PrimaryCancellation primary = lifecycle.beginCancellation(capturedBinding, failure,
                    () -> executionStateService.recordFailure(session, failure, Map.of("message", "Mission execution interrupted")));
            MissionLifecycle.Cutoff cutoff = lifecycle.awaitCutoff();
            cleanupFramesPreservingPrimary(session, branch, capturedBinding.requireMission(), cutoff, primary.cause());
            Thread.currentThread().interrupt();
            throw propagatePrimary(primary.cause());
        }
        catch (ExecutionException ex)
        {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException)
            {
                throw runtimeException;
            }
            if (cause instanceof Error error)
            {
                throw error;
            }
            throw new IllegalStateException("Mission execution failed for skill '" + skillName + "'", cause);
        }
    }

    private void cleanupFrames(LoomspanSession session, PhysicalBranchContext branch,
            ai.loomspan.internal.core.MissionContext mission, MissionLifecycle.Cutoff cutoff)
    {
        RuntimeException[] cleanupFailure = {null};
        cutoff.runIfPermitted(ExecutionBindingScope.requireCurrent(), () -> {
            if (!cutoff.claimCleanup()) return;
            for (ExecutionFrame frame : branch.drainForCleanup(cutoff, mission.missionFrameId()))
            {
                try
                {
                    executionStateService.closeFrameForCleanup(session, frame,
                            Map.of("status", "aborted", "reason", "mission-cleanup"), cutoff, true);
                }
                catch (RuntimeException ex)
                {
                    if (cleanupFailure[0] == null) cleanupFailure[0] = ex;
                    else if (cleanupFailure[0] != ex) cleanupFailure[0].addSuppressed(ex);
                }
            }
        });
        if (cleanupFailure[0] != null) throw cleanupFailure[0];
    }

    private void cleanupFramesPreservingPrimary(LoomspanSession session, PhysicalBranchContext branch,
            ai.loomspan.internal.core.MissionContext mission, MissionLifecycle.Cutoff cutoff,
            Throwable primary)
    {
        try { cleanupFrames(session, branch, mission, cutoff); }
        catch (RuntimeException cleanupFailure)
        {
            if (cleanupFailure != primary) primary.addSuppressed(cleanupFailure);
        }
    }

    private RuntimeException propagatePrimary(Throwable failure)
    {
        if (failure instanceof RuntimeException runtimeException) return runtimeException;
        if (failure instanceof Error error) throw error;
        return new IllegalStateException("Mission execution failed", failure);
    }


}
