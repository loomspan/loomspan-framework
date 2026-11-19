package ai.loomspan.internal.core;

import ai.loomspan.api.*;
import ai.loomspan.internal.runtime.input.SkillInputValidationResult;
import ai.loomspan.internal.outputschema.OutputSchemaValidationResult;
import java.util.List;

/** Framework-owned contract verdicts. Ownership prevents reuse across invocations. */
public final class ContractFailures
{
    private ContractFailures() { }

    public interface Owned
    {
        String sessionId();
        SkillFailureDiagnosis diagnosis();
    }

    public static SkillFailureDiagnosis input(String name, String message, SkillInputValidationResult result)
    {
        return new SkillFailureDiagnosis(name, SkillContractStage.INPUT_SCHEMA, message,
                result.issues().stream().map(i -> new SkillContractIssue(i.path(), i.code(), i.message())).toList());
    }

    public static SkillFailureDiagnosis output(String name, String message, OutputSchemaValidationResult result)
    {
        return result.contractIssues().isEmpty() ? null
                : new SkillFailureDiagnosis(name, SkillContractStage.OUTPUT_SCHEMA, message, result.contractIssues());
    }

    public static String currentSessionId()
    {
        return ExecutionBindingScope.current().map(b -> b.session().getSessionId()).orElse(null);
    }

    public static final class Input extends SkillInputValidationException implements Owned
    {
        private final String sessionId;
        public Input(String sessionId, String name, String message, SkillInputValidationResult result)
        {
            super(message, result.issues().stream()
                    .map(i -> new SkillInputValidationIssue(i.path(), i.code(), i.message())).toList(),
                    input(name, message, result));
            this.sessionId = sessionId;
        }
        public String sessionId() { return sessionId; }
        public SkillFailureDiagnosis diagnosis() { return getFailureDiagnosis().orElseThrow(); }
    }

    public static final class Terminal extends IllegalStateException implements Owned
    {
        private final String sessionId;
        private final SkillFailureDiagnosis diagnosis;
        public Terminal(String sessionId, String message, SkillFailureDiagnosis diagnosis)
        {
            super(message);
            this.sessionId = sessionId;
            this.diagnosis = diagnosis;
        }
        public String sessionId() { return sessionId; }
        public SkillFailureDiagnosis diagnosis() { return diagnosis; }
    }

    public static final class Transform extends IllegalArgumentException implements Owned
    {
        private final String sessionId;
        private final SkillFailureDiagnosis diagnosis;
        public Transform(String sessionId, String name, SkillContractStage stage, String message, Throwable cause)
        {
            super(message, cause);
            this.sessionId = sessionId;
            this.diagnosis = new SkillFailureDiagnosis(name, stage, message, List.of());
        }
        public Transform ownedBy(String sessionId)
        {
            return new Transform(sessionId, diagnosis.skillName(), diagnosis.stage(), getMessage(), getCause());
        }
        public String sessionId() { return sessionId; }
        public SkillFailureDiagnosis diagnosis() { return diagnosis; }
    }
}
