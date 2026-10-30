package ai.loomspan.internal.skill;

import ai.loomspan.api.*;
import ai.loomspan.internal.core.SkillIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ai.loomspan.internal.skill.SkillReloaderTest.*;
import static ai.loomspan.internal.skill.SkillDependencyValidatorTest.*;
import static ai.loomspan.internal.skill.SkillAdmissionSnapshotTest.publish;
import static org.assertj.core.api.Assertions.*;

class SkillAdmissionContractsTest {
    @Test void fullyForwardedChangedAndAddedOutputRequiresCallerRepublication() {
        for (var changed : List.of(producer("leaf", "integer"), output("leaf", "{value: {type: string}, added: {type: boolean}}"))) {
            var f = new Fixture(null);
            publish(f, "dep", producer("leaf", "string"));
            publish(f, "a", forward("root", "dep-leaf"));
            String preparedSchema = f.reloader.snapshot("a").skill("a-root").orElseThrow().outputSchema();
            publish(f, "dep", changed);
            assertThatThrownBy(() -> validate(f, "a-root")).hasMessageContaining("a-root");
            assertThat(f.reloader.snapshot("a").skill("a-root").orElseThrow().outputSchema()).isEqualTo(preparedSchema);
            publish(f, "a", forward("root", "dep-leaf"));
            validate(f, "a-root");
        }
    }

    @Test void multiHopForwardedOutputIsRecomputedAcrossCapturedGenerations() {
        var f = new Fixture(null);
        publish(f, "dep", producer("leaf", "string"));
        publish(f, "bridge", forward("hop", "dep-leaf"));
        publish(f, "a", forward("root", "bridge-hop"));
        publish(f, "dep", output("leaf", "{value: {type: string}, added: {type: boolean}}"));
        assertThatThrownBy(() -> validate(f, "a-root")).hasMessageContaining("contract");
        // Refreshing just the outer caller cannot repair the bridge's own stale prepared contract.
        publish(f, "a", forward("root", "bridge-hop"));
        assertThatThrownBy(() -> validate(f, "a-root")).hasMessageContaining("bridge-hop");
        publish(f, "bridge", forward("hop", "dep-leaf"));
        validate(f, "a-root");
    }

    @Test void equivalentSchemaFormattingAndObjectOrderRemainAdmissible() {
        var f = new Fixture(null);
        publish(f, "dep", output("leaf", "{value: {type: string}, count: {type: integer}}"));
        publish(f, "a", forward("root", "dep-leaf"));
        publish(f, "dep", new SkillDocument("reformatted", model("leaf").yaml() + """
                output_schema:
                  properties:
                    count:
                      type: integer
                    value:
                      type: string
                  type: object
                """));
        validate(f, "a-root");
    }

    @Test void unrelatedBoundChildOutputAdditionRemainsAdmissibleButChangedSelectedTypeRejects() {
        var f = new Fixture(null);
        publish(f, "dep", producer("leaf", "string"));
        publish(f, "a", outputBoundRoot());
        publish(f, "dep", output("leaf", "{value: {type: string}, added: {type: boolean}}"));
        validate(f, "a-root");
        publish(f, "dep", producer("leaf", "integer"));
        assertThatThrownBy(() -> validate(f, "a-root")).hasMessageContaining("incompatible");
        publish(f, "dep", new SkillDocument("closed leaf", model("leaf").yaml()
                + "output_schema: {type: object, properties: {other: {type: string}}, additionalProperties: false}\n"));
        assertThatThrownBy(() -> validate(f, "a-root")).hasMessageContaining("undeclared");
    }

    @Test void ordinaryChildSchemaChangesAreNotPinned() {
        var f = new Fixture(null);
        publish(f, "dep", producer("leaf", "string"));
        publish(f, "a", calls("root", "dep-leaf"));
        publish(f, "dep", new SkillDocument("leaf", producer("leaf", "integer").yaml()
                + "input_schema: {type: object, properties: {requiredLater: {type: boolean}}, required: [requiredLater]}\n"));
        validate(f, "a-root");
        publish(f, "dep", model("leaf"));
        validate(f, "a-root");
    }

    @Test void changedInputBindingReceiverPathConstraintRejects() {
        var f = new Fixture(null);
        publish(f, "dep", receiver("string"));
        publish(f, "a", new SkillDocument("root", model("root").yaml() + """
                planning_mode: true
                input_schema: {type: object, properties: {id: {type: string}}, required: [id]}
                allowed_skills:
                  - name: dep-leaf
                    input_bindings:
                      /id: {from: input, path: /id}
                """));
        validate(f, "a-root");
        publish(f, "dep", new SkillDocument("leaf", receiver("string").yaml().replace("{id:", "{other:").replace("required: [id]", "required: [other]")));
        assertThatThrownBy(() -> validate(f, "a-root")).hasMessageContaining("undeclared");
    }

    @Test void preparedCallerPublishesAfterEachDependencyOnlyChangeButAffectedAdmissionsFail() {
        for (String change : List.of("deletion", "contract", "private")) {
            var f = new Fixture(null);
            publish(f, "dep", producer("leaf", "string"));
            publish(f, "a", forward("root", "dep-leaf"), model("safe"));
            var old = f.manager.capture(SkillIdentity.parse("a-root"));
            try {
                var caller = f.reloader.prepare("a", List.of(forward("root", "dep-leaf"), model("safe")), MODEL);
                if (change.equals("deletion")) f.reloader.remove("dep", f.manager.active("dep").id());
                else if (change.equals("private")) f.reloader.publish(f.reloader.prepare("dep", List.of(producer("leaf", "string")), MODEL));
                else publish(f, "dep", producer("leaf", "integer"));
                f.reloader.publish(caller);
                assertThat(f.reloader.snapshot("a").generationId()).isEqualTo(caller.generationId());
                assertThat(f.reloader.snapshot("a").skill("a-root").orElseThrow().outputSchema()).contains("string");
                assertThatThrownBy(() -> validate(f, "a-root")).isInstanceOf(RuntimeException.class);
                validate(f, "a-safe");
                f.manager.validateAdmission(old.snapshot());
                assertThat(old.snapshot().generations().get("dep").visibility()).isEqualTo(NamespaceVisibility.PUBLIC);
            } finally { old.lease().close(); }
        }
    }

    static void validate(Fixture f, String name) {
        var capture = f.manager.capture(SkillIdentity.parse(name));
        try { f.manager.validateAdmission(capture.snapshot()); }
        finally { capture.lease().close(); }
    }

    private static SkillDocument output(String name, String properties) {
        return new SkillDocument(name, model(name).yaml() + "output_schema: {type: object, properties: " + properties + "}\n");
    }

    private static SkillDocument outputBoundRoot() {
        return new SkillDocument("root", model("root").yaml() + """
                planning_mode: true
                allowed_skills: [{name: dep-leaf, max_tasks: 1}]
                output_schema: {type: object, properties: {answer: {type: string}}, required: [answer]}
                output_bindings:
                  /answer: {from: child_result, skill: dep-leaf, path: /value}
                """);
    }

    private static SkillDocument receiver(String type) {
        return new SkillDocument("leaf", model("leaf").yaml()
                + "input_schema: {type: object, properties: {id: {type: " + type + "}}, required: [id], additionalProperties: false}\n");
    }
}
