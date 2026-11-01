package ai.loomspan.internal.runtime.observation.catalog;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RegisteredSkillEntryTest {
    @Test void admitsFourFormsAndRejectsInapplicableLocations() {
        for (String kind : new String[]{"YAML", "TYPESAFE"}) {
            var entry = new RegisteredSkillEntry("default-leaf", kind, "YAML", "opaque label", null, null, null, "# exact\r\nname: leaf\r\n", null);
            assertThat(entry.summary().kind()).isEqualTo(kind);
            assertThat(entry.summary().declaration()).isEqualTo("YAML");
            assertThatThrownBy(() -> new RegisteredSkillEntry("default-leaf", kind, "YAML", "label", "default-run", "bean", "run()", "yaml", null)).isInstanceOf(IllegalArgumentException.class);
        }
        var executor = new RegisteredSkillEntry("default-leaf", "JAVA", "YAML", "label", "default-run", "bean", "run()", "executor: run", null);
        assertThat(executor.summary().executorName()).isEqualTo("default-run");
        assertThat(new RegisteredSkillEntry("default-method", "JAVA", "SKILL_METHOD", null, null, "bean", "method()", null, null).summary().declaration()).isEqualTo("SKILL_METHOD");
        for (String[] invalid : new String[][]{
                {"REST", "YAML", "path", null, null, null},
                {"JAVA", "OTHER", null, null, "bean", "run()"},
                {"YAML", "SKILL_METHOD", null, null, "bean", "run()"},
                {"JAVA", "YAML", "path", null, "bean", "run()"},
                {"JAVA", "YAML", "path", "default-run", null, "run()"},
                {"JAVA", "SKILL_METHOD", "path", null, "bean", "run()"},
                {"JAVA", "SKILL_METHOD", null, "default-run", "bean", "run()"}}) {
            assertThatThrownBy(() -> new RegisteredSkillEntry.Summary("default-leaf", invalid[0], invalid[1], invalid[2], invalid[3], invalid[4], invalid[5]))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new RegisteredSkillEntry("default-method", "JAVA", "SKILL_METHOD", null, null, "bean", "run()", "yaml", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
