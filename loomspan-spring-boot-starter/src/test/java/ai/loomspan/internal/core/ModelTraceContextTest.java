package ai.loomspan.internal.core;

import ai.loomspan.autoconfigure.AiDriver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTraceContextTest {

    @Test
    void oneContextAllocatesMonotonicAttemptsInOneSequence() {
        ModelTraceContext context = context();

        Map<String, Object> first = context.nextAttempt();
        Map<String, Object> second = context.nextAttempt();

        assertThat(first.get("retrySequenceId")).isEqualTo(second.get("retrySequenceId"));
        assertThat(first.get("attemptId")).isNotEqualTo(second.get("attemptId"));
        assertThat(first.get("attemptNumber")).isEqualTo(1);
        assertThat(second.get("attemptNumber")).isEqualTo(2);
    }

    @Test
    void separateLogicalCallsUseSeparateRetrySequences() {
        ModelTraceContext first = context();
        ModelTraceContext second = context();

        assertThat(first.retrySequenceId()).isNotEqualTo(second.retrySequenceId());
        assertThat(first.nextAttempt().get("attemptNumber")).isEqualTo(1);
        assertThat(second.nextAttempt().get("attemptNumber")).isEqualTo(1);
    }

    @Test
    void concurrentAllocationProducesUniqueContiguousAttemptNumbers() throws Exception {
        ModelTraceContext context = context();
        List<Callable<Map<String, Object>>> tasks = IntStream.range(0, 32)
                .mapToObj(ignored -> (Callable<Map<String, Object>>) context::nextAttempt)
                .toList();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Map<String, Object>> attempts = executor.invokeAll(tasks).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        }
                        catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    })
                    .toList();

            assertThat(attempts).extracting(attempt -> attempt.get("retrySequenceId"))
                    .containsOnly(context.retrySequenceId());
            assertThat(attempts).extracting(attempt -> attempt.get("attemptId"))
                    .doesNotHaveDuplicates();
            assertThat(attempts).extracting(attempt -> (Integer) attempt.get("attemptNumber"))
                    .containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, 32).boxed().toList());
        }
    }

    private static ModelTraceContext context() {
        return new ModelTraceContext(
                new ModelExecutionIdentity("test", "connection", AiDriver.OPENAI, "provider/model"),
                "test.skill",
                "mission");
    }
}
