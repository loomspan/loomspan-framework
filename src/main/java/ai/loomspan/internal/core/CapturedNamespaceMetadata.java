package ai.loomspan.internal.core;

import ai.loomspan.internal.skill.SkillAdmissionSnapshot;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.Objects;

/** Primitive observation facts, independent of retained runtime resources. */
public record CapturedNamespaceMetadata(String entryNamespace, String generationId,
        Map<String, String> namespaceGenerationIds)
{
    public CapturedNamespaceMetadata
    {
        if (!SkillIdentity.normalizeNamespace(entryNamespace).equals(entryNamespace))
            throw new IllegalArgumentException("entryNamespace must be canonical");
        Objects.requireNonNull(generationId, "generationId must not be null");
        TreeMap<String, String> copied = new TreeMap<>(Objects.requireNonNull(namespaceGenerationIds));
        copied.forEach((namespace, id) -> {
            if (!SkillIdentity.normalizeNamespace(namespace).equals(namespace) || id == null || id.isBlank())
                throw new IllegalArgumentException("namespace generation mapping must be canonical and nonblank");
        });
        if (generationId.isBlank() || !generationId.equals(copied.get(entryNamespace)))
            throw new IllegalArgumentException("entry generation must match captured namespace mapping");
        namespaceGenerationIds = Collections.unmodifiableMap(copied);
    }

    public static CapturedNamespaceMetadata from(SkillAdmissionSnapshot snapshot)
    {
        Map<String, String> ids = new TreeMap<>();
        snapshot.generations().forEach((namespace, generation) -> ids.put(namespace, generation.id()));
        return new CapturedNamespaceMetadata(snapshot.entry().namespace(), snapshot.entryGeneration().id(), ids);
    }
}
