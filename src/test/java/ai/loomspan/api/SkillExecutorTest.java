package ai.loomspan.api;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class SkillExecutorTest
{
    @Test void exactAnnotationAndInvocationShape() throws Exception
    {
        assertThat(SkillExecutor.class.getDeclaredMethods()).extracting(java.lang.reflect.Method::getName)
                .containsExactlyInAnyOrder("name", "namespace", "config");
        assertThat(SkillExecutor.class.getMethod("name").getDefaultValue()).isEqualTo("");
        assertThat(SkillExecutor.class.getMethod("namespace").getDefaultValue()).isEqualTo("default");
        assertThat(SkillExecutor.class.getMethod("config").getDefaultValue()).isEqualTo("");
        assertThat(SkillExecutor.class.getAnnotation(Target.class).value()).containsExactly(ElementType.METHOD);
        assertThat(SkillExecutor.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(SkillExecutorInvocation.class.getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("input", "skillConfig", "executorConfig", "skillName", "executorConfigGenerationId");
        assertThat(SkillExecutorInvocation.class.getConstructors()).hasSize(1);
        assertThat(SkillExecutorInvocation.class.getConstructors()[0].getParameterTypes())
                .containsExactly(Map.class, Map.class, Map.class, String.class, String.class);
    }

    @Test void copiesEveryMapAndNestedContainerButPreservesNullsAndLeaves()
    {
        var leaf = new StringBuilder("mutable leaf");
        var resource = new ByteArrayResource(new byte[] {1});
        var array = new byte[] {2};
        var set = new java.util.HashSet<>(List.of("unchanged leaf"));
        var nested = new LinkedHashMap<Object, Object>(); nested.put(null, null); nested.put("leaf", leaf);
        var list = new ArrayList<>(Arrays.asList(nested, null, resource, array, set));
        var source = new LinkedHashMap<String, Object>(); source.put("list", list); source.put(null, null);
        var invocation = new SkillExecutorInvocation(source, source, source, "  sales-create  ", " generation ");
        source.clear(); list.clear(); nested.clear();
        assertThat(invocation.skillName()).isEqualTo("  sales-create  ");
        assertThat(invocation.executorConfigGenerationId()).isEqualTo(" generation ");
        for (Map<String, Object> copied : List.of(invocation.input(), invocation.skillConfig(), invocation.executorConfig()))
        {
            assertThat(copied).containsEntry(null, null);
            var copiedList = (List<?>) copied.get("list");
            assertThat(copiedList).hasSize(5);
            assertThat(copiedList.get(1)).isNull();
            assertThat(copiedList.get(2)).isSameAs(resource);
            assertThat(copiedList.get(3)).isSameAs(array);
            assertThat(copiedList.get(4)).isSameAs(set);
            var copiedMap = (Map<?, ?>) copiedList.getFirst();
            assertThat(copiedMap.get("leaf")).isSameAs(leaf);
            assertThat(copiedMap.containsKey(null)).isTrue();
            assertThatThrownBy(copied::clear).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(copiedList::clear).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(copiedMap::clear).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test void requiresAllComponentsAndNonblankMetadata()
    {
        for (int index = 0; index < 5; index++)
        {
            int field = index;
            assertThatThrownBy(() -> new SkillExecutorInvocation(field == 0 ? null : Map.of(),
                    field == 1 ? null : Map.of(), field == 2 ? null : Map.of(),
                    field == 3 ? null : "sales-create", field == 4 ? null : "generation"))
                    .isInstanceOf(NullPointerException.class);
        }
        for (String blank : List.of("", " \t\n"))
        {
            assertThatThrownBy(() -> new SkillExecutorInvocation(Map.of(), Map.of(), Map.of(), blank, "g"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new SkillExecutorInvocation(Map.of(), Map.of(), Map.of(), "s", blank))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void designatedExceptionHasExactMessageAndOptionalDiagnosticCause()
    {
        var cause = new IllegalStateException("diagnostic, never feedback");
        String message = "  correct the account\n";
        assertThat(new RecoverableSkillException(message)).hasMessage(message).hasNoCause();
        assertThat(new RecoverableSkillException(message, cause)).hasMessage(message).hasCause(cause).isInstanceOf(SkillException.class);
        assertThat(new RecoverableSkillException(message, null)).hasMessage(message).hasNoCause();
        assertThat(RecoverableSkillException.class.getConstructors()).hasSize(2);
        assertThatCode(() -> RecoverableSkillException.class.getConstructor(String.class)).doesNotThrowAnyException();
        assertThatCode(() -> RecoverableSkillException.class.getConstructor(String.class, Throwable.class)).doesNotThrowAnyException();
        for (String invalid : Arrays.asList(null, "", " \t\n"))
        {
            assertThatThrownBy(() -> new RecoverableSkillException(invalid)).isInstanceOf(invalid == null ? NullPointerException.class : IllegalArgumentException.class);
            assertThatThrownBy(() -> new RecoverableSkillException(invalid, cause)).isInstanceOf(invalid == null ? NullPointerException.class : IllegalArgumentException.class);
        }
    }
}
