package ai.loomspan.internal.skill;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = false)
public class YamlSkillManifest
{
    enum Field
    {
        EXECUTOR("executor"),
        CONFIG("config"),
        TYPESAFE("typesafe"),
        MODEL("model"),
        THINKING_LEVEL("thinking_level"),
        PROMPT("prompt"),
        INPUT_SCHEMA("input_schema"),
        OUTPUT_SCHEMA("output_schema"),
        OUTPUT_FROM("output_from"),
        OUTPUT_BINDINGS("output_bindings"),
        PLANNING_MODE("planning_mode"),
        CONCURRENCY("concurrency"),
        MAX_STEPS("max_steps"),
        ALLOWED_SKILLS("allowed_skills"),
        LINTER("linter"),
        OUTPUT_SCHEMA_MAX_RETRIES("output_schema_max_retries");

        private final String yamlName;

        Field(String yamlName)
        {
            this.yamlName = yamlName;
        }

        String yamlName()
        {
            return yamlName;
        }
    }


    @JsonIgnore
    private ai.loomspan.internal.schema.CanonicalSchema compiledInputSchema;
    @JsonIgnore
    private ai.loomspan.internal.schema.CanonicalSchema compiledOutputSchema;
    ai.loomspan.internal.schema.CanonicalSchema compiledInputSchema() {
        if (compiledInputSchema == null && inputSchema != null) compiledInputSchema = new ai.loomspan.internal.schema.StandardSchemaCompiler().compile(inputSchema,
                ai.loomspan.internal.schema.StandardSchemaCompiler.Role.INPUT, ai.loomspan.internal.schema.StandardSchemaCompiler.Origin.AUTHORED);
        return compiledInputSchema;
    }
    ai.loomspan.internal.schema.CanonicalSchema compiledOutputSchema() {
        if (compiledOutputSchema == null && outputSchema != null) compiledOutputSchema = new ai.loomspan.internal.schema.StandardSchemaCompiler().compile(outputSchema,
                ai.loomspan.internal.schema.StandardSchemaCompiler.Role.OUTPUT, ai.loomspan.internal.schema.StandardSchemaCompiler.Origin.AUTHORED);
        return compiledOutputSchema;
    }
    void copySchemasFrom(YamlSkillManifest source) {
        inputSchema = source.getInputSchema(); outputSchema = source.getOutputSchema();
        compiledInputSchema = source.compiledInputSchema(); compiledOutputSchema = source.compiledOutputSchema();
    }

    private final EnumSet<Field> declaredFields = EnumSet.noneOf(Field.class);
    private String name;
    private String description;
    private String model;
    private String prompt;
    private String executor;
    @JsonIgnore
    private Map<String, Object> config = Map.of();
    @JsonIgnore
    private YamlSkillDefinition.Form declarationForm;
    void declarationForm(YamlSkillDefinition.Form form) { declarationForm = form; }
    YamlSkillDefinition.Form declarationForm() { return declarationForm; }
    private tools.jackson.databind.JsonNode typesafe;
    @JsonIgnore
    private ai.loomspan.internal.typesafe.TypeSafeDeclaration compiledTypesafe;
    @JsonIgnore
    public ai.loomspan.internal.typesafe.TypeSafeDeclaration compiledTypesafe() { return compiledTypesafe; }

    public tools.jackson.databind.JsonNode getTypesafe() { return typesafe == null ? null : typesafe.deepCopy(); }
    public void setTypesafe(tools.jackson.databind.JsonNode value) { declaredFields.add(Field.TYPESAFE); typesafe = value == null ? null : value.deepCopy(); compiledTypesafe = value == null || value.isNull() ? null : ai.loomspan.internal.typesafe.TypeSafeDeclaration.compile(value); }
    void copyTypesafeFrom(YamlSkillManifest source)
    {
        typesafe = source.typesafe == null ? null : source.typesafe.deepCopy();
        compiledTypesafe = source.compiledTypesafe;
    }
    @JsonIgnore
    public boolean isTypesafeDeclared() { return isDeclared(Field.TYPESAFE); }

    @JsonProperty("thinking_level")
    private String thinkingLevel;

    @JsonProperty("allowed_skills")
    private List<AllowedSkillManifest> allowedSkills = List.of();

    @JsonProperty("rbac_roles")
    private List<String> rbacRoles = List.of();

    @JsonProperty("planning_mode")
    private Boolean planningMode;

    private Boolean concurrency;

    @JsonProperty("max_steps")
    private Integer maxSteps;

    private LinterManifest linter;

    @JsonProperty("output_schema")
    private tools.jackson.databind.JsonNode outputSchema;

    @JsonProperty("output_from")
    private OutputFromManifest outputFrom;

    @JsonProperty("output_bindings")
    private Map<String, InputBindingManifest> outputBindings = Map.of();

    public Map<String, InputBindingManifest> getOutputBindings() { return outputBindings; }

    public void setOutputBindings(Map<String, InputBindingManifest> value) {
        declaredFields.add(Field.OUTPUT_BINDINGS);
        outputBindings = value == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    @JsonIgnore
    public boolean isOutputBindingsDeclared() { return isDeclared(Field.OUTPUT_BINDINGS); }

    @JsonProperty("input_schema")
    private tools.jackson.databind.JsonNode inputSchema;

    @JsonProperty("output_schema_max_retries")
    private Integer outputSchemaMaxRetries;



    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getModel()
    {
        return model;
    }

    public void setModel(String model)
    {
        declaredFields.add(Field.MODEL);
        this.model = model;
    }

    public String getPrompt()
    {
        return prompt;
    }

    public String getExecutor() { return executor; }
    public void setExecutor(String value) { declaredFields.add(Field.EXECUTOR); executor = value; }
    @JsonIgnore
    public Map<String, Object> getConfig() { return config; }
    @JsonProperty("config")
    public void setConfig(Map<String, Object> value) {
        declaredFields.add(Field.CONFIG);
        config = ai.loomspan.internal.util.ImmutableContainers.copyMap(java.util.Objects.requireNonNull(value));
    }

    public void setPrompt(String prompt)
    {
        declaredFields.add(Field.PROMPT);
        this.prompt = StringUtils.hasText(prompt) ? prompt.strip() : null;
    }

    public String getThinkingLevel()
    {
        return thinkingLevel;
    }

    public void setThinkingLevel(String thinkingLevel)
    {
        declaredFields.add(Field.THINKING_LEVEL);
        this.thinkingLevel = thinkingLevel;
    }

    public List<AllowedSkillManifest> getAllowedSkills()
    {
        return allowedSkills;
    }

    public void setAllowedSkills(List<AllowedSkillManifest> allowedSkills)
    {
        declaredFields.add(Field.ALLOWED_SKILLS);
        this.allowedSkills = allowedSkills == null ? List.of() : List.copyOf(allowedSkills);
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public static final class AllowedSkillManifest
    {
        private String name;

        @JsonProperty("min_tasks")
        private Integer minTasks;

        @JsonProperty("max_tasks")
        private Integer maxTasks;

        private Boolean required;

        @JsonProperty("input_bindings")
        private Map<String, InputBindingManifest> inputBindings = Map.of();
        public Map<String, InputBindingManifest> getInputBindings() { return inputBindings; }
        public void setInputBindings(Map<String, InputBindingManifest> value) {
            inputBindings = value == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(value));
        }

        public AllowedSkillManifest()
        {
        }

        public AllowedSkillManifest(String name, Integer minTasks, Integer maxTasks, Boolean required)
        {
            this.name = name;
            this.minTasks = minTasks;
            this.maxTasks = maxTasks;
            this.required = required;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public Integer getMinTasks()
        {
            return minTasks;
        }

        public void setMinTasks(Integer minTasks)
        {
            this.minTasks = minTasks;
        }

        public Integer getMaxTasks()
        {
            return maxTasks;
        }

        public void setMaxTasks(Integer maxTasks)
        {
            this.maxTasks = maxTasks;
        }

        public Boolean getRequired()
        {
            return required;
        }

        public void setRequired(Boolean required)
        {
            this.required = required;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public static final class InputBindingManifest {
        private String from;
        private String path;
        private String skill;
        public String getFrom() { return from; }
        public void setFrom(String value) { from = value; }
        public String getPath() { return path; }
        public void setPath(String value) { path = value; }
        public String getSkill() { return skill; }
        public void setSkill(String value) { skill = value; }
    }

    public List<String> getRbacRoles()
    {
        return rbacRoles;
    }

    public void setRbacRoles(List<String> rbacRoles)
    {
        this.rbacRoles = rbacRoles == null ? List.of() : List.copyOf(rbacRoles);
    }

    public Boolean getPlanningMode()
    {
        return planningMode;
    }

    public void setPlanningMode(Boolean planningMode)
    {
        declaredFields.add(Field.PLANNING_MODE);
        this.planningMode = planningMode;
    }

    public Boolean getConcurrency()
    {
        return concurrency;
    }

    public void setConcurrency(Boolean concurrency)
    {
        declaredFields.add(Field.CONCURRENCY);
        this.concurrency = concurrency;
    }

    public Integer getMaxSteps()
    {
        return maxSteps;
    }

    public void setMaxSteps(Integer maxSteps)
    {
        declaredFields.add(Field.MAX_STEPS);
        this.maxSteps = maxSteps;
    }

    public LinterManifest getLinter()
    {
        return linter;
    }

    public void setLinter(LinterManifest linter)
    {
        declaredFields.add(Field.LINTER);
        this.linter = linter;
    }





    public tools.jackson.databind.JsonNode getOutputSchema()
    {
        return outputSchema == null ? null : outputSchema.deepCopy();
    }

    public void setOutputSchema(tools.jackson.databind.JsonNode outputSchema)
    {
        declaredFields.add(Field.OUTPUT_SCHEMA);
        this.outputSchema = outputSchema == null ? null : outputSchema.deepCopy();
        this.compiledOutputSchema = null;
    }

    public OutputFromManifest getOutputFrom() { return outputFrom; }

    public void setOutputFrom(OutputFromManifest outputFrom) {
        declaredFields.add(Field.OUTPUT_FROM);
        this.outputFrom = outputFrom;
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public static class OutputFromManifest {
        private String skill;
        public String getSkill() { return skill; }
        public void setSkill(String skill) { this.skill = skill; }
    }

    public tools.jackson.databind.JsonNode getInputSchema()
    {
        return inputSchema == null ? null : inputSchema.deepCopy();
    }

    public void setInputSchema(tools.jackson.databind.JsonNode inputSchema)
    {
        declaredFields.add(Field.INPUT_SCHEMA);
        this.inputSchema = inputSchema == null ? null : inputSchema.deepCopy();
        this.compiledInputSchema = null;
    }

    public Integer getOutputSchemaMaxRetries()
    {
        return outputSchemaMaxRetries;
    }

    public void setOutputSchemaMaxRetries(Integer outputSchemaMaxRetries)
    {
        declaredFields.add(Field.OUTPUT_SCHEMA_MAX_RETRIES);
        this.outputSchemaMaxRetries = outputSchemaMaxRetries;
    }

    @JsonIgnore
    boolean isDeclared(Field field)
    {
        return declaredFields.contains(field);
    }

    @JsonIgnore
    Set<Field> declaredFields()
    {
        return Set.copyOf(declaredFields);
    }



    @JsonIgnore
    void restoreDeclaredFields(Set<Field> fields)
    {
        declaredFields.clear();
        declaredFields.addAll(fields);
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public static class LinterManifest
    {
        // ENG-016 supports regex linting only; future modes can be added here as siblings.
        private String type;

        @JsonProperty("max_retries")
        private Integer maxRetries;

        private RegexManifest regex;

        public String getType()
        {
            return type;
        }

        public void setType(String type)
        {
            this.type = StringUtils.hasText(type) ? type.trim().toLowerCase(Locale.ROOT) : type;
        }

        public Integer getMaxRetries()
        {
            return maxRetries;
        }

        public void setMaxRetries(Integer maxRetries)
        {
            this.maxRetries = maxRetries;
        }

        public RegexManifest getRegex()
        {
            return regex;
        }

        public void setRegex(RegexManifest regex)
        {
            this.regex = regex;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public static class RegexManifest
    {
        private String pattern;
        private String message;

        public String getPattern()
        {
            return pattern;
        }

        public void setPattern(String pattern)
        {
            this.pattern = StringUtils.hasText(pattern) ? pattern.trim() : pattern;
        }

        public String getMessage()
        {
            return message;
        }

        public void setMessage(String message)
        {
            this.message = StringUtils.hasText(message) ? message : null;
        }
    }


}
