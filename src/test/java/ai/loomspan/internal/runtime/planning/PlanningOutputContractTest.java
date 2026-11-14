package ai.loomspan.internal.runtime.planning;

import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.assertThat;

class PlanningOutputContractTest {
    private static final tools.jackson.databind.ObjectMapper JSON = LoomspanJacksonCodecs.defaults().schemaTree();
    private static ObjectNode valid() { return (ObjectNode) JSON.readTree("""
        {"capabilityName":"default-root","createdAt":"2026-01-01T00:00:00Z","status":"VALID","tasks":[{
        "taskId":"one","title":"One","status":"PENDING","capabilityName":"default-child","intent":null,
        "dependsOn":[],"expectedOutputs":null,"parallelGroup":null,"note":null}]}
        """); }
    @Test void generatedShapeIsClosedRequiredAndInitialOnly() {
        var contract = PlanningOutputContract.SCHEMA;
        assertThat(contract.validate(valid())).isEmpty();
        var nullable = valid(); nullable.putNull("status");
        assertThat(contract.validate(nullable)).isEmpty();
        for (String field : new String[]{"capabilityName","createdAt","status","tasks"}) {
            var omitted = valid(); omitted.remove(field);
            assertThat(contract.validate(omitted)).as(field).isNotEmpty();
        }
        for (String field : new String[]{"taskId","title","status","capabilityName","intent","dependsOn","expectedOutputs","parallelGroup","note"}) {
            var omitted = valid(); ((ObjectNode)omitted.at("/tasks/0")).remove(field);
            assertThat(contract.validate(omitted)).as(field).isNotEmpty();
        }
        for (String field : new String[]{"taskId","title","status","capabilityName","dependsOn"}) {
            var candidate = valid(); ((ObjectNode)candidate.at("/tasks/0")).putNull(field);
            assertThat(contract.validate(candidate)).as(field).isNotEmpty();
        }
        for (String status : new String[]{"INVALID","STALE","EXECUTED","valid"}) {
            var candidate = valid(); candidate.put("status",status);
            assertThat(contract.validate(candidate)).isNotEmpty();
        }
        for (String status : new String[]{"COMPLETED","FAILED","IN_PROGRESS","SUCCESS","pending"}) {
            var candidate = valid(); ((ObjectNode)candidate.at("/tasks/0")).put("status",status);
            assertThat(contract.validate(candidate)).isNotEmpty();
        }
        var identity = valid(); identity.put("planId","untrusted");
        assertThat(contract.validate(identity)).isNotEmpty();
        var unknown = valid(); ((ObjectNode)unknown.at("/tasks/0")).put("future",true);
        assertThat(contract.validate(unknown)).isNotEmpty();
    }
}
