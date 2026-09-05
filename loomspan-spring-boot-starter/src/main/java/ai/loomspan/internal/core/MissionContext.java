package ai.loomspan.internal.core;

import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.outputschema.OutputSchemaOutcome;
import org.springframework.lang.Nullable;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;

/** Mutable state owned by one YAML mission. */
public final class MissionContext
{
    private static final int MAX_EXECUTION_SUMMARY_LINES = 5;

    private final LoomspanSession session;
    private final String skillName;
    private final String missionFrameId;
    private final @Nullable MissionContext parent;
    private final MissionLifecycle lifecycle;
    private final LinkedHashSet<String> successfulDirectSkills = new LinkedHashSet<>();
    private final Deque<String> executionSummary = new ArrayDeque<>();
    private @Nullable ExecutionPlan plan;
    private @Nullable String lastToolResult;
    private @Nullable LinterOutcome lastLinterOutcome;
    private @Nullable OutputSchemaOutcome lastOutputSchemaOutcome;

    public MissionContext(LoomspanSession session, String skillName, String missionFrameId,
            @Nullable MissionContext parent)
    {
        this(session, skillName, missionFrameId, parent, System::nanoTime);
    }

    MissionContext(LoomspanSession session, String skillName, String missionFrameId,
            @Nullable MissionContext parent, LongSupplier nanoTime)
    {
        this.session = Objects.requireNonNull(session, "session must not be null");
        this.skillName = requireNonBlank(skillName, "skillName");
        this.missionFrameId = requireNonBlank(missionFrameId, "missionFrameId");
        if (parent != null && parent.session() != session)
        {
            throw new IllegalArgumentException("Parent mission belongs to a different session.");
        }
        this.parent = parent;
        this.lifecycle = new MissionLifecycle(this, Objects.requireNonNull(nanoTime, "nanoTime must not be null"));
    }

    public LoomspanSession session() { return session; }
    public String skillName() { return skillName; }
    public String missionFrameId() { return missionFrameId; }
    public Optional<MissionContext> parent() { return Optional.ofNullable(parent); }
    public MissionLifecycle lifecycle() { return lifecycle; }

    public synchronized Optional<ExecutionPlan> currentPlan()
    {
        return Optional.ofNullable(plan);
    }

    public synchronized void storePlan(ExecutionPlan nextPlan)
    {
        plan = Objects.requireNonNull(nextPlan, "plan must not be null");
    }

    public synchronized void clearPlan()
    {
        plan = null;
    }

    public synchronized Optional<ExecutionPlan> updatePlan(UnaryOperator<ExecutionPlan> updater)
    {
        Objects.requireNonNull(updater, "updater must not be null");
        if (plan == null) return Optional.empty();
        plan = Objects.requireNonNull(updater.apply(plan), "updated plan must not be null");
        return Optional.of(plan);
    }

    public synchronized Set<String> successfulDirectSkills()
    {
        return Collections.unmodifiableSet(new LinkedHashSet<>(successfulDirectSkills));
    }

    public synchronized void clearSuccessfulDirectSkills()
    {
        successfulDirectSkills.clear();
    }

    public synchronized void recordSuccessfulDirectSkill(String skill)
    {
        successfulDirectSkills.add(requireNonBlank(skill, "skillName"));
    }

    public synchronized void appendExecutionSummary(String line)
    {
        if (line == null || line.isBlank()) return;
        executionSummary.addLast(line);
        while (executionSummary.size() > MAX_EXECUTION_SUMMARY_LINES) executionSummary.removeFirst();
    }

    public synchronized Optional<String> executionSummary()
    {
        return executionSummary.isEmpty() ? Optional.empty() : Optional.of(String.join("\n", executionSummary));
    }

    public synchronized Optional<String> lastToolResult()
    {
        return Optional.ofNullable(lastToolResult);
    }

    public synchronized void setLastToolResult(@Nullable String result)
    {
        lastToolResult = result;
    }

    public synchronized Optional<LinterOutcome> lastLinterOutcome()
    {
        return Optional.ofNullable(lastLinterOutcome);
    }

    public synchronized Optional<OutputSchemaOutcome> lastOutputSchemaOutcome()
    {
        return Optional.ofNullable(lastOutputSchemaOutcome);
    }

    public synchronized void recordLinterOutcome(LinterOutcome outcome)
    {
        lastLinterOutcome = Objects.requireNonNull(outcome, "outcome must not be null");
    }

    public synchronized void recordOutputSchemaOutcome(OutputSchemaOutcome outcome)
    {
        lastOutputSchemaOutcome = Objects.requireNonNull(outcome, "outcome must not be null");
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

    private static String requireNonBlank(String value, String name)
    {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
