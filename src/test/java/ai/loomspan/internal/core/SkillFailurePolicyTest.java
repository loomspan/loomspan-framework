package ai.loomspan.internal.core;

import ai.loomspan.api.RecoverableSkillException;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.*;
import org.springframework.ai.tool.function.FunctionToolCallback;
import static org.assertj.core.api.Assertions.*;

class SkillFailurePolicyTest {
    @Test void actualFutureAndSpringCallbackWrappersAreTransparent() throws Exception {
        var recoverable = new RecoverableSkillException("  correct\n\u96ea", new Exception("cause"));
        for (RuntimeException application : java.util.List.of(recoverable, new IllegalStateException("outer", recoverable))) {
            try (var worker = Executors.newSingleThreadExecutor()) {
                var task = worker.submit(() -> { throw application; });
                var actual = catchThrowable(task::get);
                assertThat(actual).isInstanceOf(ExecutionException.class);
                assertThat(SkillFailurePolicy.unwrapInvocationFailure(actual)).isSameAs(application);
                assertThat(SkillFailurePolicy.recoverable(actual)).isEqualTo(application == recoverable ? recoverable : null);
            }
            var callback = FunctionToolCallback.<Map<String,Object>,String>builder("failure", (input, context) -> { throw application; })
                    .description("Actual callback").inputType(Map.class).inputSchema("{\"type\":\"object\"}").build();
            var actual = catchThrowable(() -> callback.call("{}"));
            assertThat(actual).isInstanceOf(org.springframework.ai.tool.execution.ToolExecutionException.class);
            assertThat(SkillFailurePolicy.unwrapInvocationFailure(actual)).isSameAs(application);
            assertThat(SkillFailurePolicy.recoverable(actual)).isEqualTo(application == recoverable ? recoverable : null);
        }
    }
    @Test void infrastructureTraversalIsBoundedAndDoesNotSearchApplicationCauses() {
        class Cycle extends ExecutionException { Throwable next; @Override public synchronized Throwable getCause() { return next; } }
        var first = new Cycle(); var second = new Cycle(); first.next = second; second.next = first;
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> {
            assertThat(SkillFailurePolicy.recoverable(first)).isNull();
            assertThat(SkillFailurePolicy.recoverable(new java.lang.reflect.InvocationTargetException(null))).isNull();
            Throwable deep = new RecoverableSkillException("message");
            for (int i=0;i<100;i++) deep = new ExecutionException(deep);
            assertThat(SkillFailurePolicy.recoverable(deep)).isNull();
        });
    }
}
