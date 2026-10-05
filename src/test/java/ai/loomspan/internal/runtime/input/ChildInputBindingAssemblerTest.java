package ai.loomspan.internal.runtime.input;

import ai.loomspan.internal.core.MissionContext;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ChildInputBindingAssemblerTest
{
    private final ChildInputBindingAssembler assembler = new ChildInputBindingAssembler();

    private ChildInputBinding input(String destination, String source)
    {
        return new ChildInputBinding(ObjectFieldPath.parse(destination, false), ChildInputBinding.SourceKind.INPUT,
                ObjectFieldPath.parse(source, true), null);
    }

    private ChildInputBinding result(String destination, String source)
    {
        return new ChildInputBinding(ObjectFieldPath.parse(destination, false), ChildInputBinding.SourceKind.CHILD_RESULT,
                ObjectFieldPath.parse(source, true), "producer");
    }

    @Test
    @SuppressWarnings("unchecked")
    void consumerMutationCannotChangeParentOrAnotherConsumer()
    {
        var nested = new LinkedHashMap<String,Object>(Map.of("value", "original"));
        var sourceList = new java.util.ArrayList<Object>(List.of(nested));
        var parent = Map.<String,Object>of("records", sourceList);
        var binding = input("/records", "/records");
        var first = assembler.assemble(Map.of(), List.of(binding), parent, List.of(), "p");
        var second = assembler.assemble(Map.of(), List.of(binding), parent, List.of(), "p");
        var firstRecords = (List<Object>) first.arguments().get("records");
        var secondRecords = (List<Object>) second.arguments().get("records");
        assertThat(firstRecords).isNotSameAs(sourceList).isNotSameAs(secondRecords);
        assertThat(firstRecords.getFirst()).isNotSameAs(nested).isNotSameAs(secondRecords.getFirst());
        assertThatThrownBy(() -> ((Map<String,Object>) firstRecords.getFirst()).put("value", "consumer mutation"))
                .isInstanceOf(UnsupportedOperationException.class);
        nested.put("value", "source mutation");
        sourceList.add("new item");
        assertThat(firstRecords).isEqualTo(List.of(Map.of("value", "original")));
        assertThat(secondRecords).isEqualTo(List.of(Map.of("value", "original")));
    }

    @Test
    void assemblesRenamedNestedValuesAndNullWithoutSharingSources()
    {
        var source = new LinkedHashMap<String,Object>();
        source.put("literal.dot", new java.util.ArrayList<>(List.of(Map.of("amount", new BigInteger("1234567890123456789012345")))));
        source.put("nil", null);
        var bindings = List.of(input("/context/evidence", "/literal.dot"), input("/context/nil", "/nil"),
                result("/context/accepted", "/data"));
        var accepted = new MissionContext.CompletedTaskResult("task-1", "producer", "{\"data\":{\"text\":\"{not parsed}\"}}");
        var assembly = assembler.assemble(Map.of("context", Map.of("reasoning", "new")), bindings, source,
                List.of(accepted), "parent-1");
        var context = (Map<?,?>) assembly.arguments().get("context");
        assertThat(context.get("evidence")).isEqualTo(source.get("literal.dot"));
        assertThat(context.get("nil")).isNull();
        assertThat(context.containsKey("nil")).isTrue();
        assertThat(context.get("accepted")).isEqualTo(Map.of("text", "{not parsed}"));
        assertThat(context.get("reasoning")).isEqualTo("new");
        assertThat(assembly.exactPaths()).contains(List.of("context", "evidence"));
        assertThat(assembly.provenance().getLast()).containsEntry("sourceTaskId", "task-1")
                .containsEntry("parentMissionFrameId", "parent-1").doesNotContainKey("value");
        source.put("literal.dot", "changed");
        var second = assembler.assemble(Map.of(), bindings, Map.of("literal.dot", List.of(1), "nil", "present"),
                List.of(accepted), "parent-2");
        assertThat(((Map<?,?>) second.arguments().get("context")).get("evidence")).isEqualTo(List.of(1));
        assertThat(context.get("evidence")).isInstanceOf(List.class);
        assertThat(accepted.result()).isEqualTo("{\"data\":{\"text\":\"{not parsed}\"}}");
    }

    @Test
    void rejectsEveryModelOverrideAndNonobjectAncestorButAllowsUnboundSibling()
    {
        var binding = input("/context/evidence", "/source");
        for (Object context : List.of(Map.of("evidence", "same"), Map.of("evidence", Map.of("nested", 1)),
                "scalar", List.of(1)))
            assertThatThrownBy(() -> assembler.assemble(Map.of("context", context), List.of(binding),
                    Map.of("source", "same"), List.of(), "parent"))
                    .hasMessageContaining("binding_model_override");
        var nullAncestor = new LinkedHashMap<String,Object>();
        nullAncestor.put("context", null);
        assertThatThrownBy(() -> assembler.assemble(nullAncestor, List.of(binding), Map.of("source", "same"), List.of(), "p"))
                .hasMessageContaining("binding_model_override");
        assertThat(assembler.assemble(Map.of("context", Map.of("reasoning", "new")), List.of(binding),
                Map.of("source", "same"), List.of(), "p").arguments()).containsKey("context");
    }

    @Test
    void missingOptionalSourceAndUnavailableOrAmbiguousResultsFailClosed()
    {
        assertThatThrownBy(() -> assembler.assemble(Map.of(), List.of(input("/optional", "/absent")),
                Map.of(), List.of(), "p")).hasMessageContaining("binding_source_unavailable");
        var binding = result("/value", "/data");
        assertThatThrownBy(() -> assembler.assemble(Map.of(), List.of(binding), Map.of(), List.of(), "p"))
                .hasMessageContaining("found 0");
        var first = new MissionContext.CompletedTaskResult("one", "producer", "{}");
        var second = new MissionContext.CompletedTaskResult("two", "producer", "{}");
        assertThatThrownBy(() -> assembler.assemble(Map.of(), List.of(binding), Map.of(), List.of(first, second), "p"))
                .hasMessageContaining("found 2");
        var malformed = new MissionContext.CompletedTaskResult("one", "producer", "{unfinished");
        assertThatThrownBy(() -> assembler.assemble(Map.of(), List.of(binding), Map.of(), List.of(malformed), "p"))
                .hasMessageContaining("Missing object source path");
        assertThat(assembler.assemble(Map.of(), List.of(result("/value", "")), Map.of(), List.of(malformed), "p").arguments())
                .containsEntry("value", "{unfinished");
        assertThatThrownBy(() -> assembler.assemble(Map.of(), List.of(input("/value", "/array/0")),
                Map.of("array", List.of("never index")), List.of(), "p"))
                .hasMessageContaining("binding_source_unavailable");
    }
}
