package ai.loomspan.internal.typesafe;

import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import ai.loomspan.internal.skill.YamlSkillManifest.OutputSchemaManifest;

import tools.jackson.core.JsonPointer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The bounded TypeSafe declaration, request validation and descriptive-schema authority. */
public final class TypeSafeDeclaration {
    private static final ObjectMapper JSON = LoomspanJacksonCodecs.defaults().schemaTree();
    private final JsonNode declaration;
    private final OutputSchemaManifest schema;
    private final Map<String, List<Token>> templates;

    private TypeSafeDeclaration(JsonNode declaration) {
        this.declaration = declaration == null ? null : declaration.deepCopy();
        templates = compileTemplates(this.declaration);
        validate(this.declaration, true, templates);
        schema = deriveSchema(this.declaration.get("questions"));
    }

    public static TypeSafeDeclaration compile(JsonNode declaration) {
        return new TypeSafeDeclaration(declaration);
    }

    public OutputSchemaManifest outputSchema() {
        return JSON.convertValue(schema, OutputSchemaManifest.class);
    }

    public ResolvedRequest resolve(Object normalizedInput) {
        requireJsonValue(normalizedInput, "/input");
        JsonNode input = JSON.valueToTree(normalizedInput);
        checkJson(input, "/input");
        ObjectNode result = JSON.createObjectNode();
        result.set("state", substitute(declaration.get("state"), input, "typesafe.state"));
        ObjectNode questions = JSON.createObjectNode();
        declaration
                .get("questions")
                .properties()
                .forEach(
                        entry -> {
                            ObjectNode question = JSON.createObjectNode();
                            question.set("type", entry.getValue().get("type").deepCopy());
                            for (String field : List.of("instructions", "criteria"))
                                if (entry.getValue().has(field))
                                    question.set(
                                            field,
                                            substitute(
                                                    entry.getValue().get(field),
                                                    input,
                                                    "typesafe.questions."
                                                            + entry.getKey()
                                                            + "."
                                                            + field));
                            questions.set(entry.getKey(), question);
                        });
        result.set("questions", questions);
        validate(result, false, templates);
        return new ResolvedRequest(JSON.writeValueAsString(result));
    }

    /** Immutable serialization is captured once and reused for every provider attempt. */
    public record ResolvedRequest(String jsonText) {
        public JsonNode json() {
            return JSON.readTree(jsonText);
        }
    }

    private static void validate(
            JsonNode block, boolean registration, Map<String, List<Token>> templates) {
        require(block != null && block.isObject(), "typesafe", "must be an object");
        fields(block, Set.of("state", "questions"), "typesafe");
        require(block.has("state"), "typesafe.state", "is required");
        description(block.get("state"), false, "typesafe.state");
        JsonNode questions = block.get("questions");
        require(
                questions != null && questions.isObject() && !questions.isEmpty(),
                "typesafe.questions",
                "must be a nonempty object");
        names(questions, "typesafe.questions");
        questions
                .properties()
                .forEach(
                        entry -> {
                            String path = "typesafe.questions." + entry.getKey();
                            JsonNode q = entry.getValue();
                            require(q.isObject(), path, "must be an object");
                            fields(q, Set.of("type", "instructions", "criteria"), path);
                            JsonNode type = q.get("type");
                            require(
                                    type != null
                                            && type.isString()
                                            && Set.of("choice", "noul", "score")
                                                    .contains(type.stringValue()),
                                    path + ".type",
                                    "must be literal choice, noul, or score");
                            if (q.has("instructions"))
                                description(q.get("instructions"), true, path + ".instructions");
                            JsonNode criteria = q.get("criteria");
                            boolean deferred = registration && wholeReference(criteria, templates);
                            if (deferred) return;
                            switch (type.stringValue()) {
                                case "choice" -> {
                                    require(
                                            criteria != null
                                                    && criteria.isObject()
                                                    && criteria.size() >= 1
                                                    && criteria.size() <= 255,
                                            path + ".criteria",
                                            "must be an object with 1 to 255 candidates");
                                    names(criteria, path + ".criteria");
                                    criteria.properties()
                                            .forEach(
                                                    candidate ->
                                                            description(
                                                                    candidate.getValue(),
                                                                    true,
                                                                    path
                                                                            + ".criteria."
                                                                            + candidate.getKey()));
                                }
                                case "score" -> {
                                    require(
                                            criteria != null
                                                    && criteria.isArray()
                                                    && criteria.size() >= 1
                                                    && criteria.size() <= 10,
                                            path + ".criteria",
                                            "must be an ordered array of 1 to 10 levels");
                                    for (int i = 0; i < criteria.size(); i++)
                                        description(
                                                criteria.get(i),
                                                true,
                                                path + ".criteria[" + i + "]");
                                }
                                case "noul" -> {
                                    if (criteria != null && !criteria.isNull()) {
                                        require(
                                                criteria.isObject(),
                                                path + ".criteria",
                                                "must be null or an object");
                                        fields(
                                                criteria,
                                                Set.of("true", "false"),
                                                path + ".criteria");
                                        criteria.properties()
                                                .forEach(
                                                        outcome ->
                                                                description(
                                                                        outcome.getValue(),
                                                                        true,
                                                                        path
                                                                                + ".criteria."
                                                                                + outcome
                                                                                        .getKey()));
                                    }
                                    JsonNode instructions = q.get("instructions");
                                    if (instructions == null || instructions.isNull()) {
                                        boolean outcome =
                                                criteria != null
                                                        && criteria.isObject()
                                                        && criteria.properties().stream()
                                                                .anyMatch(
                                                                        e ->
                                                                                !e.getValue()
                                                                                        .isNull());
                                        require(
                                                outcome,
                                                path,
                                                "Noul requires instructions or criteria describing"
                                                        + " an outcome");
                                    }
                                }
                                default -> throw new IllegalStateException();
                            }
                        });
        checkJson(block, "typesafe");
    }

    private static void description(JsonNode node, boolean nullable, String path) {
        require(
                node != null
                        && ((nullable && node.isNull())
                                || node.isString()
                                || node.isObject()
                                || node.isArray()),
                path,
                "must be string, object, array" + (nullable ? ", or null" : ""));
    }

    private static Map<String, List<Token>> compileTemplates(JsonNode declaration) {
        Map<String, List<Token>> compiled = new LinkedHashMap<>();
        if (declaration != null && declaration.isObject()) {
            if (declaration.has("state"))
                scan(declaration.get("state"), "typesafe.state", compiled);
            JsonNode questions = declaration.get("questions");
            if (questions != null && questions.isObject()) {
                questions
                        .properties()
                        .forEach(
                                entry -> {
                                    JsonNode question = entry.getValue();
                                    if (question.isObject()) {
                                        for (String field : List.of("instructions", "criteria")) {
                                            if (question.has(field))
                                                scan(
                                                        question.get(field),
                                                        "typesafe.questions."
                                                                + entry.getKey()
                                                                + "."
                                                                + field,
                                                        compiled);
                                        }
                                    }
                                });
            }
        }
        return Map.copyOf(compiled);
    }

    private static void scan(JsonNode node, String path, Map<String, List<Token>> compiled) {
        if (node.isString()) {
            compiled.computeIfAbsent(node.stringValue(), value -> List.copyOf(tokens(value, path)));
        } else if (node.isObject()) {
            node.properties()
                    .forEach(
                            entry -> scan(entry.getValue(), path + "." + entry.getKey(), compiled));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) scan(node.get(i), path + "[" + i + "]", compiled);
        }
    }

    private record Token(String literal, JsonPointer reference) {}

    private static List<Token> tokens(String value, String path) {
        List<Token> result = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < value.length(); ) {
            if (value.startsWith("$${", i)) {
                literal.append("${");
                i += 3;
            } else if (value.startsWith("${", i)) {
                if (!literal.isEmpty()) {
                    result.add(new Token(literal.toString(), null));
                    literal.setLength(0);
                }
                int end = value.indexOf('}', i + 2);
                require(end >= 0, path, "unterminated input expression");
                String pointer = value.substring(i + 2, end);
                require(
                        (pointer.equals("/input") || pointer.startsWith("/input/"))
                                && !pointer.contains("${"),
                        path,
                        "invalid input expression '${" + pointer + "}'");
                for (int j = 0; j < pointer.length(); j++)
                    if (pointer.charAt(j) == '~') {
                        require(
                                j + 1 < pointer.length()
                                        && (pointer.charAt(j + 1) == '0'
                                                || pointer.charAt(j + 1) == '1'),
                                path,
                                "invalid JSON Pointer escape");
                        j++;
                    }
                result.add(new Token(null, JsonPointer.compile(pointer.substring(6))));
                i = end + 1;
            } else {
                literal.append(value.charAt(i++));
            }
        }
        if (!literal.isEmpty() || result.isEmpty()) result.add(new Token(literal.toString(), null));
        return result;
    }

    private static boolean wholeReference(JsonNode node, Map<String, List<Token>> templates) {
        if (node == null || !node.isString()) return false;
        var parsed = templates.get(node.stringValue());
        return parsed.size() == 1 && parsed.getFirst().reference() != null;
    }

    private JsonNode substitute(JsonNode node, JsonNode input, String path) {
        if (node.isString()) {
            var parsed = templates.get(node.stringValue());
            if (parsed.size() == 1 && parsed.getFirst().reference() != null)
                return lookup(input, parsed.getFirst().reference(), path).deepCopy();
            StringBuilder text = new StringBuilder();
            for (Token token : parsed) {
                if (token.reference() == null) text.append(token.literal());
                else {
                    JsonNode selected = lookup(input, token.reference(), path);
                    require(
                            selected.isString() || selected.isNumber() || selected.isBoolean(),
                            path,
                            "embedded input reference must select a string, finite number, or"
                                    + " boolean");
                    text.append(
                            selected.isString()
                                    ? selected.stringValue()
                                    : JSON.writeValueAsString(selected));
                }
            }
            return JSON.getNodeFactory().stringNode(text.toString());
        }
        if (node.isObject()) {
            var object = JSON.createObjectNode();
            node.properties()
                    .forEach(
                            e ->
                                    object.set(
                                            e.getKey(),
                                            substitute(
                                                    e.getValue(), input, path + "." + e.getKey())));
            return object;
        }
        if (node.isArray()) {
            var array = JSON.createArrayNode();
            for (int i = 0; i < node.size(); i++)
                array.add(substitute(node.get(i), input, path + "[" + i + "]"));
            return array;
        }
        return node.deepCopy();
    }

    private static JsonNode lookup(JsonNode input, JsonPointer pointer, String path) {
        JsonNode current = input;
        for (JsonPointer remaining = pointer; !remaining.matches(); remaining = remaining.tail()) {
            String token = remaining.getMatchingProperty();
            if (current.isArray()) {
                require(
                        token.matches("0|[1-9][0-9]*"),
                        path,
                        "invalid array index '" + token + "'");
                int index = remaining.getMatchingIndex();
                require(
                        index >= 0 && index < current.size(),
                        path,
                        "array index out of range '" + token + "'");
                current = current.get(index);
            } else {
                require(current.isObject(), path, "input reference traverses a scalar or null");
                current = current.get(token);
                require(current != null, path, "missing input reference '" + pointer + "'");
            }
        }
        return current;
    }

    private static void names(JsonNode object, String path) {
        Map<String, String> seen = new LinkedHashMap<>();
        object.properties()
                .forEach(
                        e -> {
                            String key = e.getKey();
                            String previous = seen.putIfAbsent(key.toLowerCase(Locale.ROOT), key);
                            require(
                                    previous == null,
                                    path + "." + key,
                                    "name '"
                                            + key
                                            + "' collides with '"
                                            + previous
                                            + "' when compared case-insensitively");
                        });
    }

    private static void fields(JsonNode object, Set<String> allowed, String path) {
        object.properties()
                .forEach(
                        e ->
                                require(
                                        allowed.contains(e.getKey()),
                                        path + "." + e.getKey(),
                                        "unknown field"));
    }

    public static final class ValidationException extends IllegalArgumentException {
        private final String path;
        private final String detail;

        private ValidationException(String path, String detail) {
            super(path + ": " + detail);
            this.path = path;
            this.detail = detail;
        }

        public String path() {
            return path;
        }

        public String detail() {
            return detail;
        }
    }

    private static void require(boolean condition, String path, String reason) {
        if (!condition) throw new ValidationException(path, reason);
    }

    private static void checkJson(JsonNode value, String path) {
        require(
                value.isNull()
                        || value.isString()
                        || value.isBoolean()
                        || value.isNumber()
                        || value.isObject()
                        || value.isArray(),
                path,
                "value is not JSON-compatible");
        if (value.isNumber()
                && (value.numberValue() instanceof Double || value.numberValue() instanceof Float))
            require(Double.isFinite(value.doubleValue()), path, "number must be finite");
        if (value.isObject())
            value.properties().forEach(e -> checkJson(e.getValue(), path + "." + e.getKey()));
        if (value.isArray())
            for (int i = 0; i < value.size(); i++) checkJson(value.get(i), path + "[" + i + "]");
    }

    private static void requireJsonValue(Object value, String path) {
        if (value == null || value instanceof String || value instanceof Boolean) return;
        if (value instanceof JsonNode node) {
            checkJson(node, path);
            return;
        }
        if (value instanceof Number number) {
            require(
                    number instanceof Byte
                            || number instanceof Short
                            || number instanceof Integer
                            || number instanceof Long
                            || number instanceof Float
                            || number instanceof Double
                            || number instanceof java.math.BigInteger
                            || number instanceof java.math.BigDecimal,
                    path,
                    "number is not JSON-compatible");
            if (number instanceof Double || number instanceof Float)
                require(Double.isFinite(number.doubleValue()), path, "number must be finite");
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.forEach(
                    (key, item) -> {
                        require(key instanceof String, path, "object keys must be strings");
                        requireJsonValue(item, path + "." + key);
                    });
            return;
        }
        if (value instanceof Collection<?> list) {
            for (Object item : list) requireJsonValue(item, path);
            return;
        }
        if (value.getClass().isArray()) {
            for (int i = 0; i < java.lang.reflect.Array.getLength(value); i++)
                requireJsonValue(java.lang.reflect.Array.get(value, i), path);
            return;
        }
        require(false, path, "value is not JSON-compatible: " + value.getClass().getName());
    }

    private static OutputSchemaManifest deriveSchema(JsonNode questions) {
        Map<String, OutputSchemaManifest> answers = new LinkedHashMap<>();
        questions
                .properties()
                .forEach(
                        e -> {
                            JsonNode q = e.getValue();
                            String type = q.get("type").stringValue();
                            Map<String, OutputSchemaManifest> fields = new LinkedHashMap<>();
                            OutputSchemaManifest identity = scalar("string", null);
                            identity.setEnumValues(List.of(type));
                            fields.put("type", identity);
                            if (type.equals("noul"))
                                fields.put(
                                        "noul",
                                        scalar("number", "Probability of yes, from 0 to 1."));
                            else {
                                JsonNode criteria = q.get("criteria");
                                boolean fixed =
                                        criteria != null
                                                && (type.equals("choice")
                                                        ? criteria.isObject()
                                                        : criteria.isArray());
                                OutputSchemaManifest value =
                                        scalar(
                                                type.equals("choice") ? "string" : "number",
                                                type.equals("choice")
                                                        ? "Selected candidate."
                                                        : fixed
                                                                ? "Score from 0 through "
                                                                        + (criteria.size() - 1)
                                                                        + "."
                                                                : "Score from 0 through supplied"
                                                                      + " level count minus one.");
                                if (type.equals("choice") && fixed)
                                    value.setEnumValues(
                                            criteria.properties().stream()
                                                    .map(Map.Entry::getKey)
                                                    .toList());
                                fields.put(type, value);
                                fields.put(
                                        "confidence", scalar("number", "Confidence from 0 to 1."));
                                Map<String, OutputSchemaManifest> probabilities =
                                        new LinkedHashMap<>();
                                if (fixed) {
                                    if (type.equals("choice"))
                                        criteria.properties()
                                                .forEach(
                                                        candidate ->
                                                                probabilities.put(
                                                                        candidate.getKey(),
                                                                        scalar(
                                                                                "number",
                                                                                "Probability from 0"
                                                                                    + " to 1.")));
                                    else
                                        for (int i = 0; i < criteria.size(); i++)
                                            probabilities.put(
                                                    Integer.toString(i),
                                                    scalar("number", "Probability from 0 to 1."));
                                }
                                var probabilityMap = object(probabilities, !fixed);
                                probabilityMap.setDescription(
                                        "Candidate or level to probability mapping, from 0 to 1.");
                                fields.put("probabilities", probabilityMap);
                                if (type.equals("score")) {
                                    var legend = object(Map.of(), true);
                                    legend.setDescription("Provider-returned level descriptions.");
                                    fields.put("legend", legend);
                                }
                            }
                            answers.put(e.getKey(), object(fields, true));
                        });
        return object(answers, false);
    }

    private static OutputSchemaManifest scalar(String type, String description) {
        var node = new OutputSchemaManifest();
        node.setType(type);
        node.setDescription(description);
        return node;
    }

    private static OutputSchemaManifest object(
            Map<String, OutputSchemaManifest> fields, boolean additional) {
        var node = scalar("object", null);
        node.setProperties(fields);
        node.setRequired(new ArrayList<>(fields.keySet()));
        node.setAdditionalProperties(additional);
        return node;
    }
}
