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
import java.util.List;
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
        return executeInput(capability, arguments, session, authentication, List.of());
    }

    public Object executeAssembled(CapabilityMetadata capability, Map<String, Object> arguments,
            LoomspanSession session, @Nullable Authentication authentication, List<List<String>> exactPaths)
    {
        return executeInput(capability, arguments, session, authentication, exactPaths);
    }

    private Object executeInput(CapabilityMetadata capability, Map<String, Object> arguments,
            LoomspanSession session, @Nullable Authentication authentication, List<List<String>> exactPaths)
    {
        Objects.requireNonNull(capability, "capability must not be null");
        Objects.requireNonNull(session, "session must not be null");

        accessGuard.checkAccess(capability, session, authentication);
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        SkillInputValidationResult validation = inputValidator.validateExact(safeArguments, capability.inputContract(), exactPaths);

        if (!validation.valid())
        {
            throw new SkillInputValidationException(
                    (exactPaths.isEmpty() ? "Invalid input for capability '" : "binding_assembled_input_invalid: Invalid input for capability '") + capability.name() + "'",
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
        if (!binding.generation().owns(capability))
        {
            throw new IllegalArgumentException("Capability '" + capability.name()
                    + "' does not belong to the current skill generation");
        }
        return executionCoordinatorProvider.getObject().execute(capability,
                objectiveFor(capability), normalizedInput, session, authentication);
    }

    private String objectiveFor(CapabilityMetadata capability)
    {
        return "Fulfill the mission for skill '%s' using the provided mission input object.".formatted(capability.name());
    }
}
