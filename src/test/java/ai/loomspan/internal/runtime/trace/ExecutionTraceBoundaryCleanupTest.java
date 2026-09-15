package ai.loomspan.internal.runtime.trace;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionTraceBoundaryCleanupTest {

    @Test
    void planningAndMissionDoNotEmitModelTraceTaxonomyDirectly() throws Exception {
        assertThat(source("ai/loomspan/internal/runtime/planning/DefaultPlanningService.java"))
                .doesNotContain("TraceRecordType.MODEL_REQUEST_PREPARED")
                .doesNotContain("TraceRecordType.MODEL_REQUEST_SENT")
                .doesNotContain("TraceRecordType.MODEL_RESPONSE_RECEIVED")
                .doesNotContain("recordTrace(");

        assertThat(source("ai/loomspan/internal/runtime/DefaultMissionExecutionEngine.java"))
                .doesNotContain("TraceRecordType.MODEL_REQUEST_PREPARED")
                .doesNotContain("TraceRecordType.MODEL_REQUEST_SENT")
                .doesNotContain("TraceRecordType.MODEL_RESPONSE_RECEIVED")
                .doesNotContain("recordTrace(");
    }

    @Test
    void advisorsDoNotImportTraceRecordType() throws Exception {
        assertThat(source("ai/loomspan/internal/linter/LinterCallAdvisor.java"))
                .doesNotContain("import ai.loomspan.internal.core.TraceRecordType;");

        assertThat(source("ai/loomspan/internal/outputschema/OutputSchemaCallAdvisor.java"))
                .doesNotContain("import ai.loomspan.internal.core.TraceRecordType;");
    }

    private static String source(String relativePath) throws IOException {
        Path path = Path.of("src/main/java").resolve(relativePath);
        return Files.readString(path);
    }
}
