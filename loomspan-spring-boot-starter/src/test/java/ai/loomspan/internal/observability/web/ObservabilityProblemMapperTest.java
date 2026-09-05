package ai.loomspan.internal.observability.web;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityProblemMapperTest
{
    @ParameterizedTest
    @CsvSource({
            "401,LOOMSPAN_API_KEY_REJECTED",
            "400,INVALID_REQUEST",
            "400,INVALID_CURSOR",
            "410,STALE_CURSOR",
            "404,NOT_FOUND",
            "503,LIVE_MONITORING_UNAVAILABLE",
            "429,LIMIT_EXCEEDED"
    })
    void preservesApprovedProblems(int status, ObservabilityProblem.Code code)
    {
        ObservabilityProblem problem = new ObservabilityProblemMapper()
                .map(new ObservabilityException(status, code, "safe"));
        assertThat(problem).isEqualTo(new ObservabilityProblem(status, code, "safe"));
    }

    @org.junit.jupiter.api.Test
    void sanitizesUnexpectedFailure()
    {
        ObservabilityProblem problem = new ObservabilityProblemMapper()
                .map(new IllegalStateException("secret path C:\\private"));
        assertThat(problem.status()).isEqualTo(500);
        assertThat(problem.code()).isEqualTo(ObservabilityProblem.Code.APPLICATION_ERROR);
        assertThat(problem.message()).doesNotContain("secret", "private", "IllegalStateException");
    }
}
