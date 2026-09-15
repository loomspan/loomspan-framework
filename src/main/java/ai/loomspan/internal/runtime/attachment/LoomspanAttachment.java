package ai.loomspan.internal.runtime.attachment;

import org.springframework.core.io.Resource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record LoomspanAttachment(
        String fieldPath,
        String name,
        String contentType,
        AttachmentMediaType mediaType,
        String source,
        Long sizeBytes,
        String sha256,
        Map<String, Object> metadata,
        Resource resource)
{
    public LoomspanAttachment
    {
        Objects.requireNonNull(fieldPath, "fieldPath must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(mediaType, "mediaType must not be null");
        Objects.requireNonNull(resource, "resource must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public Map<String, Object> descriptor()
    {
        LinkedHashMap<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("attachment", true);
        descriptor.put("fieldPath", fieldPath);
        descriptor.put("name", name);
        descriptor.put("contentType", contentType);
        descriptor.put("mediaType", mediaType.name());
        descriptor.put("source", source);
        if (sizeBytes != null)
        {
            descriptor.put("sizeBytes", sizeBytes);
        }
        if (sha256 != null && !sha256.isBlank())
        {
            descriptor.put("sha256", sha256);
        }
        if (!metadata.isEmpty())
        {
            descriptor.put("metadata", metadata);
        }
        return Map.copyOf(descriptor);
    }
}
