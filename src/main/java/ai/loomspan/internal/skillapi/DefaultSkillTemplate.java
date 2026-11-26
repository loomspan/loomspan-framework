package ai.loomspan.internal.skillapi;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.LoomspanSessionRunner;
import ai.loomspan.internal.core.CapabilityExecutionRouter;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.SkillIdentity;
import ai.loomspan.internal.skill.SkillGeneration;
import ai.loomspan.internal.skill.SkillGenerationManager;
import ai.loomspan.internal.skill.SkillAdmissionSnapshot;
import ai.loomspan.internal.runtime.input.SkillInputContract;
import ai.loomspan.internal.runtime.input.SkillInputValidationResult;
import ai.loomspan.internal.runtime.input.SkillInputValidator;
import ai.loomspan.internal.security.SkillRoleEvaluator;
import ai.loomspan.api.SkillException;
import ai.loomspan.api.SkillExecutionView;
import ai.loomspan.api.SkillInputValidationException;
import ai.loomspan.api.SkillInputValidationIssue;
import ai.loomspan.api.SkillTemplate;
import ai.loomspan.api.SkillInvocationResult;
import ai.loomspan.api.SkillAccessDeniedException;
import ai.loomspan.api.SkillFailureDiagnosis;
import ai.loomspan.api.RecoverableSkillException;
import ai.loomspan.api.PlannerInabilityException;
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

    private final SkillGenerationManager generationManager;
    private final CapabilityExecutionRouter executionRouter;
    private final LoomspanSessionRunner sessionRunner;
    private final ObjectMapper objectMapper;
    private final SkillInputValidator inputValidator;
    private final SkillExecutionViewMapper executionViewMapper;
    private final SkillRoleEvaluator roleEvaluator;
    private final SecurityContextHolderStrategy securityContextStrategy;

    public DefaultSkillTemplate(SkillGenerationManager generationManager,
            CapabilityExecutionRouter executionRouter,
            LoomspanSessionRunner sessionRunner,
            ObjectMapper objectMapper,
            SkillInputValidator inputValidator,
            SkillRoleEvaluator roleEvaluator,
            @Nullable SecurityContextHolderStrategy securityContextStrategy)
    {
        this(generationManager, executionRouter, sessionRunner, objectMapper, inputValidator,
                roleEvaluator, securityContextStrategy, new SkillExecutionViewMapper(objectMapper));
    }

    DefaultSkillTemplate(SkillGenerationManager generationManager,
            CapabilityExecutionRouter executionRouter,
            LoomspanSessionRunner sessionRunner,
            ObjectMapper objectMapper,
            SkillInputValidator inputValidator,
            SkillRoleEvaluator roleEvaluator,
            @Nullable SecurityContextHolderStrategy securityContextStrategy,
            SkillExecutionViewMapper executionViewMapper)
    {
        this.generationManager = Objects.requireNonNull(generationManager, "generationManager must not be null");
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
    public void validate(String namespace, String localName, Object input)
    {
        validatePrepared(prepareObject(namespace, localName, input));
    }

    @Override
    public void validate(String namespace, String localName, Map<String, Object> input)
    {
        validatePrepared(prepareMap(namespace, localName, input));
    }

    @Override
    public SkillInvocationResult invoke(String namespace, String localName, Object input)
    {
        return invoke(namespace, localName, input, null);
    }

    @Override
    public SkillInvocationResult invoke(String namespace, String localName, Map<String, Object> input)
    {
        return invoke(namespace, localName, input, null);
    }

    @Override
    public SkillInvocationResult invoke(String namespace, String localName, Object input, Consumer<SkillExecutionView> observer)
    {
        return invokePrepared(prepareObject(namespace, localName, input), observer);
    }

    @Override
    public SkillInvocationResult invoke(String namespace, String localName, Map<String, Object> input, Consumer<SkillExecutionView> observer)
    {
        return invokePrepared(prepareMap(namespace, localName, input), observer);
    }

    PreparedInput prepareObject(String namespace, String localName, Object input)
    {
        SkillGenerationManager.Capture captured = captureRoot(namespace, localName);
        try
        {
            if (input == null)
                throw new SkillInputValidationException("Skill input must not be null.", List.of(),
                        new ai.loomspan.api.SkillFailureDiagnosis(captured.snapshot().entry().toString(),
                                ai.loomspan.api.SkillContractStage.INPUT_SCHEMA, "Skill input must not be null.", List.of()));
            return prepareMap(captured, captured.snapshot().entry().toString(), objectMapper.convertValue(input, MAP_TYPE));
        }
        catch (AccessDeniedException | SkillException ex)
        {
            captured.lease().close();
            if (ex instanceof SkillException skillFailure && skillFailure.getSessionId() != null)
                throw failureSnapshot(captured.snapshot().entry().toString(), skillFailure, null, null);
            throw ex;
        }
        catch (RuntimeException ex)
        {
            captured.lease().close();
            throw new SkillException("Skill '" + namespace + ":" + localName + "' execution failed.", ex);
        }
        catch (Error ex)
        {
            captured.lease().close();
            throw ex;
        }
    }

    PreparedInput prepareMap(String namespace, String localName, Map<String, Object> input)
    {
        var captured = captureRoot(namespace, localName);
        return prepareMap(captured, captured.snapshot().entry().toString(), input);
    }

    private SkillGenerationManager.Capture captureRoot(String namespace, String localName)
    {
        try { return generationManager.capture(new SkillIdentity(namespace, localName)); }
        catch (SkillException ex)
        {
            if (ex.getSessionId() != null) throw failureSnapshot(namespace + ":" + localName, ex, null, null);
            throw ex;
        }
        catch (RuntimeException ex)
        {
            throw new SkillException("Skill '" + namespace + ":" + localName + "' execution failed.", ex);
        }
    }

    private PreparedInput prepareMap(SkillGenerationManager.Capture captured, String skillName, Map<String, Object> input)
    {
        try
        {
            SkillGeneration generation = captured.snapshot().entryGeneration();
            CapabilityMetadata capability = requireSkill(generation, skillName);
            SkillInputContract contract = capability.inputContract();
            SkillInputValidationResult validation = inputValidator.validate(normalizeNullInput(input, contract, skillName), contract);
            if (!validation.valid())
            {
                List<SkillInputValidationIssue> issues = validation.issues().stream()
                        .map(issue -> new SkillInputValidationIssue(issue.path(), issue.code(), issue.message()))
                        .toList();
                throw new SkillInputValidationException(buildValidationMessage(skillName, validation), issues,
                        ai.loomspan.internal.core.ContractFailures.input(skillName, buildValidationMessage(skillName, validation), validation));
            }
            var admitted = generationManager.validateAdmission(captured.snapshot());
            return new PreparedInput(admitted, capability, validation, captured.lease());
        }
        catch (AccessDeniedException | SkillException ex)
        {
            captured.lease().close();
            if (ex instanceof SkillException skillFailure && skillFailure.getSessionId() != null)
                throw failureSnapshot(captured.snapshot().entry().toString(), skillFailure, null, null);
            throw ex;
        }
        catch (RuntimeException ex)
        {
            captured.lease().close();
            throw new SkillException("Skill '" + skillName + "' execution failed.", ex);
        }
        catch (Error ex)
        {
            captured.lease().close();
            throw ex;
        }
    }

    private void validatePrepared(PreparedInput prepared)
    {
        String skillName = prepared.capability().name();
        try
        {
            Authentication authentication = securityContextStrategy.getContext().getAuthentication();
            authorize(prepared, authentication);
        }
        catch (AccessDeniedException | SkillException ex)
        {
            throw ex;
        }
        catch (RuntimeException ex)
        {
            throw new SkillException("Skill '" + skillName + "' execution failed.", ex);
        }
        finally { prepared.lease().close(); }
    }

    private SkillInvocationResult invokePrepared(PreparedInput prepared, Consumer<SkillExecutionView> observer)
    {
        return invokePrepared(prepared, observer, null);
    }

    SkillInvocationResult invokePrepared(PreparedInput prepared, Consumer<SkillExecutionView> observer,
            @Nullable ai.loomspan.internal.core.FrameworkExecutionLifecycle.AdmittedRoot admittedRoot)
    {
        String skillName = prepared.capability().name();
        Authentication authentication;
        try
        {
            authentication = currentAuthentication();
            authorize(prepared, authentication);
        }
        catch (AccessDeniedException | SkillException ex)
        {
            prepared.lease().close();
            if (ex instanceof SkillException skillFailure && skillFailure.getSessionId() != null)
                throw failureSnapshot(skillName, skillFailure, null, null);
            throw ex;
        }
        catch (RuntimeException ex)
        {
            prepared.lease().close();
            throw new SkillException("Skill '" + skillName + "' execution failed.", ex);
        }
        catch (Error ex)
        {
            prepared.lease().close();
            throw ex;
        }
        return invokePrepared(prepared, observer, authentication, admittedRoot);
    }

    SkillInvocationResult invokePrepared(PreparedInput prepared, Consumer<SkillExecutionView> observer,
            @Nullable Authentication authentication,
            @Nullable ai.loomspan.internal.core.FrameworkExecutionLifecycle.AdmittedRoot admittedRoot)
    {
        String skillName = prepared.capability().name();
        var selected = new java.util.concurrent.atomic.AtomicReference<ai.loomspan.api.SkillFailureDiagnosis>();
        var sessionId = new java.util.concurrent.atomic.AtomicReference<String>();
        try
        {
            java.util.function.Function<LoomspanSession, String> action =
                    session -> executeValidated(prepared.capability(), prepared.validation(), session);
            LoomspanSessionRunner.RootCompletion<String, SkillInvocationResult> completion = new LoomspanSessionRunner.RootCompletion<>() {
                @Override
                public void started(String id)
                {
                    sessionId.set(id);
                }

                @Override
                public SkillInvocationResult complete(String result, LoomspanSession session, Throwable failure)
                {
                    selected.set(session.terminalDiagnosis().orElse(null));
                    if (observer != null) observer.accept(executionViewMapper.map(session));
                    return failure == null ? new SkillInvocationResult(result, session.getSessionId()) : null;
                }
            };
            return admittedRoot == null
                    ? sessionRunner.callWithNewSession(
                            prepared.capability().name(), prepared.snapshot(), authentication,
                            prepared.lease(), action, completion)
                    : sessionRunner.callWithAdmittedSession(
                            prepared.capability().name(), prepared.snapshot(), authentication, admittedRoot, action, completion);
        }
        catch (AccessDeniedException ex)
        {
            if (sessionId.get() == null) throw ex;
            var failure = new SkillAccessDeniedException(ex.getMessage(), ex, sessionId.get());
            copySuppressed(ex, failure);
            throw failure;
        }
        catch (RuntimeException ex)
        {
            throw failureSnapshot(skillName, ex, selected.get(), sessionId.get());
        }
    }

    static SkillException failureSnapshot(String skillName, RuntimeException original,
            SkillFailureDiagnosis diagnosis, String sessionId)
    {
        SkillException failure;
        if (original instanceof PlannerInabilityException)
            failure = new PlannerInabilityException(original.getMessage(), original, diagnosis, sessionId);
        else if (original instanceof RecoverableSkillException)
            failure = new RecoverableSkillException(original.getMessage(), original, diagnosis, sessionId);
        else if (original instanceof SkillInputValidationException input)
            failure = new SkillInputValidationException(input.getMessage(), original, input.getIssues(), diagnosis, sessionId);
        else
            failure = new SkillException(original instanceof SkillException ? original.getMessage()
                    : "Skill '" + skillName + "' execution failed.", original, diagnosis, sessionId);
        copySuppressed(original, failure);
        return failure;
    }

    private static void copySuppressed(Throwable original, Throwable failure)
    {
        for (Throwable suppressed : original.getSuppressed()) failure.addSuppressed(suppressed);
    }

    Authentication currentAuthentication()
    {
        return securityContextStrategy.getContext().getAuthentication();
    }

    void authorize(PreparedInput prepared, Authentication authentication)
    {
        roleEvaluator.checkAccess(prepared.capability(), prepared.snapshot(), authentication);
    }

    private String executeValidated(CapabilityMetadata capability,
            SkillInputValidationResult validation,
            LoomspanSession session)
    {
        Object result = executionRouter.execute(capability, validation.normalizedInput(), session, null);
        return String.valueOf(result);
    }

    private CapabilityMetadata requireSkill(SkillGeneration generation, String skillName)
    {
        CapabilityMetadata capability = generation.capability(skillName);
        if (capability == null)
        {
            throw new SkillException("Unknown skill '" + skillName + "'");
        }
        if (!skillName.equals(capability.name()))
        {
            throw new SkillException("Skill generation returned skill '" + capability.name()
                    + "' for requested name '" + skillName + "'");
        }

        return capability;
    }

    private Map<String, Object> normalizeNullInput(Map<String, Object> input, SkillInputContract contract, String skillName)
    {
        if (input != null)
        {
            return input;
        }
        if (contract.isGeneric() || contract.allowsEmptyInput())
        {
            return Map.of();
        }

        throw new SkillInputValidationException("Skill input must not be null for contract-backed skills.", List.of(),
                new ai.loomspan.api.SkillFailureDiagnosis(skillName, ai.loomspan.api.SkillContractStage.INPUT_SCHEMA,
                        "Skill input must not be null for contract-backed skills.", List.of()));
    }

    private String buildValidationMessage(String skillName, SkillInputValidationResult validation)
    {
        String detail = validation.issues().stream()
                .map(issue -> (issue.path() == null || issue.path().isBlank() ? "<root>" : issue.path()) + ": " + issue.message())
                .reduce((left, right) -> left + "; " + right)
                .orElse("Invalid skill input.");

        return "Invalid input for skill '" + skillName + "': " + detail;
    }

    record PreparedInput(SkillAdmissionSnapshot snapshot, CapabilityMetadata capability,
            SkillInputValidationResult validation, SkillGenerationManager.OwnerLease lease)
    {
    }
}
