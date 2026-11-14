package ai.loomspan.internal.schema;

import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import com.networknt.schema.*;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.dialect.Dialect;
import com.networknt.schema.keyword.AnnotationKeyword;
import com.networknt.schema.resource.SchemaLoader;
import tools.jackson.databind.JsonNode;
import java.util.Set;
import java.util.Objects;

/** The single supported Draft 2020-12 declaration profile. */
public final class StandardSchemaCompiler {
    public enum Role { INPUT, OUTPUT, PLANNING }
    public enum Origin { AUTHORED, GENERATED }
    public static final String DIALECT = "https://json-schema.org/draft/2020-12/schema";
    static final Set<String> ANNOTATIONS = Set.of("x-loomspan-attachment", "x-loomspan-evidence", "x-loomspan-runtime-ref-capable");
    public static final Set<String> FORMATS = Set.of("date", "date-time", "duration", "email", "hostname", "idn-email", "idn-hostname", "ipv4", "ipv6", "iri", "iri-reference", "json-pointer", "regex", "relative-json-pointer", "time", "uri", "uri-reference", "uri-template", "uuid");
    private static final Set<String> TYPES = Set.of("object", "array", "string", "number", "integer", "boolean");
    private static final Set<String> KEYWORDS = Set.of("$schema", "type", "properties", "required", "additionalProperties", "items", "enum", "description", "format", "minimum", "maximum", "minLength", "maxLength", "minItems", "maxItems");
    private static final Set<String> META_RESOURCES = Set.of(DIALECT, "https://json-schema.org/draft/2020-12/meta/core", "https://json-schema.org/draft/2020-12/meta/applicator", "https://json-schema.org/draft/2020-12/meta/unevaluated", "https://json-schema.org/draft/2020-12/meta/validation", "https://json-schema.org/draft/2020-12/meta/meta-data", "https://json-schema.org/draft/2020-12/meta/format-annotation", "https://json-schema.org/draft/2020-12/meta/content");
    private final SchemaRegistry registry = SchemaRegistry.withDialect(annotatedDialect(), builder -> builder
            .schemaLoader(SchemaLoader.builder().fetchRemoteResources(false).allow(iri -> META_RESOURCES.contains(iri.toString())).build()));
    private static Dialect annotatedDialect() {
        var builder = Dialect.builder(Dialects.getDraft202012());
        for (String annotation : ANNOTATIONS) builder.keyword(new AnnotationKeyword(annotation));
        return builder.build();
    }
    public CanonicalSchema compile(JsonNode schema, Role role, Origin origin) {
        Objects.requireNonNull(role); Objects.requireNonNull(origin);
        if (schema == null) throw failure("", "schema must be an object");
        JsonNode copy = schema.deepCopy();
        walk(copy, role, origin, "", false, false);
        if (!copy.path("type").isString() || !"object".equals(copy.path("type").textValue())) throw failure("/type", "root type must be object");
        var meta = registry.getSchema(SchemaLocation.of(DIALECT));
        meta.initializeValidators();
        var errors = meta.validate(copy, context -> context.executionConfig(config -> config.maxEvaluationDepth(0).maxEvaluationSteps(0)));
        if (!errors.isEmpty()) {
            var error = errors.getFirst();
            throw failure(error.getInstanceLocation().toString(), error.toString());
        }
        var validator = registry.getSchema(copy);
        validator.initializeValidators();
        return new CanonicalSchema(copy, validator);
    }
    public CanonicalSchema compile(String schema, Role role, Origin origin) {
        return compile(LoomspanJacksonCodecs.defaults().schemaTree().readTree(schema), role, origin);
    }
    private void walk(JsonNode node, Role role, Origin origin, String path, boolean property, boolean immediate) {
        if (node.isBoolean()) {
            if (origin == Origin.GENERATED && property && !node.booleanValue()) return;
            throw failure(path, "boolean schemas are unsupported at this location");
        }
        if (!node.isObject()) throw failure(path, "schema must be an object");
        if (node.isEmpty() || (node.size() == 1 && node.has("description") && node.get("description").isString())) {
            if (role == Role.INPUT && !path.isEmpty()) return;
            throw failure(path, "shape-free schemas are supported only at nested input locations");
        }
        for (String key : node.propertyNames()) {
            if (!KEYWORDS.contains(key) && !ANNOTATIONS.contains(key)) throw failure(path + "/" + escape(key), "unsupported keyword '" + key + "'");
        }
        if (node.has("$schema") && (!path.isEmpty() || !DIALECT.equals(node.path("$schema").asText()))) throw failure(path + "/$schema", "only the exact root Draft 2020-12 dialect is supported");
        JsonNode type = node.get("type");
        String ordinary = null;
        if (type != null && type.isString() && TYPES.contains(type.textValue())) ordinary = type.textValue();
        else if (type != null && type.isArray() && type.size() == 2) {
            JsonNode a = type.get(0), b = type.get(1);
            if (a.isString() && b.isString()) {
                if ("null".equals(a.textValue()) && TYPES.contains(b.textValue())) ordinary = b.textValue();
                if ("null".equals(b.textValue()) && TYPES.contains(a.textValue())) ordinary = a.textValue();
            }
        }
        if (ordinary == null) throw failure(path + "/type", "declare one ordinary type, optionally paired with null");
        if (node.has("format") && (!node.get("format").isString() || !FORMATS.contains(node.get("format").textValue()))) throw failure(path + "/format", "unsupported format");
        if (node.has("enum") && node.get("enum").isArray()) for (var value : node.get("enum")) if (value.isContainer()) throw failure(path + "/enum", "enum values must be JSON scalars");
        if (node.has("x-loomspan-attachment")) attachment(node.get("x-loomspan-attachment"), role, ordinary, path + "/x-loomspan-attachment");
        if (node.has("x-loomspan-runtime-ref-capable") && (origin != Origin.GENERATED || role != Role.INPUT || !"string".equals(ordinary) || !node.get("x-loomspan-runtime-ref-capable").isBoolean())) throw failure(path + "/x-loomspan-runtime-ref-capable", "requires a generated input string leaf and boolean metadata");
        if (node.has("x-loomspan-evidence")) {
            var evidence = node.get("x-loomspan-evidence");
            if (role != Role.OUTPUT || !immediate || !evidence.isString() || evidence.textValue().isBlank()) throw failure(path + "/x-loomspan-evidence", "requires a nonblank string on an immediate root output property");
        }
        var properties = node.get("properties");
        if (properties != null && properties.isObject()) for (String name : properties.propertyNames()) walk(properties.get(name), role, origin, path + "/properties/" + escape(name), true, path.isEmpty());
        if (node.has("items")) walk(node.get("items"), role, origin, path + "/items", false, false);
        if ("array".equals(ordinary) && !node.has("items")) throw failure(path + "/items", "homogeneous array items schema is required");
        var additional = node.get("additionalProperties");
        if (additional != null && additional.isObject()) {
            if (role != Role.INPUT) throw failure(path + "/additionalProperties", "schema-valued additionalProperties is input-only");
            walk(additional, role, origin, path + "/additionalProperties", false, false);
        }
    }
    private void attachment(JsonNode value, Role role, String type, String path) {
        if (role != Role.INPUT || !"string".equals(type) || !value.isObject()) throw failure(path, "attachment requires an input string leaf and object metadata");
        for (String key : value.propertyNames()) if (!Set.of("media_type", "allowed_content_types").contains(key)) throw failure(path + "/" + escape(key), "unknown attachment field");
        if (!value.path("media_type").isString() || !Set.of("image", "pdf", "audio", "video", "file").contains(value.path("media_type").textValue())) throw failure(path + "/media_type", "unsupported attachment media type");
        var content = value.get("allowed_content_types");
        if (content == null || !content.isArray() || content.isEmpty()) throw failure(path + "/allowed_content_types", "requires a nonempty list of nonblank strings");
        for (int i = 0; i < content.size(); i++) {
            var item = content.get(i);
            if (!item.isString() || item.textValue().isBlank()) throw failure(path + "/allowed_content_types", "requires nonblank strings");
            try { org.springframework.util.MimeTypeUtils.parseMimeType(item.textValue()); }
            catch (IllegalArgumentException ex) {
                throw failure(path + "/allowed_content_types/" + i, "invalid attachment MIME '" + item.textValue() + "': " + ex.getMessage());
            }
        }
    }
    private static String escape(String value) { return value.replace("~", "~0").replace("/", "~1"); }
    private static DeclarationException failure(String path, String detail) { return new DeclarationException(path, detail); }
    public static final class DeclarationException extends IllegalArgumentException {
        private final String path;
        DeclarationException(String path, String detail) { super(detail); this.path = path; }
        public String path() { return path; }
    }
}
