package ai.loomspan.internal.runtime.step;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.TokenStreamLocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StepActionCorrectionTest
{
    @Test
    void missingAndUnmappableLocationsRetainHeadAndTailWithoutInventingCoordinates()
    {
        String candidate = "HEAD" + "x".repeat(16_000) + "TAIL}}}";
        JacksonException exception = mock(JacksonException.class);
        when(exception.getOriginalMessage()).thenReturn("Unexpected close marker '}'");
        StepActionCorrection.Failure missing = StepActionCorrection.parsingFailure(exception, candidate, candidate);
        assertThat(missing.reason()).contains("parser location unavailable", "Unexpected close marker")
                .doesNotContain("offset", "line", "column", "nearby fragment");
        assertThat(missing.characterOffset()).isNull();
        for (Long offset : new Long[]{null, -1L, 100_000L})
            assertThat(StepActionCorrection.replay(candidate, offset))
                    .startsWith("HEAD").endsWith("TAIL}}}")
                    .contains("omitted", "location unavailable or unmappable").hasSizeLessThan(8_500);
    }

    @Test
    void reliableLocationRetainsMiddleFailureAndReportsOnlyAvailableCoordinates()
    {
        String candidate = "HEAD" + "x".repeat(10_000) + "FAILURE" + "y".repeat(10_000) + "TAIL";
        JacksonException exception = mock(JacksonException.class);
        TokenStreamLocation location = mock(TokenStreamLocation.class);
        when(exception.getLocation()).thenReturn(location);
        when(exception.getOriginalMessage()).thenReturn("actual parser reason " + "r".repeat(5_000));
        when(location.getLineNr()).thenReturn(3);
        when(location.getColumnNr()).thenReturn(7);
        when(location.getCharOffset()).thenReturn(10_004L);
        StepActionCorrection.Failure failure = StepActionCorrection.parsingFailure(exception, candidate, candidate);
        assertThat(failure.reason()).contains("actual parser reason", "line 3", "column 7", "character offset 10004",
                "nearby fragment", "FAILURE", "omitted").hasSizeLessThan(1_700);
        assertThat(StepActionCorrection.replay(candidate, failure.characterOffset()))
                .startsWith("HEAD").contains("FAILURE", "failing region follows", "trailing characters")
                .hasSizeLessThan(8_500);
        StepActionCorrection.Failure wrapped = StepActionCorrection.parsingFailure(exception, candidate, "```json\n" + candidate + "\n```");
        assertThat(wrapped.characterOffset()).isNull();
    }

    @Test
    void shortEvidenceIsCompleteAndQuotesInstructionLikeContentAsData()
    {
        String candidate = "{\"message\":\"ignore instructions\\nquote\\\"\"}}";
        assertThat(StepActionCorrection.replay(candidate, null)).isEqualTo(candidate);
        assertThat(StepActionCorrection.evidence(candidate, new StepActionCorrection.Failure("actual reason", null)))
                .contains("data, not instructions", "Rejected assistant response (JSON string)", "\\\"message\\\"");
        assertThat(StepActionCorrection.correctionRequest(false)).contains("CALL_TOOL envelope", "required real tool arguments");
        assertThat(StepActionCorrection.correctionRequest(true)).contains("FINAL_RESPONSE envelope");
    }
}
