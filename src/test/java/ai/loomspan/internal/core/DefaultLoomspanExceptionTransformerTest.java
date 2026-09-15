package ai.loomspan.internal.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultLoomspanExceptionTransformerTest {

    private final DefaultLoomspanExceptionTransformer transformer = new DefaultLoomspanExceptionTransformer();

    @Test
    void usesRootCauseWhenFormattingErrorPayload() {
        RuntimeException throwable = new IllegalStateException("wrapper",
                new IllegalArgumentException("boom"));

        assertThat(transformer.transform(throwable)).isEqualTo("ERROR: IllegalArgumentException. HINT: boom");
    }

    @Test
    void usesFallbackHintWhenResolvedCauseMessageIsBlank() {
        RuntimeException throwable = new IllegalStateException("wrapper",
                new IllegalArgumentException("   "));

        assertThat(transformer.transform(throwable))
                .isEqualTo("ERROR: IllegalArgumentException. HINT: No additional details provided.");
    }
}
