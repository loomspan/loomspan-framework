package ai.loomspan.api;

import ai.loomspan.api.extensions.v1.SkillExecutorInvocation;

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
        assertThat(SkillExecutorInvocation.class.isRecord()).isFalse();
        assertThat(java.lang.reflect.Modifier.isFinal(SkillExecutorInvocation.class.getModifiers())).isTrue();
        assertThat(SkillExecutorInvocation.class.getConstructors()).isEmpty();
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
        var invocation = ai.loomspan.internal.core.ExecutorInvocations.create(source, source, source, source, "  sales-create  ", " generation ", () -> false, java.util.Optional::empty, org.mockito.Mockito.mock(ai.loomspan.api.extensions.v1.ExecutorDiagnostics.class));
        source.clear(); list.clear(); nested.clear();
        assertThat(invocation.skillName()).isEqualTo("  sales-create  ");
        assertThat(invocation.executorConfigGenerationId()).isEqualTo(" generation ");
        for (Map<String, Object> copied : List.of(invocation.input(), invocation.skillConfig(), invocation.executorConfig(), invocation.consumerExecutorConfig()))
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

    @Test void mapsRemainIndependentAndConsumerCyclesAreRejected() {
        var input = new LinkedHashMap<String,Object>(); input.put("same", "input");
        var skill = new LinkedHashMap<String,Object>(); skill.put("same", "skill");
        var owner = new LinkedHashMap<String,Object>(); owner.put("same", "owner");
        var consumer = new LinkedHashMap<String,Object>(); consumer.put("same", "consumer");
        var nested = new LinkedHashMap<Object,Object>(); nested.put(3, null); nested.put("Case.Dot", new java.math.BigDecimal("2.50"));
        consumer.put("nested", nested); consumer.put("empty", List.of());
        var call = ai.loomspan.internal.core.ExecutorInvocations.create(input, skill, owner, consumer, "default-leaf", "generation",
                () -> false, java.util.Optional::empty, org.mockito.Mockito.mock(ai.loomspan.api.extensions.v1.ExecutorDiagnostics.class));
        input.clear(); skill.clear(); owner.clear(); consumer.clear(); nested.clear();
        assertThat(call.input()).containsEntry("same", "input");
        assertThat(call.skillConfig()).containsEntry("same", "skill");
        assertThat(call.executorConfig()).containsEntry("same", "owner");
        assertThat(call.consumerExecutorConfig()).containsEntry("same", "consumer");
        var frozen = (Map<?,?>) call.consumerExecutorConfig().get("nested");
        assertThat(frozen.containsKey(3)).isTrue(); assertThat(frozen.get(3)).isNull();
        assertThat(frozen.get("Case.Dot")).isEqualTo(new java.math.BigDecimal("2.50"));
        consumer.put("cycle", consumer);
        assertThatThrownBy(() -> ai.loomspan.internal.core.ExecutorInvocations.create(Map.of(), Map.of(), Map.of(), consumer,
                "default-leaf", "generation", () -> false, java.util.Optional::empty,
                org.mockito.Mockito.mock(ai.loomspan.api.extensions.v1.ExecutorDiagnostics.class))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void requiresAllComponentsAndNonblankMetadata()
    {
        for (int index = 0; index < 6; index++)
        {
            int field = index;
            assertThatThrownBy(() -> ai.loomspan.internal.core.ExecutorInvocations.create(field == 0 ? null : Map.of(),
                    field == 1 ? null : Map.of(), field == 2 ? null : Map.of(), field == 3 ? null : Map.of(),
                    field == 4 ? null : "sales-create", field == 5 ? null : "generation", () -> false, java.util.Optional::empty, org.mockito.Mockito.mock(ai.loomspan.api.extensions.v1.ExecutorDiagnostics.class)))
                    .isInstanceOf(NullPointerException.class);
        }
        for (String blank : List.of("", " \t\n"))
        {
            assertThatThrownBy(() -> ai.loomspan.internal.core.ExecutorInvocations.create(Map.of(), Map.of(), Map.of(), java.util.Map.of(), blank, "g", () -> false, java.util.Optional::empty, org.mockito.Mockito.mock(ai.loomspan.api.extensions.v1.ExecutorDiagnostics.class)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ai.loomspan.internal.core.ExecutorInvocations.create(Map.of(), Map.of(), Map.of(), java.util.Map.of(), "s", blank, () -> false, java.util.Optional::empty, org.mockito.Mockito.mock(ai.loomspan.api.extensions.v1.ExecutorDiagnostics.class)))
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
        assertThat(RecoverableSkillException.class.getConstructors()).hasSize(3);
        assertThatCode(() -> RecoverableSkillException.class.getConstructor(String.class)).doesNotThrowAnyException();
        assertThatCode(() -> RecoverableSkillException.class.getConstructor(String.class, Throwable.class)).doesNotThrowAnyException();
        assertThatCode(() -> RecoverableSkillException.class.getConstructor(String.class, Throwable.class,
                SkillFailureDiagnosis.class, String.class)).doesNotThrowAnyException();
        for (String invalid : Arrays.asList(null, "", " \t\n"))
        {
            assertThatThrownBy(() -> new RecoverableSkillException(invalid)).isInstanceOf(invalid == null ? NullPointerException.class : IllegalArgumentException.class);
            assertThatThrownBy(() -> new RecoverableSkillException(invalid, cause)).isInstanceOf(invalid == null ? NullPointerException.class : IllegalArgumentException.class);
        }
    }
}
