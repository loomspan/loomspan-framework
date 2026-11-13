package ai.loomspan.internal.serialization;

import ai.loomspan.api.SkillParam;
import ai.loomspan.internal.schema.CanonicalSchema;
import ai.loomspan.internal.schema.StandardSchemaCompiler;
import org.springframework.core.io.Resource;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.TemporalAccessor;
import java.util.*;

/** Generates canonical contracts using the same introspection as Java binding. */
public final class LoomspanMethodInputSchemaGenerator
{
    private final ObjectMapper bindingMapper;
    private final ObjectMapper schemaMapper;
    private final StandardSchemaCompiler compiler = new StandardSchemaCompiler();

    public LoomspanMethodInputSchemaGenerator(ObjectMapper bindingMapper, ObjectMapper schemaMapper)
    {
        this.bindingMapper = Objects.requireNonNull(bindingMapper);
        this.schemaMapper = Objects.requireNonNull(schemaMapper);
    }

    public List<JavaType> parameterTypes(Method method, Class<?> targetClass)
    {
        JavaType owner = bindingMapper.constructType(targetClass).findSuperType(method.getDeclaringClass());
        if (owner == null) owner = bindingMapper.constructType(method.getDeclaringClass());
        var bindings = owner.getBindings();
        return Arrays.stream(method.getGenericParameterTypes())
                .map(type -> bindingMapper.getTypeFactory().resolveMemberType(type, bindings)).toList();
    }

    public CanonicalSchema generate(Method method, Method contract, List<JavaType> types)
    {
        ObjectNode root = schemaMapper.createObjectNode();
        root.put("$schema", StandardSchemaCompiler.DIALECT);
        root.put("type", "object");
        var properties = root.putObject("properties");
        var required = root.putArray("required");
        Parameter[] implementationParameters = method.getParameters();
        Parameter[] contractParameters = contract.getParameters();
        for (int index = 0; index < implementationParameters.length; index++)
        {
            Parameter parameter = contractParameters[index];
            String name = parameter.getName();
            ObjectNode schema = schemaFor(types.get(index), method, name, new ArrayList<>());
            SkillParam metadata = parameter.getAnnotation(SkillParam.class);
            if (metadata == null) metadata = implementationParameters[index].getAnnotation(SkillParam.class);
            if (metadata != null && !metadata.description().isBlank()) schema.put("description", metadata.description());
            properties.set(name, schema);
            if (metadata == null || metadata.required()) required.add(name);
        }
        root.put("additionalProperties", false);
        return compiler.compile(root, StandardSchemaCompiler.Role.INPUT, StandardSchemaCompiler.Origin.GENERATED);
    }

    public CanonicalSchema generate(Method method)
    {
        return generate(method, method, parameterTypes(method, method.getDeclaringClass()));
    }

    /** Shared with the binder: runtime handles are leaves, never Java bean contracts. */
    public static boolean isRuntimeLeaf(Class<?> raw)
    {
        return raw == byte[].class || Resource.class.isAssignableFrom(raw) || InputStream.class.isAssignableFrom(raw);
    }

    private ObjectNode schemaFor(JavaType type, Method method, String path, List<Expansion> active)
    {
        if (type instanceof tools.jackson.databind.type.ResolvedRecursiveType recursive && recursive.getSelfReferencedType() != null)
            type = recursive.getSelfReferencedType();
        if (type == null) type = bindingMapper.constructType(Object.class);
        ObjectNode schema = schemaMapper.createObjectNode();
        Class<?> raw = type.getRawClass();
        if (isRuntimeLeaf(raw))
        {
            schema.put("type", "string");
            schema.put("description", raw == byte[].class
                    ? "Provide a ref:// URI for binary content or an inline string value when appropriate."
                    : Resource.class.isAssignableFrom(raw) ? "Provide a ref:// URI for the resource content."
                    : "Provide a ref:// URI for the stream content.");
            schema.put("x-loomspan-runtime-ref-capable", true);
        }
        else if (type.isArrayType() || type.isCollectionLikeType())
        {
            enter(type, method, path, active);
            try {
                schema.put("type", "array");
                schema.set("items", schemaFor(type.getContentType(), method, path + "[]", active));
            } finally { active.removeLast(); }
        }
        else if (type.isMapLikeType())
        {
            JavaType key = type.getKeyType();
            if (key != null && key.getRawClass() != String.class && key.getRawClass() != Object.class)
                throw invalid(method, path, "unsupported map key type " + key.toCanonical());
            enter(type, method, path, active);
            try {
                schema.put("type", "object");
                schema.set("additionalProperties", schemaFor(type.getContentType(), method, path + ".*", active));
            } finally { active.removeLast(); }
        }
        else if (raw == boolean.class || raw == Boolean.class) schema.put("type", "boolean");
        else if (raw == byte.class || raw == short.class || raw == int.class || raw == long.class
                || raw == Byte.class || raw == Short.class || raw == Integer.class || raw == Long.class || raw == BigInteger.class)
            schema.put("type", "integer");
        else if (raw == float.class || raw == double.class || raw == Float.class || raw == Double.class
                || raw == BigDecimal.class || raw == Number.class) schema.put("type", "number");
        else if (raw.isEnum())
        {
            schema.put("type", "string");
            Enum<?>[] constants = (Enum<?>[]) raw.getEnumConstants();
            String[] names = Arrays.stream(constants).map(Enum::name).toArray(String[]::new);
            var config = bindingMapper.deserializationConfig();
            var annotated = bindingMapper._deserializationContext().introspectBeanDescriptionForCreation(type).getClassInfo();
            names = config.getAnnotationIntrospector().findEnumValues(config, annotated, constants, names);
            var values = schema.putArray("enum");
            for (int i = 0; i < constants.length; i++) values.add(names[i] == null ? constants[i].name() : names[i]);
        }
        else if (raw == UUID.class || CharSequence.class.isAssignableFrom(raw) || raw == Character.class || raw == char.class
                || TemporalAccessor.class.isAssignableFrom(raw) || raw.getPackageName().startsWith("java.time")
                || raw.getPackageName().startsWith("java.net") || raw.getPackageName().startsWith("java.nio.file"))
            schema.put("type", "string");
        else if (raw != Object.class)
        {
            enter(type, method, path, active);
            try
            {
                schema.put("type", "object");
                var properties = schema.putObject("properties");
                var required = schema.putArray("required");
                for (BeanPropertyDefinition property : bindingMapper._deserializationContext()
                        .introspectBeanDescriptionForCreation(type).findProperties())
                {
                    if (property.getPrimaryMember() == null) continue;
                    String propertyPath = path + "." + property.getName();
                    var config = bindingMapper.deserializationConfig();
                    for (var member : Arrays.asList(property.getPrimaryMember(), property.getField(), property.getGetter(), property.getSetter()))
                    {
                        if (member != null && config.getAnnotationIntrospector().findUnwrappingNameTransformer(config, member) != null)
                            throw invalid(method, propertyPath, "enabled @JsonUnwrapped is unsupported");
                    }
                    properties.set(property.getName(), schemaFor(property.getPrimaryMember().getType(), method, propertyPath, active));
                    required.add(property.getName());
                }
                schema.put("additionalProperties", false);
            }
            finally { active.removeLast(); }
        }
        return schema;
    }

    private static void enter(JavaType type, Method method, String path, List<Expansion> active)
    {
        for (Expansion ancestor : active)
        {
            if (ancestor.type().getRawClass() == type.getRawClass() && !strictSubtree(ancestor.type(), type))
                throw invalid(method, path, "recursive input type cycle " + active + " -> " + type.toCanonical() + " at " + path);
        }
        active.add(new Expansion(type, path));
    }

    // A repeated declaration is finite only when expansion consumes a smaller substituted type.
    // Box<Box<String>> -> Box<String> is finite; Node<T> -> Node<List<T>> is not.
    private static boolean strictSubtree(JavaType parent, JavaType candidate)
    {
        for (int i = 0; i < parent.containedTypeCount(); i++)
        {
            JavaType child = parent.containedType(i);
            if (child != null && (child.equals(candidate) || strictSubtree(child, candidate))) return true;
        }
        return false;
    }

    private record Expansion(JavaType type, String path)
    {
        @Override public String toString() { return type.toCanonical() + " at " + path; }
    }
    private static IllegalArgumentException invalid(Method method, String path, String detail)
    {
        return new IllegalArgumentException("Invalid Java input for " + method.toGenericString() + " at " + path + ": " + detail);
    }
}
