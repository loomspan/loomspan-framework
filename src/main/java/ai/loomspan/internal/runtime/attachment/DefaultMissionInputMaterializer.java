package ai.loomspan.internal.runtime.attachment;

import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.MissionInputMessageFormatter;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.runtime.input.SkillInputValidator;
import ai.loomspan.internal.schema.StandardSchemaCompiler;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import tools.jackson.databind.JsonNode;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import ai.loomspan.internal.vfs.RefResolver;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class DefaultMissionInputMaterializer implements MissionInputMaterializer
{
    public static final DataSize DEFAULT_MAX_SIZE = DataSize.ofMegabytes(20);

    private final RefResolver refResolver;
    private final SkillInputContractResolver inputContractResolver;
    private final long maxSizeBytes;

    private long maxSizeBytes()
    {
        return ai.loomspan.internal.core.ExecutionBindingScope.current()
                .map(binding -> binding.generation().runtime())
                .map(runtime -> runtime.properties().getSession().getAttachments().getMaxSize().toBytes())
                .orElse(maxSizeBytes);
    }

    public DefaultMissionInputMaterializer(RefResolver refResolver)
    {
        this(refResolver, new SkillInputContractResolver(), DEFAULT_MAX_SIZE);
    }

    public DefaultMissionInputMaterializer(RefResolver refResolver,
            SkillInputContractResolver inputContractResolver,
            DataSize maxSize)
    {
        this.refResolver = Objects.requireNonNull(refResolver, "refResolver must not be null");
        this.inputContractResolver = Objects.requireNonNull(inputContractResolver, "inputContractResolver must not be null");
        this.maxSizeBytes = Objects.requireNonNull(maxSize, "maxSize must not be null").toBytes();
    }

    @Override
    public RenderedMissionInput materialize(LoomspanSession session,
            YamlSkillDefinition definition,
            String objective,
            @Nullable Map<String, Object> missionInput)
    {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(definition, "definition must not be null");
        Map<String, Object> safeInput = missionInput == null ? Map.of() : missionInput;
        if (!definition.hasDeclaredInputSchema())
        {
            String userText = MissionInputMessageFormatter.buildUserMessage(objective, safeInput);
            return new RenderedMissionInput(userText, List.of(), safeInput);
        }

        var admission = new SkillInputValidator().validate(safeInput, inputContractResolver.resolveYamlCapability(definition));
        if (!admission.valid()) throw new IllegalArgumentException("Invalid attachment mission input: " + admission.issues());
        JsonNode schema = definition.inputSchema().tree();
        List<LoomspanAttachment> attachments = new ArrayList<>();
        Object traceSafe = materializeNode(session, schema, safeInput, "", attachments);
        Map<String, Object> traceSafeInput = traceSafe instanceof Map<?, ?> map
                ? castMap(map)
                : Map.of();
        AttachmentSchemaNodes.replaceAttachmentLeaves(schema);
        var descriptorContract = new StandardSchemaCompiler().compile(schema,
                StandardSchemaCompiler.Role.INPUT, StandardSchemaCompiler.Origin.GENERATED);
        var issues = descriptorContract.validate(LoomspanJacksonCodecs.defaults().schemaTree().valueToTree(traceSafeInput));
        if (!issues.isEmpty()) throw new IllegalArgumentException("Invalid materialized attachment input: " + issues);
        return new RenderedMissionInput(
                MissionInputMessageFormatter.buildUserMessage(objective, traceSafeInput),
                attachments,
                traceSafeInput);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> map)
    {
        return (Map<String, Object>) map;
    }

    private Object materializeNode(LoomspanSession session,
            JsonNode schema,
            Object value,
            String path,
            List<LoomspanAttachment> attachments)
    {
        if (value == null || schema == null) return value;
        if (AttachmentSchemaNodes.isAttachment(schema))
        {
            LoomspanAttachment attachment = materializeAttachment(session, schema, value, path);
            attachments.add(attachment);
            return attachment.descriptor();
        }
        if (value instanceof Map<?, ?> map)
        {
            LinkedHashMap<String, Object> traceSafe = new LinkedHashMap<>();
            map.forEach((key, childValue) ->
            {
                String childName = String.valueOf(key);
                JsonNode childSchema = AttachmentSchemaNodes.property(schema, childName);
                if (childSchema == null)
                {
                    traceSafe.put(childName, childValue);
                }
                else
                {
                    traceSafe.put(childName, materializeNode(session, childSchema, childValue, AttachmentSchemaNodes.child(path, childName), attachments));
                }
            });
            return java.util.Collections.unmodifiableMap(traceSafe);
        }
        if (value instanceof List<?> list && schema.has("items"))
        {
            List<Object> traceSafe = new ArrayList<>(list.size());
            for (int index = 0; index < list.size(); index++)
            {
                traceSafe.add(materializeNode(session, schema.get("items"), list.get(index), AttachmentSchemaNodes.child(path, Integer.toString(index)), attachments));
            }
            return java.util.Collections.unmodifiableList(traceSafe);
        }
        return value;
    }

    private LoomspanAttachment materializeAttachment(LoomspanSession session,
            JsonNode schema,
            Object value,
            String path)
    {
        String source;
        Resource resource;
        if (value instanceof String ref && ref.matches("^ref://\\S+$"))
        {
            source = ref;
            try
            {
                Object resolved = refResolver.resolveArgument(ref, session);
                if (!(resolved instanceof Resource resolvedResource))
                {
                    throw new IllegalArgumentException("resolved value was not a Resource");
                }
                resource = resolvedResource;
            }
            catch (RuntimeException ex)
            {
                throw new IllegalArgumentException("Attachment field '" + path + "' could not resolve " + ref + ": "
                        + ex.getMessage(), ex);
            }
        }
        else if (value instanceof Resource resourceValue)
        {
            source = "resource";
            resource = resourceValue;
        }
        else
        {
            throw new IllegalArgumentException("Attachment field '" + path
                    + "' must be a strict ref:// value or Spring Resource");
        }
        if (resource.isOpen())
        {
            throw new IllegalArgumentException("Attachment field '" + path
                    + "' must be backed by a repeatable Resource, not an already-open stream");
        }

        JsonNode annotation = schema.get("x-loomspan-attachment");
        List<String> allowedContentTypes = new ArrayList<>();
        annotation.path("allowed_content_types").forEach(type -> allowedContentTypes.add(type.asText()));
        AttachmentMediaType mediaType = AttachmentMediaType.fromManifest(annotation.path("media_type").asText());
        String contentType = detectContentType(resource, allowedContentTypes);
        var parsed = MimeTypeUtils.parseMimeType(contentType);
        if (allowedContentTypes.stream().map(MimeTypeUtils::parseMimeType).noneMatch(parsed::equals))
        {
            throw new IllegalArgumentException("Attachment field '" + path + "' content type '" + contentType
                    + "' is not allowed; expected one of " + allowedContentTypes);
        }
        validateMediaSignature(resource, parsed.getType() + "/" + parsed.getSubtype(), mediaType, path);
        ReadResult read = digest(resource, path, maxSizeBytes());
        return new LoomspanAttachment(
                path,
                resource.getFilename(),
                contentType,
                mediaType,
                source,
                read.size(),
                read.sha256(),
                Map.of("allowedContentTypes", allowedContentTypes),
                resource);
    }

    private String detectContentType(Resource resource, List<String> allowedContentTypes)
    {
        String filename = resource.getFilename();
        String byExtension = contentTypeFromFilename(filename);
        if (byExtension != null)
        {
            return byExtension;
        }
        if (allowedContentTypes.size() == 1)
        {
            return allowedContentTypes.get(0);
        }
        return MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE;
    }

    @Nullable
    private String contentTypeFromFilename(@Nullable String filename)
    {
        if (!StringUtils.hasText(filename) || !filename.contains("."))
        {
            return null;
        }
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (extension)
        {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            case "txt", "text" -> "text/plain";
            case "json" -> "application/json";
            case "csv" -> "text/csv";
            default -> null;
        };
    }

    private void validateMediaSignature(Resource resource,
            String contentType,
            AttachmentMediaType mediaType,
            String path)
    {
        if (mediaType != AttachmentMediaType.IMAGE)
        {
            return;
        }
        byte[] header = readHeader(resource, 16, path);
        boolean valid = switch (contentType)
        {
            case "image/jpeg" -> startsWith(header, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/gif" -> startsWith(header, 0x47, 0x49, 0x46, 0x38);
            case "image/webp" -> startsWith(header, 0x52, 0x49, 0x46, 0x46)
                    && header.length >= 12
                    && header[8] == 0x57
                    && header[9] == 0x45
                    && header[10] == 0x42
                    && header[11] == 0x50;
            default -> false;
        };
        if (!valid)
        {
            throw new IllegalArgumentException("Attachment field '" + path
                    + "' content does not match declared image type '" + contentType + "'");
        }
    }

    private byte[] readHeader(Resource resource, int byteCount, String path)
    {
        try (InputStream inputStream = resource.getInputStream())
        {
            return inputStream.readNBytes(byteCount);
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException("Attachment field '" + path
                    + "' could not be read for content-type validation: " + ex.getMessage(), ex);
        }
    }

    private boolean startsWith(byte[] header, int... expected)
    {
        if (header.length < expected.length)
        {
            return false;
        }
        for (int index = 0; index < expected.length; index++)
        {
            if ((header[index] & 0xFF) != expected[index])
            {
                return false;
            }
        }
        return true;
    }

    private record ReadResult(long size, String sha256) {}

    private ReadResult digest(Resource resource, String path, long limit)
    {
        try (InputStream inputStream = resource.getInputStream())
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = inputStream.read(buffer, 0,
                    (int) (Math.min(buffer.length - 1L, Math.max(0, limit - total)) + 1))) != -1)
            {
                if (read > limit - total)
                {
                    throw new IllegalArgumentException("Attachment field '" + path
                            + "' is exceeding configured limit of " + limit + " bytes");
                }
                total += read;
                digest.update(buffer, 0, read);
            }
            return new ReadResult(total, HexFormat.of().formatHex(digest.digest()));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException("Attachment field '" + path + "' could not be read: " + ex.getMessage(), ex);
        }
    }

}
