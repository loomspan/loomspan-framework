package ai.loomspan.internal.runtime.attachment;

import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Schema-node selection shared by attachment recording and materialization. */
public final class AttachmentSchemaNodes
{
    private AttachmentSchemaNodes() {}

    public static boolean isAttachment(JsonNode schema)
    {
        return schema != null && schema.path("x-loomspan-attachment").isObject();
    }

    public static JsonNode property(JsonNode schema, String name)
    {
        if (schema == null) return null;
        JsonNode named = schema.path("properties").get(name);
        if (named != null) return named;
        JsonNode additional = schema.get("additionalProperties");
        return additional != null && additional.isObject() ? additional : null;
    }

    public static String child(String path, String token)
    {
        return path + "/" + token.replace("~", "~0").replace("/", "~1");
    }

    /** Private validation schema only: canonical and provider trees are never modified. */
    static void replaceAttachmentLeaves(JsonNode schema)
    {
        if (!(schema instanceof ObjectNode node)) return;
        if (isAttachment(node))
        {
            boolean nullable = node.path("type").isArray()
                    && node.path("type").valueStream().anyMatch(type -> "null".equals(type.asText()));
            JsonNode descriptor = LoomspanJacksonCodecs.defaults().schemaTree().readTree("""
                    {"type":"object","properties":{
                      "attachment":{"type":"boolean","enum":[true]},
                      "fieldPath":{"type":"string"},"name":{"type":["string","null"]},
                      "contentType":{"type":"string"},"mediaType":{"type":"string"},
                      "source":{"type":"string"},"sizeBytes":{"type":"integer","minimum":0},
                      "sha256":{"type":"string"},"metadata":{"type":"object","additionalProperties":true}},
                     "required":["attachment","fieldPath","name","contentType","mediaType","source"],
                     "additionalProperties":false}
                    """);
            node.removeAll();
            node.setAll((ObjectNode) descriptor);
            if (nullable) node.set("type", LoomspanJacksonCodecs.defaults().schemaTree().readTree("[\"object\",\"null\"]"));
            return;
        }
        node.path("properties").forEach(AttachmentSchemaNodes::replaceAttachmentLeaves);
        if (node.has("items")) replaceAttachmentLeaves(node.get("items"));
        if (node.path("additionalProperties").isObject()) replaceAttachmentLeaves(node.get("additionalProperties"));
    }
}
