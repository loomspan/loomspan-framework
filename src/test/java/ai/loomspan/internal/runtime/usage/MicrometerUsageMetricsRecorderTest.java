package ai.loomspan.internal.runtime.usage;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.core.ModelExecutionIdentity;
import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.linter.LinterOutcomeStatus;
import ai.loomspan.internal.provider.ProviderFailureCategory;
import ai.loomspan.internal.provider.ProviderRetryDecision;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerUsageMetricsRecorderTest {

    @Test
    void emitsMicrometerMetersForModelToolLinterAndGuardrailEvents() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MicrometerUsageMetricsRecorder recorder = new MicrometerUsageMetricsRecorder(meterRegistry);

        recorder.recordSkillInvocation("default-root_skill");
        recorder.recordModelUsage("default-root_skill",
                new ModelExecutionIdentity("test-model", "test-connection", AiDriver.OPENAI, "provider-model"),
                new ModelUsageRecord(3, 5, 8, UsagePrecision.HEURISTIC, null));
        recorder.recordProviderAttempt("default-root_skill",
                new ModelExecutionIdentity("test-model", "test-connection", AiDriver.OPENAI, "provider-model"),
                "failed", ProviderFailureCategory.RATE_LIMITED, ProviderRetryDecision.RETRY);
        recorder.recordToolInvocation("default-root_skill", "default-tool_one", "success");
        recorder.recordToolAccuracy("default-root_skill", "regex", "inaccurate");
        recorder.recordLinterOutcome(new LinterOutcome("default-root_skill", "regex", 2, 1, 2, LinterOutcomeStatus.PASSED, "ok"));
        recorder.recordGuardrailTrip("default-root_skill", GuardrailType.MAX_MODEL_CALLS);

        assertThat(meterRegistry.get("loomspan.skill.calls").tag("skill", "default-root_skill").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("loomspan.model.calls").tag("skill", "default-root_skill")
                .tag("connection", "test-connection").tag("driver", "openai")
                .tag("precision", "HEURISTIC").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("loomspan.model.usage.units").tag("skill", "default-root_skill").tag("precision", "HEURISTIC").counter().count()).isEqualTo(8.0d);
        assertThat(meterRegistry.get("loomspan.provider.attempts").tag("skill", "default-root_skill")
                .tag("model", "test-model").tag("connection", "test-connection").tag("driver", "openai")
                .tag("outcome", "failed").tag("category", "rate_limited").tag("decision", "retry")
                .counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("loomspan.tool.calls").tag("skill", "default-root_skill").tag("tool", "default-tool_one").tag("outcome", "success").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("loomspan.tool.accuracy.samples").tag("skill", "default-root_skill").tag("linter", "regex").tag("outcome", "inaccurate").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("loomspan.linter.outcomes").tag("skill", "default-root_skill").tag("status", "PASSED").tag("linter", "regex").counter().count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("loomspan.guardrail.trips").tag("skill", "default-root_skill").tag("guardrail", "MAX_MODEL_CALLS").tag("outcome", "quota_exceeded").counter().count()).isEqualTo(1.0d);
    }
    @Test
    void preservesCaseSensitiveQualifiedSkillTags() {
        var registry = new SimpleMeterRegistry();
        var recorder = new MicrometerUsageMetricsRecorder(registry);
        for (String name : new String[]{"a-search", "a-Search", "b-search"}) {
            recorder.recordSkillInvocation(name);
            recorder.recordToolInvocation(name, name, "SUCCESS");
            assertThat(registry.get("loomspan.tool.calls").tag("skill", name).tag("tool", name)
                    .tag("outcome", "success").counter().count()).isEqualTo(1);
            assertThat(registry.get("loomspan.skill.calls").tag("skill", name).counter().count()).isEqualTo(1);
        }
        recorder.recordSkillInvocation(null);
        recorder.recordSkillInvocation(" ");
        assertThat(registry.get("loomspan.skill.calls").tag("skill", "unknown").counter().count()).isEqualTo(2);
    }

}
