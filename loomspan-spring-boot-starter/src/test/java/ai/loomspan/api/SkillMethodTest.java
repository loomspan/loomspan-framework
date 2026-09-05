package ai.loomspan.api;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.assertj.core.api.Assertions.assertThat;

class SkillMethodTest {

    @Test
    void isRuntimeMethodAnnotation() {
        Retention retention = SkillMethod.class.getAnnotation(Retention.class);
        Target target = SkillMethod.class.getAnnotation(Target.class);

        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactly(ElementType.METHOD);
    }

    @Test
    void exposesDescriptionAndOptionalExactName() throws NoSuchMethodException {
        SkillMethod annotation = SampleSkills.class
                .getDeclaredMethod("defaultSkill")
                .getAnnotation(SkillMethod.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).isEmpty();
        assertThat(annotation.description()).isEqualTo("Default route");
        assertThat(SkillMethod.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .containsExactlyInAnyOrder("description", "name");
    }

    static class SampleSkills {

        @SkillMethod(description = "Default route")
        void defaultSkill() {
        }
    }
}
