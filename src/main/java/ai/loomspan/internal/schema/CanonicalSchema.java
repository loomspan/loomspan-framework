package ai.loomspan.internal.schema;

import com.networknt.schema.Schema;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Objects;

/** Immutable, initialized internal schema snapshot. */
public final class CanonicalSchema {
    private final JsonNode tree;
    private final Schema validator;
    CanonicalSchema(JsonNode tree, Schema validator) { this.tree = tree.deepCopy(); this.validator = validator; }
    public JsonNode tree() { return tree.deepCopy(); }
    public JsonNode providerTree() { var copy = tree.deepCopy(); strip(copy); return copy; }
    private static void strip(JsonNode node) {
        if (!(node instanceof ObjectNode object)) return;
        for (String name : StandardSchemaCompiler.ANNOTATIONS) object.remove(name);
        var properties = node.get("properties");
        if (properties != null && properties.isObject()) properties.forEach(CanonicalSchema::strip);
        if (node.has("items")) strip(node.get("items"));
        if (node.has("additionalProperties")) strip(node.get("additionalProperties"));
    }
    /** Completed verdict only: operational failures propagate without conversion to validation errors. */
    public List<com.networknt.schema.Error> validate(JsonNode instance) {
        Objects.requireNonNull(instance, "instance must not be null");
        return List.copyOf(validator.validate(instance, context -> context.executionConfig(config -> config
                .formatAssertionsEnabled(true).maxEvaluationDepth(0).maxEvaluationSteps(0))));
    }
}
