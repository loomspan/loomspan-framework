package ai.loomspan.internal.skillapi;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.LoomspanSessionRunner;
import ai.loomspan.internal.core.CapabilityExecutionRouter;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.CapabilityRegistry;
import ai.loomspan.internal.runtime.input.SkillInputContract;
import ai.loomspan.internal.runtime.input.SkillInputValidationResult;
import ai.loomspan.internal.runtime.input.SkillInputValidator;
import ai.loomspan.internal.security.SkillRoleEvaluator;
import ai.loomspan.api.SkillException;
import ai.loomspan.api.SkillExecutionView;
import ai.loomspan.api.SkillInputValidationException;
import ai.loomspan.api.SkillInputValidationIssue;
import ai.loomspan.api.SkillTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class DefaultSkillTemplate implements SkillTemplate
{
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>()
    {
    };

    private final CapabilityRegistry capabilityRegistry;
    private final CapabilityExecutionRouter executionRouter;
    private final LoomspanSessionRunner sessionRunner;
    private final ObjectMapper objectMapper;
    private final SkillInputValidator inputValidator;
    private final SkillExecutionViewMapper executionViewMapper;
    private final SkillRoleEvaluator roleEvaluator;
    private final SecurityContextHolderStrategy securityContextStrategy;

    public DefaultSkillTemplate(CapabilityRegistry capabilityRegistry,
            CapabilityExecutionRouter executionRouter,
            LoomspanSessionRunner sessionRunner,
            ObjectMapper objectMapper,
            SkillInputValidator inputValidator,
            SkillRoleEvaluator roleEvaluator,
            @Nullable SecurityContextHolderStrategy securityContextStrategy)
    {
        this(capabilityRegistry, executionRouter, sessionRunner, objectMapper, inputValidator,
                roleEvaluator, securityContextStrategy, new SkillExecutionViewMapper(objectMapper));
    }

    DefaultSkillTemplate(CapabilityRegistry capabilityRegistry,
            CapabilityExecutionRouter executionRouter,
            LoomspanSessionRunner sessionRunner,
            ObjectMapper objectMapper,
            SkillInputValidator inputValidator,
            SkillRoleEvaluator roleEvaluator,
            @Nullable SecurityContextHolderStrategy securityContextStrategy,
            SkillExecutionViewMapper executionViewMapper)
    {
        this.capabilityRegistry = Objects.requireNonNull(capabilityRegistry, "capabilityRegistry must not be null");
        this.executionRouter = Objects.requireNonNull(executionRouter, "executionRouter must not be null");
        this.sessionRunner = Objects.requireNonNull(sessionRunner, "sessionRunner must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.inputValidator = Objects.requireNonNull(inputValidator, "inputValidator must not be null");
        this.executionViewMapper = Objects.requireNonNull(executionViewMapper, "executionViewMapper must not be null");
        this.roleEvaluator = Objects.requireNonNull(roleEvaluator, "roleEvaluator must not be null");
        this.securityContextStrategy = securityContextStrategy == null
                ? SecurityContextHolder.getContextHolderStrategy() : securityContextStrategy;
    }

    @Override
    public void validate(String skillName, Object input)
    {
        validatePrepared(skillName, prepareObject(skillName, input));
    }

    @Override
    public void validate(String skillName, Map<String, Object> input)
    {
        validatePrepared(skillName, prepareMap(skillName, input));
    }

    @Override
    public String invoke(String skillName, Object input)
    {
        return invoke(skillName, input, null);
    }

    @Override
    public String invoke(String skillName, Map<String, Object> input)
    {
        return invoke(skillName, input, null);
    }

    @Override
    public String invoke(String skillName, Object input, Consumer<SkillExecutionView> observer)
    {
        return invokePrepared(skillName, prepareObject(skillName, input), observer);
    }

    @Override
    public String invoke(String skillName, Map<String, Object> input, Consumer<SkillExecutionView> observer)
    {
        return invokePrepared(skillName, prepareMap(skillName, input), observer);
    }

    PreparedInput prepareObject(String skillName, Object input)
    {
        if (input == null)
        {
            throw new SkillInputValidationException("Skill input must not be null.", List.of());
        }
        try
        {
            return prepareMap(skillName, objectMapper.convertValue(input, MAP_TYPE));
        }
        catch (AccessDeniedException | SkillException ex)
        {
            throw ex;
        }
        catch (RuntimeException ex)
        {
            throw new SkillException("Skill '" + skillName + "' execution failed.", ex);
        }
    }

    PreparedInput prepareMap(String skillName, Map<String, Object> input)
    {
        try
        {
            CapabilityMetadata capability = requireSkill(skillName);
            SkillInputContract contract = capability.inputContract();
            SkillInputValidationResult validation = inputValidator.validate(normalizeNullInput(input, contract), contract);
            if (!validation.valid())
            {
                List<SkillInputValidationIssue> issues = validation.issues().stream()
                        .map(issue -> new SkillInputValidationIssue(issue.path(), issue.code(), issue.message()))
                        .toList();
                throw new SkillInputValidationException(buildValidationMessage(skillName, validation), issues);
            }
            return new PreparedInput(capability, validation);
        }
        catch (AccessDeniedException | SkillException ex)
        {
            throw ex;
        }
        catch (RuntimeException ex)
        {
            throw new SkillException("Skill '" + skillName + "' execution failed.", ex);
        }
    }

    private void validatePrepared(String skillName, PreparedInput prepared)
    {
        try
        {
            Authentication authentication = securityContextStrategy.getContext().getAuthentication();
            roleEvaluator.checkAccess(prepared.capability().name(), prepared.capability().accessPolicy(), authentication);
        }
        catch (AccessDeniedException | SkillException ex)
        {
            throw ex;
        }
        catch (RuntimeException ex)
        {
            throw new SkillException("Skill '" + skillName + "' execution failed.", ex);
        }
    }

    private String invokePrepared(String skillName, PreparedInput prepared, Consumer<SkillExecutionView> observer)
    {
        return invokePrepared(skillName, prepared, observer, null);
    }

    String invokePrepared(String skillName, PreparedInput prepared, Consumer<SkillExecutionView> observer,
            @Nullable ai.loomspan.internal.core.FrameworkExecutionLifecycle.AdmittedRoot admittedRoot)
    {
        Authentication authentication;
        try
        {
            authentication = currentAuthentication();
        }
        catch (AccessDeniedException | SkillException ex)
        {
            throw ex;
        }
        catch (RuntimeException ex)
        {
            throw new SkillException("Skill '" + skillName + "' execution failed.", ex);
        }
        return invokePrepared(skillName, prepared, observer, authentication, admittedRoot);
    }

    String invokePrepared(String skillName, PreparedInput prepared, Consumer<SkillExecutionView> observer,
            @Nullable Authentication authentication,
            @Nullable ai.loomspan.internal.core.FrameworkExecutionLifecycle.AdmittedRoot admittedRoot)
    {
        try
        {
            java.util.function.Function<LoomspanSession, String> action =
                    session -> executeValidated(prepared.capability(), prepared.validation(), session);
            LoomspanSessionRunner.RootCompletion<String, String> completion = (result, session, failure) -> {
                if (observer != null) observer.accept(executionViewMapper.map(session));
                return result;
            };
            return admittedRoot == null
                    ? sessionRunner.callWithNewSession(
                            prepared.capability().name(), authentication, action, completion)
                    : sessionRunner.callWithAdmittedSession(
                            prepared.capability().name(), authentication, admittedRoot, action, completion);
        }
        catch (LoomspanSessionRunner.CompletionPhaseFailure ex)
        {
            throw ex.original();
        }
        catch (AccessDeniedException | SkillException ex)
        {
            throw ex;
        }
        catch (RuntimeException ex)
        {
            throw new SkillException("Skill '" + skillName + "' execution failed.", ex);
        }
    }

    Authentication currentAuthentication()
    {
        return securityContextStrategy.getContext().getAuthentication();
    }

    private String executeValidated(CapabilityMetadata capability,
            SkillInputValidationResult validation,
            LoomspanSession session)
    {
        Object result = executionRouter.execute(capability, validation.normalizedInput(), session, null);
        return String.valueOf(result);
    }

    private CapabilityMetadata requireSkill(String skillName)
    {
        CapabilityMetadata capability = capabilityRegistry.getCapability(skillName);
        if (capability == null)
        {
            throw new SkillException("Unknown skill '" + skillName + "'");
        }
        if (!skillName.equals(capability.name()))
        {
            throw new SkillException("CapabilityRegistry returned skill '" + capability.name()
                    + "' for requested name '" + skillName + "'");
        }

        return capability;
    }

    private Map<String, Object> normalizeNullInput(Map<String, Object> input, SkillInputContract contract)
    {
        if (input != null)
        {
            return input;
        }
        if (contract.isGeneric() || contract.allowsEmptyInput())
        {
            return Map.of();
        }

        throw new SkillInputValidationException("Skill input must not be null for contract-backed skills.", List.of());
    }

    private String buildValidationMessage(String skillName, SkillInputValidationResult validation)
    {
        String detail = validation.issues().stream()
                .map(issue -> (issue.path() == null || issue.path().isBlank() ? "<root>" : issue.path()) + ": " + issue.message())
                .reduce((left, right) -> left + "; " + right)
                .orElse("Invalid skill input.");

        return "Invalid input for skill '" + skillName + "': " + detail;
    }

    record PreparedInput(CapabilityMetadata capability, SkillInputValidationResult validation)
    {
    }
}
