package ai.loomspan.internal.core;

import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.outputschema.OutputSchemaOutcome;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The synchronized physical frame path for one execution branch. */
public final class PhysicalBranchContext
{
    private final LoomspanSession session;
    private final List<ExecutionFrame> inheritedRootToLeaf;
    private final Deque<ExecutionFrame> localFrames = new ArrayDeque<>();
    private final boolean missionDiagnosticOwner;
    private LinterOutcome lastLinterOutcome;
    private OutputSchemaOutcome lastOutputSchemaOutcome;

    public PhysicalBranchContext(LoomspanSession session)
    {
        this(session, List.of(), true);
    }

    private PhysicalBranchContext(LoomspanSession session,
            List<ExecutionFrame> inheritedRootToLeaf,
            boolean missionDiagnosticOwner)
    {
        this.session = Objects.requireNonNull(session, "session must not be null");
        this.inheritedRootToLeaf = List.copyOf(inheritedRootToLeaf);
        this.missionDiagnosticOwner = missionDiagnosticOwner;
    }

    public LoomspanSession session() { return session; }

    public synchronized int depth() { return inheritedRootToLeaf.size() + localFrames.size(); }

    public synchronized int localDepth() { return localFrames.size(); }

    public synchronized Optional<ExecutionFrame> leaf()
    {
        ExecutionFrame localLeaf = localFrames.peek();
        return localLeaf == null
                ? inheritedRootToLeaf.stream().reduce((left, right) -> right)
                : Optional.of(localLeaf);
    }

    public synchronized ExecutionFrame requireLeaf()
    {
        ExecutionFrame frame = leaf().orElse(null);
        if (frame == null) throw new IllegalStateException("Cannot access an execution frame from an empty branch path.");
        return frame;
    }

    public synchronized boolean contains(ExecutionFrame frame)
    {
        ExecutionFrame candidate = Objects.requireNonNull(frame, "frame must not be null");
        return localFrames.contains(candidate) || inheritedRootToLeaf.contains(candidate);
    }

    public synchronized boolean owns(ExecutionFrame frame)
    {
        return localFrames.contains(Objects.requireNonNull(frame, "frame must not be null"));
    }

    public synchronized List<ExecutionFrame> rootToLeafSnapshot()
    {
        ArrayList<ExecutionFrame> snapshot = new ArrayList<>(inheritedRootToLeaf);
        ArrayList<ExecutionFrame> localRootToLeaf = new ArrayList<>(localFrames);
        Collections.reverse(localRootToLeaf);
        snapshot.addAll(localRootToLeaf);
        return List.copyOf(snapshot);
    }

    public synchronized List<ExecutionFrame> leafToRootSnapshot()
    {
        ArrayList<ExecutionFrame> snapshot = new ArrayList<>(localFrames);
        ArrayList<ExecutionFrame> inheritedLeafToRoot = new ArrayList<>(inheritedRootToLeaf);
        Collections.reverse(inheritedLeafToRoot);
        snapshot.addAll(inheritedLeafToRoot);
        return List.copyOf(snapshot);
    }

    public synchronized void push(ExecutionFrame frame)
    {
        Objects.requireNonNull(frame, "frame must not be null");
        if (countsTowardMaxDepth(frame) && currentMaxDepthUsage() >= session.getMaxDepth())
        {
            throw new LoomspanStackOverflowException(session.getSessionId(), session.getMaxDepth(), frame.route());
        }
        localFrames.push(frame);
    }

    public synchronized ExecutionFrame close(ExecutionFrame frame)
    {
        Objects.requireNonNull(frame, "frame must not be null");
        if (!localFrames.contains(frame)) return frame;
        ExecutionFrame leaf = localFrames.peek();
        if (!frame.equals(leaf))
        {
            throw new IllegalStateException("Attempted to close execution frame '%s' but active frame was '%s'."
                    .formatted(frame.frameId(), leaf.frameId()));
        }
        return localFrames.pop();
    }

    public synchronized boolean rollback(ExecutionFrame frame)
    {
        if (frame != null && frame.equals(localFrames.peek()))
        {
            localFrames.pop();
            return true;
        }
        return false;
    }

    public synchronized List<ExecutionFrame> drainForCleanup(MissionLifecycle.Cutoff cutoff, String excludedFrameId)
    {
        Objects.requireNonNull(cutoff, "cutoff must not be null");
        if (!cutoff.ownsSession(session))
        {
            throw new IllegalArgumentException("Cleanup authority belongs to a different session.");
        }
        ArrayList<ExecutionFrame> drained = new ArrayList<>();
        while (!localFrames.isEmpty())
        {
            ExecutionFrame frame = localFrames.peek();
            if (Objects.equals(frame.frameId(), excludedFrameId)) break;
            drained.add(localFrames.pop());
        }
        return List.copyOf(drained);
    }

    public synchronized void recordLinterOutcome(LinterOutcome outcome)
    {
        lastLinterOutcome = Objects.requireNonNull(outcome, "outcome must not be null");
    }

    public synchronized void recordOutputSchemaOutcome(OutputSchemaOutcome outcome)
    {
        lastOutputSchemaOutcome = Objects.requireNonNull(outcome, "outcome must not be null");
    }

    public synchronized Optional<LinterOutcome> lastLinterOutcome()
    {
        return Optional.ofNullable(lastLinterOutcome);
    }

    public synchronized Optional<OutputSchemaOutcome> lastOutputSchemaOutcome()
    {
        return Optional.ofNullable(lastOutputSchemaOutcome);
    }

    public boolean ownsMissionDiagnostics()
    {
        return missionDiagnosticOwner;
    }

    public synchronized PhysicalBranchContext fork()
    {
        return new PhysicalBranchContext(session, rootToLeafSnapshot(), false);
    }

    synchronized BranchDiagnosticDelta diagnosticDelta()
    {
        return new BranchDiagnosticDelta(lastLinterOutcome, lastOutputSchemaOutcome);
    }

    synchronized void mergeDiagnostics(BranchDiagnosticDelta delta)
    {
        Objects.requireNonNull(delta, "delta must not be null");
        if (delta.linterOutcome() != null) lastLinterOutcome = delta.linterOutcome();
        if (delta.outputSchemaOutcome() != null) lastOutputSchemaOutcome = delta.outputSchemaOutcome();
    }

    private int currentMaxDepthUsage()
    {
        int depth = 0;
        for (ExecutionFrame frame : inheritedRootToLeaf) if (countsTowardMaxDepth(frame)) depth++;
        for (ExecutionFrame frame : localFrames) if (countsTowardMaxDepth(frame)) depth++;
        return depth;
    }

    private static boolean countsTowardMaxDepth(ExecutionFrame frame)
    {
        return switch (frame.traceFrameType())
        {
            case MODEL_CALL, PLANNING, TOOL_INVOCATION, STEP_EXECUTION -> false;
            default -> true;
        };
    }
}
