package ai.loomspan.internal.skill;

import ai.loomspan.internal.runtime.input.ObjectFieldPath;
import tools.jackson.databind.JsonNode;

/** Bounded declaration checks; unknown open branches remain runtime obligations. */
final class BindingSchemaCompatibility {
    private BindingSchemaCompatibility() { }

    static JsonNode select(JsonNode node, ObjectFieldPath path, boolean destination) {
        for (String token : path.tokens()) {
            if (unknown(node)) return null;
            if (!"object".equals(type(node)))
                throw new IllegalArgumentException((destination ? "destination" : "source")
                        + " path traverses a non-object schema at " + path.pointer());
            var next = node.path("properties").get(token);
            if (next == null) {
                if (closed(node))
                    throw new IllegalArgumentException((destination ? "destination" : "source")
                            + " path selects an undeclared field in a closed object at " + path.pointer());
                if (!node.path("additionalProperties").isObject()) return null;
                next = node.get("additionalProperties");
            }
            node = next;
        }
        if (prohibited(node)) throw new IllegalArgumentException("path selects a prohibited field at " + path.pointer());
        return node;
    }

    static void check(JsonNode source, JsonNode target, String location) {
        if (unknown(source) || unknown(target)) return;
        if (prohibited(source) || prohibited(target))
            throw new IllegalArgumentException("statically incompatible prohibited field at " + location);
        String from = type(source), to = type(target);
        if (!from.equals(to) && !(from.equals("integer") && to.equals("number")))
            throw new IllegalArgumentException("statically incompatible types at " + location + ": " + from + " -> " + to);
        if (nullable(source) && !nullable(target))
            throw new IllegalArgumentException("nullable source is incompatible with non-nullable destination at " + location);
        var sourceEnum = source.path("enum");
        var destinationEnum = target.path("enum");
        if (sourceEnum.isArray() && !sourceEnum.isEmpty() && destinationEnum.isArray() && !destinationEnum.isEmpty()) {
            boolean intersects = false;
            for (var candidate : sourceEnum) for (var allowed : destinationEnum)
                if (candidate.isNumber() && allowed.isNumber()
                        ? candidate.decimalValue().compareTo(allowed.decimalValue()) == 0 : candidate.equals(allowed))
                    intersects = true;
            if (!intersects) throw new IllegalArgumentException("statically incompatible enums at " + location);
        }
        if (from.equals("array")) check(source.get("items"), target.get("items"), location + "[]");
        if (from.equals("object")) {
            var sourceProperties = source.path("properties");
            var targetProperties = target.path("properties");
            for (var field : sourceProperties.properties()) {
                var receiver = targetProperties.get(field.getKey());
                if (receiver == null && closed(target)) {
                    // Optional producer fields may be absent; only guaranteed fields prove this conflict.
                    for (var required : source.path("required"))
                        if (required.asText().equals(field.getKey()))
                            throw new IllegalArgumentException("required source field is not allowed in closed destination at "
                                    + location + "/" + field.getKey());
                }
                check(field.getValue(), receiver, location + "/" + field.getKey());
            }
            for (var required : target.path("required"))
                if (!sourceProperties.has(required.asText()) && closed(source))
                    throw new IllegalArgumentException("closed source cannot supply required destination field at "
                            + location + "/" + required.asText());
        }
    }

    private static boolean prohibited(JsonNode node) { return node != null && node.isBoolean() && !node.booleanValue(); }
    private static boolean unknown(JsonNode node) { return node == null || node.isObject() && !node.has("type"); }
    private static boolean closed(JsonNode node) { return node.path("additionalProperties").isBoolean() && !node.get("additionalProperties").booleanValue(); }
    private static String type(JsonNode node) {
        var type = node.path("type");
        if (type.isArray()) for (var candidate : type) if (!"null".equals(candidate.asText())) return candidate.asText();
        return type.asText();
    }
    private static boolean nullable(JsonNode node) {
        var type = node.path("type");
        if (type.isArray()) for (var candidate : type) if ("null".equals(candidate.asText())) return true;
        return false;
    }
}
