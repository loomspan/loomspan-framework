package ai.loomspan.internal.core;

import ai.loomspan.internal.runtime.input.SkillInputValidationResult;
import ai.loomspan.internal.runtime.input.SkillInputValidator;
import ai.loomspan.api.SkillInputValidationException;
import ai.loomspan.api.SkillInputValidationIssue;
import ai.loomspan.internal.security.AccessGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.Objects;

public class CapabilityExecutionRouter
{
    private final ObjectProvider<ExecutionCoordinator> executionCoordinatorProvider;
    private final AccessGuard accessGuard;
    private final SkillInputValidator inputValidator;

    public CapabilityExecutionRouter(ObjectProvider<ExecutionCoordinator> executionCoordinatorProvider,
            AccessGuard accessGuard)
    {
        this(executionCoordinatorProvider, accessGuard, new SkillInputValidator());
    }

    public CapabilityExecutionRouter(ObjectProvider<ExecutionCoordinator> executionCoordinatorProvider,
            AccessGuard accessGuard,
            SkillInputValidator inputValidator)
    {
        this.executionCoordinatorProvider = Objects.requireNonNull(
                executionCoordinatorProvider,
                "executionCoordinatorProvider must not be null");

        this.accessGuard = Objects.requireNonNull(accessGuard, "accessGuard must not be null");
        this.inputValidator = Objects.requireNonNull(inputValidator, "inputValidator must not be null");
    }

    public Object execute(CapabilityMetadata capability,
            Map<String, Object> arguments,
            LoomspanSession session,
            @Nullable Authentication authentication)
    {
        Objects.requireNonNull(capability, "capability must not be null");
        Objects.requireNonNull(session, "session must not be null");

        accessGuard.checkAccess(capability, session, authentication);
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        SkillInputValidationResult validation = inputValidator.validate(safeArguments, capability.inputContract());

        if (!validation.valid())
        {
            throw new SkillInputValidationException(
                    "Invalid input for capability '" + capability.name() + "'",
                    validation.issues().stream()
                            .map(issue -> new SkillInputValidationIssue(issue.path(), issue.code(), issue.message()))
                            .toList());
        }

        Map<String, Object> normalizedInput = validation.normalizedInput();

        ExecutionBinding binding = ExecutionBindingScope.requireCurrent();
        if (binding.session() != session)
        {
            throw new IllegalArgumentException("Skill execution requires the current binding for the explicit session.");
        }
        return executionCoordinatorProvider.getObject().execute(capability.name(),
                objectiveFor(capability, normalizedInput), normalizedInput, session, authentication);
    }

    private String objectiveFor(CapabilityMetadata capability, Map<String, Object> arguments)
    {
        return "Execute skill '%s' using the provided mission input object.".formatted(capability.name());
    }
}
