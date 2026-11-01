package ai.loomspan.internal.core;

import ai.loomspan.api.SkillKind;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import ai.loomspan.api.SkillMethod;
import ai.loomspan.api.SkillExecutor;
import ai.loomspan.api.SkillExecutorInvocation;
import ai.loomspan.api.SkillParam;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.serialization.LoomspanMethodInputSchemaGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SkillMethodBeanPostProcessor implements BeanPostProcessor, BeanFactoryAware
{
    private static final Logger log = LoggerFactory.getLogger(SkillMethodBeanPostProcessor.class);

    private final Map<String, CapabilityMetadata> capabilitiesByName = new LinkedHashMap<>();
    private final Map<String, SkillExecutorRegistration> executorsByName = new LinkedHashMap<>();
    private final ObjectMapper objectMapper;
    private final JavaSkillInvoker javaInvoker;
    private final SkillInputContractResolver inputContractResolver;
    private final LoomspanMethodInputSchemaGenerator schemaGenerator;
    private final Set<String> processedBeanNames = ConcurrentHashMap.newKeySet();
    private BeanFactory beanFactory;
    private final List<Declaration> declarations = new ArrayList<>();
    private boolean discoveryComplete;
    private record Declaration(String beanName, Method method, Method contractMethod,
            ai.loomspan.internal.security.SkillAccessPolicy policy) {}

    public synchronized void completeDiscovery()
    {
        if (discoveryComplete) return;
        if (beanFactory instanceof org.springframework.beans.factory.config.ConfigurableListableBeanFactory factory)
        {
            for (String name : factory.getBeanDefinitionNames())
            {
                if (name.startsWith("scopedTarget.")) continue;
                Class<?> type = factory.getType(name);
                if (type != null && !MethodIntrospector.selectMethods(type,
                        (MethodIntrospector.MetadataLookup<Boolean>) method -> hasDeclaration(method) ? Boolean.TRUE : null).isEmpty())
                {
                    Object bean = factory.getBean(name);
                    postProcessAfterInitialization(bean, name);
                }
            }
            var verifier = new ai.loomspan.internal.security.Jsr250EnforcementVerifier(beanFactory);
            for (Declaration declaration : declarations)
            {
                Object bean = factory.getBean(declaration.beanName());
                int modifiers = declaration.method().getModifiers();
                if (AopUtils.isCglibProxy(bean) && (java.lang.reflect.Modifier.isPrivate(modifiers)
                        || java.lang.reflect.Modifier.isFinal(modifiers) || java.lang.reflect.Modifier.isStatic(modifiers)))
                {
                    throw new IllegalStateException("Java skill '" + declaration.beanName() + "#"
                            + declaration.method().toGenericString() + "' cannot safely invoke a private, final, or static method"
                            + " through a CGLIB proxy; expose a proxyable instance method or use an interface-based Spring proxy");
                }
                Method invocableMethod = JavaSkillInvoker.selectRuntimeInvocableMethod(declaration.method(), declaration.contractMethod(), bean.getClass());
                verifier.verify(bean, declaration.method(), invocableMethod, AopUtils.getTargetClass(bean),
                        declaration.policy(), declaration.beanName());
            }
        }
        discoveryComplete = true;
    }

    public synchronized List<CapabilityMetadata> capabilities()
    {
        completeDiscovery();
        return List.copyOf(capabilitiesByName.values());
    }


    public synchronized List<SkillExecutorRegistration> executors()
    {
        completeDiscovery();
        return List.copyOf(executorsByName.values());
    }

    private static boolean hasDeclaration(Method method)
    {
        return AnnotatedElementUtils.findMergedAnnotation(method, SkillMethod.class) != null
                || AnnotatedElementUtils.findMergedAnnotation(method, SkillExecutor.class) != null;
    }

    public SkillMethodBeanPostProcessor()
    {
        this(ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().applicationConversion(),
                ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().schemaTree(), new SkillInputContractResolver());
    }

    public static SkillMethodBeanPostProcessor create(ObjectMapper applicationMapper,
            ObjectMapper schemaMapper, SkillInputContractResolver inputContractResolver)
    {
        return new SkillMethodBeanPostProcessor(applicationMapper, schemaMapper, inputContractResolver);
    }

    private SkillMethodBeanPostProcessor(ObjectMapper objectMapper, ObjectMapper schemaMapper,
            SkillInputContractResolver inputContractResolver)
    {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.javaInvoker = new JavaSkillInvoker(objectMapper);
        this.inputContractResolver = Objects.requireNonNull(inputContractResolver, "inputContractResolver must not be null");
        this.schemaGenerator = new LoomspanMethodInputSchemaGenerator(
                Objects.requireNonNull(schemaMapper, "schemaMapper must not be null"));
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException
    {
        this.beanFactory = Objects.requireNonNull(beanFactory, "beanFactory must not be null");
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException
    {
        if (beanName.startsWith("scopedTarget.")) return bean;
        synchronized (processedBeanNames)
        {
            if (processedBeanNames.contains(beanName))
            {
                return bean;
            }

            if (discoverAndRegisterSkills(bean, beanName))
            {
                processedBeanNames.add(beanName);
            }
        }
        return bean;
    }

    private boolean discoverAndRegisterSkills(Object bean, String beanName)
    {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        Map<Method, Boolean> discovered = MethodIntrospector.selectMethods(targetClass,
                (MethodIntrospector.MetadataLookup<Boolean>) method -> hasDeclaration(method) ? Boolean.TRUE : null);
        List<Method> canonicalMethods = discovered.keySet().stream()
                .map(BridgeMethodResolver::findBridgedMethod)
                .filter(method -> !method.isBridge() && !method.isSynthetic())
                .distinct().sorted(java.util.Comparator.comparing(Method::toGenericString)).toList();
        for (Method method : canonicalMethods)
        {
            Method contract = resolveContractMethod(beanName, targetClass, method);
            SkillExecutor executor = AnnotatedElementUtils.findMergedAnnotation(method, SkillExecutor.class);
            if (executor == null) executor = AnnotatedElementUtils.findMergedAnnotation(contract, SkillExecutor.class);
            if (executor != null) registerExecutor(beanName, targetClass, method, contract, executor);
            else
            {
                SkillMethod skill = AnnotatedElementUtils.findMergedAnnotation(method, SkillMethod.class);
                if (skill == null) skill = AnnotatedElementUtils.findMergedAnnotation(contract, SkillMethod.class);
                registerSkillMethod(beanName, targetClass, method, contract, skill);
            }
        }
        return !canonicalMethods.isEmpty();
    }

    private SkillIdentity identity(String beanName, Method method, String namespace, String localName, String role)
    {
        try { return new SkillIdentity(namespace, localName.isEmpty() ? method.getName() : localName); }
        catch (IllegalArgumentException ex)
        {
            throw new IllegalArgumentException("Invalid @" + role + " namespace/name at " + beanName + "#"
                    + method.toGenericString() + ": " + ex.getMessage(), ex);
        }
    }

    private void registerExecutor(String beanName, Class<?> targetClass, Method method, Method contract, SkillExecutor annotation)
    {
        if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != SkillExecutorInvocation.class || method.isVarArgs())
            throw new IllegalStateException("Invalid @SkillExecutor contract at " + beanName + "#" + method.toGenericString()
                    + ": requires exactly one SkillExecutorInvocation parameter");
        SkillIdentity identity = identity(beanName, method, annotation.namespace(), annotation.name(), "SkillExecutor");
        var policy = new ai.loomspan.internal.security.SkillAccessPolicyResolver().resolve(method, targetClass);
        var registration = new SkillExecutorRegistration(identity, annotation.config(), policy,
                new SkillSource(null, beanName, method.toGenericString(), null),
                invocation -> javaInvoker.invoke(beanFactory, beanName, method, contract, identity.toString(),
                        new Object[] { Objects.requireNonNull(invocation, "invocation must not be null") }));
        var existing = executorsByName.putIfAbsent(identity.toString(), registration);
        if (existing != null)
            throw new CapabilityCollisionException("Executor with name '" + identity + "' is already registered at "
                    + existing.source() + "; conflicting declaration at " + registration.source());
        declarations.add(new Declaration(beanName, method, contract, policy));
    }

    private void registerSkillMethod(String beanName, Class<?> targetClass, Method method, Method contractMethod, SkillMethod annotation)
    {
        validateSkillParameters(beanName, method, contractMethod);
        String capabilityDescription = annotation.description().isBlank() ? method.getName() : annotation.description();
        String inputSchema = buildInputSchema(method, contractMethod);
        String location = beanName + "#" + method.toGenericString();
        SkillIdentity identity = identity(beanName, method, annotation.namespace(), annotation.name(), "SkillMethod");
        String name = identity.toString();
        CapabilityInvoker invoker = arguments -> invokeSkillMethod(beanName, method, contractMethod, name, arguments);
        var policy = new ai.loomspan.internal.security.SkillAccessPolicyResolver()
                .resolve(method, targetClass);
        CapabilityMetadata metadata = new CapabilityMetadata(location, identity, capabilityDescription,
                SkillExecutionDescriptor.none(), policy, invoker, SkillKind.JAVA,
                new CapabilityToolDescriptor(name, capabilityDescription, inputSchema, null),
                inputContractResolver.resolveJavaCapability(inputSchema),
                new SkillSource(null, beanName, method.toGenericString(), null));
        CapabilityMetadata existing = capabilitiesByName.putIfAbsent(name, metadata);
        if (existing != null)
        {
            throw new CapabilityCollisionException("Capability with name '" + name
                    + "' is already registered at " + existing.id()
                    + "; conflicting declaration at " + metadata.id());
        }
        declarations.add(new Declaration(beanName, method, contractMethod, policy));
    }

    private void validateSkillParameters(String beanName, Method method, Method contractMethod)
    {
        Parameter[] parameters = method.getParameters();
        Parameter[] contractParameters = contractMethod.getParameters();
        for (int index = 0; index < parameters.length; index++)
        {
            SkillParam skillParam = contractParameters[index].getAnnotation(SkillParam.class);
            if (skillParam == null)
            {
                skillParam = parameters[index].getAnnotation(SkillParam.class);
            }
            if (skillParam != null && !skillParam.required() && parameters[index].getType().isPrimitive())
            {
                throw new IllegalStateException("Invalid @SkillParam contract on bean '" + beanName
                        + "' for method '" + method.getName() + "', parameter '"
                        + contractParameters[index].getName() + "': required=false cannot be used with primitive "
                        + parameters[index].getType().getTypeName()
                        + " because an omitted value binds to null; use the boxed type or mark the parameter required.");
            }
        }
    }

    private Method resolveContractMethod(String beanName, Class<?> targetClass, Method canonicalMethod)
    {
        List<Method> interfaceContracts = ClassUtils.getAllInterfacesForClassAsSet(targetClass).stream()
                .flatMap(type -> java.util.Arrays.stream(type.getMethods()))
                .filter(SkillMethodBeanPostProcessor::hasDeclaration)
                .filter(candidate -> JavaSkillInvoker.mapsToCanonicalMethod(candidate, targetClass, canonicalMethod))
                .distinct().sorted(java.util.Comparator.comparing(Method::toGenericString)).toList();
        List<Method> allDeclarations = new ArrayList<>(interfaceContracts);
        allDeclarations.add(canonicalMethod);
        boolean callable = allDeclarations.stream().anyMatch(method ->
                AnnotatedElementUtils.findMergedAnnotation(method, SkillMethod.class) != null);
        boolean executor = allDeclarations.stream().anyMatch(method ->
                AnnotatedElementUtils.findMergedAnnotation(method, SkillExecutor.class) != null);
        if (callable && executor)
            throw new IllegalStateException("Conflicting @SkillMethod and @SkillExecutor roles on bean '" + beanName
                    + "' for " + canonicalMethod.toGenericString() + ": " + allDeclarations);
        if (interfaceContracts.size() > 1 && interfaceContracts.stream().skip(1)
                .anyMatch(candidate -> !contractsEquivalent(interfaceContracts.getFirst(), candidate)))
            throw new IllegalStateException("Invalid Java contract on bean '" + beanName + "' for method '"
                    + canonicalMethod.getName() + "': annotated interfaces declare incompatible method or parameter metadata: "
                    + interfaceContracts + ". Consolidate the declarations into one public interface contract.");
        if (canonicalMethod.isAnnotationPresent(SkillExecutor.class)) return canonicalMethod;
        if (canonicalMethod.isAnnotationPresent(SkillMethod.class)
                && interfaceContracts.stream().noneMatch(this::hasSkillParameterMetadata)) return canonicalMethod;
        return interfaceContracts.stream().filter(this::hasSkillParameterMetadata).findFirst()
                .orElseGet(() -> interfaceContracts.stream().findFirst().orElse(canonicalMethod));
    }

    private boolean contractsEquivalent(Method left, Method right)
    {
        if (!Objects.equals(
                AnnotatedElementUtils.findMergedAnnotation(left, SkillMethod.class),
                AnnotatedElementUtils.findMergedAnnotation(right, SkillMethod.class)))
        {
            return false;
        }

        SkillExecutor leftExecutor = AnnotatedElementUtils.findMergedAnnotation(left, SkillExecutor.class);
        SkillExecutor rightExecutor = AnnotatedElementUtils.findMergedAnnotation(right, SkillExecutor.class);
        if (!Objects.equals(leftExecutor, rightExecutor)) return false;
        if (leftExecutor != null) return true;

        Parameter[] leftParameters = left.getParameters();
        Parameter[] rightParameters = right.getParameters();
        if (leftParameters.length != rightParameters.length)
        {
            return false;
        }

        for (int index = 0; index < leftParameters.length; index++)
        {
            if (!leftParameters[index].getName().equals(rightParameters[index].getName())
                    || !Objects.equals(
                            leftParameters[index].getAnnotation(SkillParam.class),
                            rightParameters[index].getAnnotation(SkillParam.class)))
            {
                return false;
            }
        }
        return true;
    }

    private boolean hasSkillParameterMetadata(Method method)
    {
        return java.util.Arrays.stream(method.getParameters())
                .anyMatch(parameter -> parameter.isAnnotationPresent(SkillParam.class));
    }

    private String buildInputSchema(Method method, Method contractMethod)
    {
        try
        {
            JsonNode schema = schemaGenerator.generate(method);
            JsonNode propertiesNode = schema.path("properties");
            if (propertiesNode instanceof ObjectNode propertiesObject)
            {
                Parameter[] parameters = method.getParameters();
                Parameter[] contractParameters = contractMethod.getParameters();
                for (int index = 0; index < parameters.length; index++)
                {
                    Parameter parameter = parameters[index];
                    Parameter contractParameter = contractParameters[index];
                    String schemaName = contractParameter.getName();
                    JsonNode parameterSchema = propertiesObject.remove(parameter.getName());
                    if (parameterSchema != null)
                    {
                        propertiesObject.set(schemaName, parameterSchema);
                        applyContractParameterMetadata((ObjectNode) schema, parameterSchema,
                                parameter.getName(), schemaName, contractParameter, parameter);
                        applyRuntimeInputSemantics(parameterSchema, objectMapper.constructType(parameter.getParameterizedType()));
                    }
                }
            }
            return objectMapper.writeValueAsString(schema);
        }
        catch (JacksonException ex)
        {
            throw new IllegalStateException("Failed to build method input schema for " + method, ex);
        }
    }

    private void applyContractParameterMetadata(ObjectNode rootSchema,
            JsonNode parameterSchema,
            String implementationParameterName,
            String parameterName,
            Parameter contractParameter,
            Parameter implementationParameter)
    {
        SkillParam skillParam = contractParameter.getAnnotation(SkillParam.class);
        if (skillParam == null)
        {
            skillParam = implementationParameter.getAnnotation(SkillParam.class);
        }
        if (skillParam != null
                && parameterSchema instanceof ObjectNode objectSchema
                && !skillParam.description().isBlank())
        {
            objectSchema.put("description", skillParam.description());
        }

        ArrayNode required = rootSchema.withArray("required");
        boolean generatedRequired = false;
        for (int index = required.size() - 1; index >= 0; index--)
        {
            String requiredName = required.get(index).asText();
            if (parameterName.equals(requiredName) || implementationParameterName.equals(requiredName))
            {
                generatedRequired = true;
                required.remove(index);
            }
        }
        if (skillParam == null ? generatedRequired : skillParam.required())
        {
            required.add(parameterName);
        }
    }

    private void applyRuntimeInputSemantics(JsonNode schemaNode, JavaType targetType)
    {
        if (!(schemaNode instanceof ObjectNode objectSchema) || targetType == null)
        {
            return;
        }

        Class<?> rawClass = targetType.getRawClass();
        if (isRefCapableBindableType(rawClass))
        {
            rewriteAsRefFriendlyString(objectSchema, rawClass);
            return;
        }

        if (targetType.isCollectionLikeType() || rawClass.isArray())
        {
            JsonNode itemsNode = objectSchema.get("items");
            if (itemsNode != null)
            {
                applyRuntimeInputSemantics(itemsNode, targetType.getContentType());
            }
            return;
        }

        if (targetType.isMapLikeType())
        {
            JsonNode additionalPropertiesNode = objectSchema.get("additionalProperties");
            if (additionalPropertiesNode != null && additionalPropertiesNode.isObject())
            {
                applyRuntimeInputSemantics(additionalPropertiesNode, targetType.getContentType());
            }
            return;
        }

        if (isSimpleBindableType(rawClass))
        {
            return;
        }

        JsonNode propertiesNode = objectSchema.get("properties");
        if (!(propertiesNode instanceof ObjectNode propertiesObject))
        {
            return;
        }

        propertyTypes(targetType).forEach((propertyName, propertyType) ->
        {
            JsonNode propertySchema = propertiesObject.get(propertyName);
            if (propertySchema != null)
            {
                applyRuntimeInputSemantics(propertySchema, propertyType);
            }
        });
    }

    private void rewriteAsRefFriendlyString(ObjectNode schemaNode, Class<?> rawClass)
    {
        schemaNode.removeAll();
        schemaNode.put("type", "string");
        schemaNode.put("description", refFriendlyDescription(rawClass));
        schemaNode.put("x-loomspan-runtime-ref-capable", true);
    }

    private String refFriendlyDescription(Class<?> rawClass)
    {
        if (byte[].class.equals(rawClass))
        {
            return "Provide a ref:// URI for binary content or an inline string value when appropriate.";
        }
        if (Resource.class.isAssignableFrom(rawClass))
        {
            return "Provide a ref:// URI for the resource content.";
        }
        if (InputStream.class.isAssignableFrom(rawClass))
        {
            return "Provide a ref:// URI for the stream content.";
        }
        return "Provide the value inline or as a ref:// URI.";
    }

    private Object invokeSkillMethod(String beanName,
            Method method,
            Method contractMethod,
            String skillName,
            Map<String, Object> arguments)
    {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        ArrayList<InputStream> openedStreams = new ArrayList<>();

        try
        {
            Object[] invocationArguments = bindArguments(method, contractMethod, safeArguments, openedStreams);
            return javaInvoker.invoke(beanFactory, beanName, method, contractMethod, skillName, invocationArguments);
        }
        finally
        {
            closeStreams(skillName, openedStreams);
        }
    }

    private Object[] bindArguments(Method method,
            Method contractMethod,
            Map<String, Object> arguments,
            List<InputStream> openedStreams)
    {
        Parameter[] parameters = method.getParameters();
        Parameter[] contractParameters = contractMethod.getParameters();
        Object[] bound = new Object[parameters.length];

        for (int index = 0; index < parameters.length; index++)
        {
            Parameter parameter = parameters[index];
            Parameter contractParameter = contractParameters[index];
            Object rawValue = arguments.get(contractParameter.getName());
            SkillParam skillParam = contractParameter.getAnnotation(SkillParam.class);
            if (skillParam == null)
            {
                skillParam = parameter.getAnnotation(SkillParam.class);
            }
            if (rawValue == null && skillParam != null && !skillParam.required())
            {
                bound[index] = null;
                continue;
            }
            bound[index] = convertArgument(parameter, rawValue, openedStreams);
        }
        return bound;
    }

    private Object convertArgument(Parameter parameter, Object rawValue, List<InputStream> openedStreams)
    {
        if (rawValue == null)
        {
            return null;
        }

        JavaType parameterJavaType = objectMapper.constructType(parameter.getParameterizedType());
        Class<?> parameterType = parameterJavaType.getRawClass();

        if (Resource.class.isAssignableFrom(parameterType))
        {
            return convertToResource(parameter, rawValue);
        }
        if (byte[].class.equals(parameterType))
        {
            return convertToBytes(parameter, rawValue);
        }
        if (InputStream.class.isAssignableFrom(parameterType))
        {
            InputStream stream = convertToInputStream(parameter, rawValue);
            openedStreams.add(stream);
            return stream;
        }
        if (String.class.equals(parameterType) && rawValue instanceof Resource resource)
        {
            return convertResourceToString(parameter, resource);
        }
        if (parameterType.isInstance(rawValue)
                && !parameterJavaType.isContainerType()
                && isSimpleBindableType(parameterType))
        {
            return rawValue;
        }

        Object materializedValue = materializeValue(rawValue, parameterJavaType, openedStreams);
        return objectMapper.convertValue(materializedValue, parameterJavaType);
    }

    private Object materializeValue(Object rawValue, JavaType targetType, List<InputStream> openedStreams)
    {
        if (rawValue == null)
        {
            return null;
        }

        Class<?> rawClass = targetType.getRawClass();
        if (Resource.class.isAssignableFrom(rawClass))
        {
            return rawValue;
        }
        if (String.class.equals(rawClass) && rawValue instanceof Resource resource)
        {
            return convertResourceToString(null, resource);
        }
        if (byte[].class.equals(rawClass) && rawValue instanceof Resource resource)
        {
            return convertToBytes(null, resource);
        }
        if (InputStream.class.isAssignableFrom(rawClass) && rawValue instanceof Resource resource)
        {
            InputStream stream = convertToInputStream(null, resource);
            openedStreams.add(stream);
            return stream;
        }
        if (targetType.isMapLikeType() && rawValue instanceof Map<?, ?> mapValue)
        {
            JavaType valueType = targetType.getContentType() == null
                    ? objectMapper.constructType(Object.class)
                    : targetType.getContentType();
            Map<Object, Object> materialized = new LinkedHashMap<>();
            mapValue.forEach((key, value) -> materialized.put(key, materializeValue(value, valueType, openedStreams)));
            return materialized;
        }
        if (targetType.isCollectionLikeType() && rawValue instanceof List<?> listValue)
        {
            JavaType contentType = targetType.getContentType() == null
                    ? objectMapper.constructType(Object.class)
                    : targetType.getContentType();
            return listValue.stream()
                    .map(value -> materializeValue(value, contentType, openedStreams))
                    .toList();
        }
        if (rawClass.isArray() && rawValue instanceof List<?> listValue)
        {
            JavaType contentType = targetType.getContentType() == null
                    ? objectMapper.constructType(Object.class)
                    : targetType.getContentType();
            return listValue.stream()
                    .map(value -> materializeValue(value, contentType, openedStreams))
                    .toList();
        }
        if (rawValue instanceof Map<?, ?> mapValue && !isSimpleBindableType(rawClass))
        {
            Map<String, JavaType> propertyTypes = propertyTypes(targetType);
            Map<String, Object> materialized = new LinkedHashMap<>();
            mapValue.forEach((key, value) ->
            {
                String propertyName = String.valueOf(key);
                JavaType propertyType = propertyTypes.getOrDefault(propertyName, objectMapper.constructType(Object.class));
                materialized.put(propertyName, materializeValue(value, propertyType, openedStreams));
            });
            return materialized;
        }
        return rawValue;
    }

    private Map<String, JavaType> propertyTypes(JavaType targetType)
    {
        return objectMapper._deserializationContext()
                .introspectBeanDescriptionForCreation(targetType)
                .findProperties()
                .stream()
                .filter(definition -> definition.getPrimaryMember() != null)
                .collect(Collectors.toMap(
                        BeanPropertyDefinition::getName,
                        definition -> definition.getPrimaryMember().getType(),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private boolean isSimpleBindableType(Class<?> rawClass)
    {
        return rawClass.isPrimitive()
                || Number.class.isAssignableFrom(rawClass)
                || CharSequence.class.isAssignableFrom(rawClass)
                || Boolean.class.equals(rawClass)
                || Enum.class.isAssignableFrom(rawClass)
                || Object.class.equals(rawClass);
    }

    private boolean isRefCapableBindableType(Class<?> rawClass)
    {
        return byte[].class.equals(rawClass)
                || Resource.class.isAssignableFrom(rawClass)
                || InputStream.class.isAssignableFrom(rawClass);
    }

    private Resource convertToResource(Parameter parameter, Object rawValue)
    {
        if (rawValue instanceof Resource resource)
        {
            return resource;
        }
        throw new IllegalArgumentException("Parameter '" + parameterName(parameter) + "' requires a Resource-backed ref payload");
    }

    private byte[] convertToBytes(Parameter parameter, Object rawValue)
    {
        if (rawValue instanceof byte[] bytes)
        {
            return bytes;
        }
        if (rawValue instanceof Resource resource)
        {
            try
            {
                return StreamUtils.copyToByteArray(resource.getInputStream());
            }
            catch (IOException ex)
            {
                throw new IllegalStateException("Failed to read binary payload for parameter '" + parameterName(parameter) + "'", ex);
            }
        }
        return objectMapper.convertValue(rawValue, byte[].class);
    }

    private InputStream convertToInputStream(Parameter parameter, Object rawValue)
    {
        if (rawValue instanceof InputStream stream)
        {
            return stream;
        }
        if (rawValue instanceof Resource resource)
        {
            try
            {
                return resource.getInputStream();
            }
            catch (IOException ex)
            {
                throw new IllegalStateException("Failed to open stream for parameter '" + parameterName(parameter) + "'", ex);
            }
        }
        throw new IllegalArgumentException("Parameter '" + parameterName(parameter) + "' requires a Resource-backed ref payload");
    }

    private String convertResourceToString(Parameter parameter, Resource resource)
    {
        try
        {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Failed to read text payload for parameter '" + parameterName(parameter) + "'", ex);
        }
    }

    private String parameterName(Parameter parameter)
    {
        return parameter == null ? "<nested>" : parameter.getName();
    }

    private void closeStreams(String capabilityName, List<InputStream> openedStreams)
    {
        for (InputStream stream : openedStreams)
        {
            try
            {
                stream.close();
            }
            catch (IOException ex)
            {
                log.warn("Capability '{}' failed while closing opened ref stream", capabilityName, ex);
            }
        }
    }
}
