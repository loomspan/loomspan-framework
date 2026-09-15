package ai.loomspan.internal.springai;

import ai.loomspan.internal.chat.SkillAdvisorResolver;
import ai.loomspan.internal.chat.SkillChatModelResolver;
import ai.loomspan.internal.model.ModelInteraction;
import ai.loomspan.internal.model.ModelInteractionFactory;
import ai.loomspan.internal.model.ModelInteractionMode;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import ai.loomspan.internal.runtime.state.ExecutionStateService;
import ai.loomspan.internal.runtime.usage.ModelUsageExtractor;
import ai.loomspan.internal.runtime.usage.SessionUsageService;
import io.micrometer.observation.ObservationRegistry;

import java.util.Objects;

public final class SpringAiModelInteractionFactory implements ModelInteractionFactory
{
    private final SpringAiChatClientAssembler assembler;

    public SpringAiModelInteractionFactory(SkillChatModelResolver chatModelResolver,
            SkillAdvisorResolver skillAdvisorResolver,
            ExecutionStateService executionStateService,
            ModelUsageExtractor modelUsageExtractor,
            SessionUsageService sessionUsageService,
            SpringAiChatOptionsContributor optionsContributor,
            ObservationRegistry observationRegistry)
    {
        this.assembler = new SpringAiChatClientAssembler(chatModelResolver, optionsContributor,
                skillAdvisorResolver, executionStateService, modelUsageExtractor, sessionUsageService,
                observationRegistry);
    }

    @Override
    public ModelInteraction create(YamlSkillDefinition definition, ModelInteractionMode mode)
    {
        return new SpringAiModelInteraction(mode == ModelInteractionMode.STEP_EXECUTION
                ? assembler.createForStepExecution(definition)
                : assembler.create(definition));
    }
}
