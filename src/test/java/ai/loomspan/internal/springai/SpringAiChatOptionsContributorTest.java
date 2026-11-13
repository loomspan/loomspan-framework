package ai.loomspan.internal.springai;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.responses.OpenAiResponsesChatOptions;
import com.anthropic.models.messages.OutputConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAiChatOptionsContributorTest
{
    private final SpringAiChatOptionsContributor contributor = new SpringAiChatOptionsContributor();

    @Test
    void providerOptionContributorsApplyImmutableTargetOptions()
    {
        OpenAiResponsesChatOptions firstOpenAi = (OpenAiResponsesChatOptions) contributor.createOptions(configuration(
                AiDriver.OPENAI, "openai/gpt-5", "high")).build();
        OpenAiResponsesChatOptions secondOpenAi = (OpenAiResponsesChatOptions) contributor.createOptions(configuration(
                AiDriver.OPENAI, "openai/gpt-4.1", null)).build();

        assertThat(firstOpenAi.getModel()).isEqualTo("openai/gpt-5");
        assertThat(firstOpenAi.getTemperature()).isEqualTo(1.0);
        assertThat(firstOpenAi.getReasoningEffort()).isEqualTo("high");
        assertThat(secondOpenAi.getModel()).isEqualTo("openai/gpt-4.1");
        assertThat(secondOpenAi.getTemperature()).isNull();
        assertThat(secondOpenAi.getReasoningEffort()).isNull();

        AnthropicChatOptions anthropic = (AnthropicChatOptions) contributor.createOptions(configuration(
                AiDriver.ANTHROPIC, "claude-sonnet-4", "medium")).build();
        assertThat(anthropic.getModel()).isEqualTo("claude-sonnet-4");
        assertThat(anthropic.getThinking()).isNotNull();

        GoogleGenAiChatOptions gemini = (GoogleGenAiChatOptions) contributor.createOptions(configuration(
                AiDriver.GEMINI, "gemini-2.5-pro", "low")).build();
        assertThat(gemini.getModel()).isEqualTo("gemini-2.5-pro");
        assertThat(gemini.getIncludeThoughts()).isTrue();
        assertThat(gemini.getThinkingBudget()).isEqualTo(1024);

        OpenAiResponsesChatOptions ollama = (OpenAiResponsesChatOptions) contributor.createOptions(configuration(
                AiDriver.OPENAI, "qwen3:8b", null)).build();
        assertThat(ollama.getModel()).isEqualTo("qwen3:8b");
    }

    @ParameterizedTest
    @CsvSource({
            "low, 1024, 4096",
            "medium, 4096, 8192",
            "high, 8192, 16384"
    })
    void anthropicRequestOptionsKeepThinkingBudgetBelowMaxTokens(
            String level, long expectedBudget, int expectedMaxTokens)
    {
        AnthropicChatOptions options = (AnthropicChatOptions) contributor.createOptions(configuration(
                AiDriver.ANTHROPIC, "claude-sonnet-4", level)).build();

        assertThat(options.getThinking()).isNotNull();
        assertThat(options.getThinking().asEnabled().budgetTokens()).isEqualTo(expectedBudget);
        assertThat(options.getMaxTokens()).isEqualTo(expectedMaxTokens);
        assertThat(options.getThinking().asEnabled().budgetTokens()).isLessThan(options.getMaxTokens().longValue());
    }

    @ParameterizedTest
    @CsvSource({"low, LOW", "medium, MEDIUM", "high, HIGH"})
    void selectedAnthropicNativeEffortDoesNotInflateGenerationCap(String level, String effort)
    {
        AnthropicChatOptions options = (AnthropicChatOptions) contributor.createOptions(configuration(
                AiDriver.ANTHROPIC, "claude-sonnet-5-5", level)).build();
        assertThat(options.getMaxTokens()).isEqualTo(AnthropicChatOptions.DEFAULT_MAX_TOKENS);
        assertThat(options.getThinking().isAdaptive()).isTrue();
        assertThat(options.getOutputConfig().effort()).contains(OutputConfig.Effort.of(effort.toLowerCase()));
    }

    @Test
    void selectedAnthropicThinkingIsOptionalAndUnsupportedLevelsFailLocally()
    {
        AnthropicChatOptions options = (AnthropicChatOptions) contributor.createOptions(configuration(
                AiDriver.ANTHROPIC, "claude-sonnet-5-5", null)).build();
        assertThat(options.getThinking()).isNull();
        assertThat(options.getOutputConfig()).isNull();
        assertThatThrownBy(() -> contributor.createOptions(configuration(
                AiDriver.ANTHROPIC, "claude-sonnet-5-5", "unsupported")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unsupported thinking level 'unsupported'");
    }

    private EffectiveSkillExecutionConfiguration configuration(AiDriver driver, String model, String thinking)
    {
        return new EffectiveSkillExecutionConfiguration("alias", "connection", driver, model, thinking);
    }
}
