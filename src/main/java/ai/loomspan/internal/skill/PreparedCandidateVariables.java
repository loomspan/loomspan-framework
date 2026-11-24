package ai.loomspan.internal.skill;

import ai.loomspan.api.ExecutionConfiguration;
import ai.loomspan.api.SkillDocument;
import ai.loomspan.internal.serialization.AuthoredYamlVariables;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Short-lived authored candidate. Resolution state never escapes into a generation. */
final class PreparedCandidateVariables {
    private final AuthoredYamlVariables.Document execution;
    private final Object stagedExecution;
    private final Map<String, AuthoredYamlVariables.Document> skills = new LinkedHashMap<>();
    private final List<AuthoredYamlVariables.Occurrence> occurrences = new ArrayList<>();
    private final List<ai.loomspan.api.SkillValidationIssue> skillIssues = new ArrayList<>();

    PreparedCandidateVariables(ExecutionConfiguration configuration, List<SkillDocument> documents) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        try { execution = AuthoredYamlVariables.execution(configuration.yaml(), "<configuration>"); }
        catch (org.snakeyaml.engine.v2.exceptions.YamlEngineException failure) {
            throw new IllegalArgumentException(failure.getMessage(), failure);
        }
        occurrences.addAll(execution.discover());
        stagedExecution = execution.staged();
        for (var document : documents) {
            if (document == null || document.sourceName() == null || document.sourceName().isBlank() || document.yaml() == null)
                continue; // The catalog owns per-document structural feedback.
            try {
                var authored = AuthoredYamlVariables.skill(document.yaml(), document.sourceName());
                var discovered = authored.discover();
                if (skills.putIfAbsent(document.sourceName(), authored) == null) occurrences.addAll(discovered);
            } catch (RuntimeException failure) {
                skillIssues.add(new ai.loomspan.api.SkillValidationIssue(ai.loomspan.api.SkillValidationIssue.Severity.ERROR,
                        document.sourceName(), null, "config", failure.getMessage()));
            }
        }
    }

    YamlSkillCatalog.CheckedDocuments checkedSkills(ai.loomspan.autoconfigure.LoomspanProperties properties,
            List<SkillDocument> documents, boolean logWarnings, Map<String, Map<?, ?>> trees, String namespace) {
        var checked = new YamlSkillCatalog(properties).checkedSupplied(namespace, documents, logWarnings, trees);
        var issues = new ArrayList<>(checked.issues());
        issues.addAll(skillIssues);
        return new YamlSkillCatalog.CheckedDocuments(checked.definitions(), List.copyOf(issues));
    }

    Object stagedExecution() { return stagedExecution; }

    Map<String, Map<?, ?>> stagedSkills() {
        var result = new LinkedHashMap<String, Map<?, ?>>();
        skills.forEach((source, document) -> {
            Object tree = knownKeys(document.staged());
            if (tree instanceof Map<?, ?> map) result.put(source, map);
        });
        return result;
    }

    Resolved resolve(Function<String, String> source, Map<String, String> explicit) {
        var names = new LinkedHashSet<String>();
        occurrences.forEach(occurrence -> names.add(occurrence.name()));
        if (explicit != null && !names.containsAll(explicit.keySet()))
            throw new IllegalArgumentException("variableValues contains an unused entry");
        var values = new LinkedHashMap<String, String>();
        // The document owns fixed diagnostics and catches lookup failures at the authored occurrence.
        Function<String, String> memoized = name -> {
            if (!values.containsKey(name)) values.put(name, source.apply(name));
            return values.get(name);
        };
        Object resolvedExecution = execution.substitute(memoized);
        var resolvedSkills = new LinkedHashMap<String, Map<?, ?>>();
        skills.forEach((label, document) -> {
            Object tree = document.substitute(memoized);
            if (tree instanceof Map<?, ?> map) resolvedSkills.put(label, map);
        });
        return new Resolved(resolvedExecution, resolvedSkills);
    }

    record Resolved(Object execution, Map<String, Map<?, ?>> skills) {}

    /** Drop unknown keys for typed staging while preserving explicit unknown scalar leaves. */
    static Object knownKeys(Object value) { return knownKeys(value, new java.util.IdentityHashMap<>()); }

    private static Object knownKeys(Object value, java.util.IdentityHashMap<Object, Object> copies) {
        if (!(value instanceof Map<?, ?>) && !(value instanceof List<?>)) return value;
        if (copies.containsKey(value)) return copies.get(value);
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<Object, Object>();
            copies.put(value, result);
            map.forEach((key, item) -> {
                if (!(key instanceof AuthoredYamlVariables.Unresolved)) result.put(key, knownKeys(item, copies));
            });
            return result;
        }
        var result = new ArrayList<Object>();
        copies.put(value, result);
        ((List<?>) value).forEach(item -> result.add(knownKeys(item, copies)));
        return result;
    }

    /** A dynamic key can supply a missing selector segment. Known scalar shapes still fail. */
    boolean unknownSelector(String namespace, String path) {
        Object selected = stagedExecution();
        for (String prefix : List.of("loomspan", "executor-config")) {
            if (!(selected instanceof Map<?, ?> map)) return false;
            selected = map.get(prefix);
        }
        if (!(selected instanceof Map<?, ?> namespaces)) return false;
        selected = namespaces.get(namespace);
        return selection(selected, path.split("\\.", -1), 0) == Selection.UNRESOLVED;
    }

    private enum Selection { INVALID, KNOWN, UNRESOLVED }

    private static Selection selection(Object selected, String[] segments, int index) {
        if (!(selected instanceof Map<?, ?> map)) return Selection.INVALID;
        if (index == segments.length) return Selection.KNOWN;
        if (map.containsKey(segments[index])) return selection(map.get(segments[index]), segments, index + 1);
        for (var entry : map.entrySet()) {
            if (entry.getKey() instanceof AuthoredYamlVariables.Unresolved
                    && selection(entry.getValue(), segments, index + 1) != Selection.INVALID) return Selection.UNRESOLVED;
        }
        return Selection.INVALID;
    }
}
