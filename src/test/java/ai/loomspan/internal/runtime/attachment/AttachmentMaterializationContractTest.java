package ai.loomspan.internal.runtime.attachment;

import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.schema.StandardSchemaCompiler;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import ai.loomspan.internal.vfs.RefResolver;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.*;
import org.springframework.util.unit.DataSize;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The actual validator and materializer with only session/catalog/resolver collaborators mocked. */
class AttachmentMaterializationContractTest
{
    private final LoomspanSession session = mock(LoomspanSession.class);
    private final Map<Object, Object> resolved = new HashMap<>();
    private final RefResolver resolver = (value, ignored) -> {
        if (!resolved.containsKey(value)) throw new IllegalArgumentException("missing " + value);
        return resolved.get(value);
    };
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x45, 0x23, 0x11};
    private static final String IMAGE = leaf("image", "image/jpeg", false), PDF = leaf("pdf", "application/pdf", false);
    private static String leaf(String media, String mime, boolean nullable) {
        return "{\"type\":" + (nullable ? "[\"string\",\"null\"]" : "\"string\"")
                + ",\"x-loomspan-attachment\":{\"media_type\":\"" + media + "\",\"allowed_content_types\":[\"" + mime + "\"]}}";
    }
    private static String root(String leaf) {
        return "{\"type\":\"object\",\"required\":[\"file\"],\"properties\":{\"file\":" + leaf + "},\"additionalProperties\":false}";
    }
    private YamlSkillDefinition definition(String schema) {
        var def = mock(YamlSkillDefinition.class);
        when(def.hasDeclaredInputSchema()).thenReturn(true);
        when(def.inputSchema()).thenReturn(new StandardSchemaCompiler().compile(schema, StandardSchemaCompiler.Role.INPUT, StandardSchemaCompiler.Origin.AUTHORED));
        return def;
    }
    private RenderedMissionInput render(String schema, Map<String, Object> input) { return render(definition(schema), input, 1024); }
    private RenderedMissionInput render(YamlSkillDefinition def, Map<String, Object> input, long limit) {
        return new DefaultMissionInputMaterializer(resolver, new SkillInputContractResolver(), DataSize.ofBytes(limit)).materialize(session, def, "Read file", input);
    }
    private static Resource resource(byte[] bytes, String name) {
        return new ByteArrayResource(bytes) { @Override public String getFilename() { return name; } };
    }

    @Test void referenceAndDirectInputsRetainIdentityActualMetadataAndCanonicalSchemas() throws Exception {
        Resource image = resource(JPEG, "ticket.jpg"); resolved.put("ref://ticket", image);
        var def = definition(root(IMAGE)); var canonical = def.inputSchema().tree(); var provider = def.inputSchema().providerTree();
        for (Object value : List.of("ref://ticket", image)) {
            var input = new LinkedHashMap<String, Object>(); input.put("file", value);
            var rendered = render(def, input, 1024); var attachment = rendered.attachments().getFirst();
            assertThat(input.get("file")).isSameAs(value); assertThat(attachment.resource()).isSameAs(image);
            assertThat(attachment.fieldPath()).isEqualTo("/file");
            assertThat(attachment.source()).isEqualTo(value instanceof String ? value : "resource");
            assertThat(attachment.sizeBytes()).isEqualTo((long) JPEG.length);
            assertThat(attachment.sha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JPEG)));
            assertThat(rendered.traceSafeInput().get("file")).isEqualTo(attachment.descriptor());
            assertThat(rendered.userText()).contains("\"attachment\" : true").doesNotContain("byteArray", "inputStream", "ByteArrayResource");
        }
        assertThat(def.inputSchema().tree()).isEqualTo(canonical); assertThat(def.inputSchema().providerTree()).isEqualTo(provider);
        assertThat(provider.toString()).doesNotContain("fieldPath", "sizeBytes", "x-loomspan-attachment");
    }

    @Test void nullableItemsTypedMapsAndMissingNamesSurviveImmutableContainers() {
        String nullable = leaf("image", "image/jpeg", true);
        String schema = "{\"type\":\"object\",\"required\":[\"null\"],\"properties\":{\"null\":" + nullable
                + ",\"items\":{\"type\":\"array\",\"items\":" + nullable + "},\"map\":{\"type\":\"object\",\"additionalProperties\":" + nullable + "}}}";
        var input = new LinkedHashMap<String, Object>(); input.put("null", null);
        input.put("items", Arrays.asList(null, resource(JPEG, null))); input.put("map", Collections.singletonMap("missing", null));
        var rendered = render(schema, input); assertThat(rendered.attachments()).hasSize(1);
        var attachment = rendered.attachments().getFirst();
        assertThat(attachment.fieldPath()).isEqualTo("/items/1"); assertThat(attachment.descriptor()).containsEntry("name", null).containsEntry("source", "resource");
        assertThat(rendered.traceSafeInput()).containsEntry("null", null);
        assertThat(((List<?>) rendered.traceSafeInput().get("items")).getFirst()).isNull();
        assertThat((Map<?, ?>) rendered.traceSafeInput().get("map")).containsValue(null);
        assertThatThrownBy(() -> rendered.traceSafeInput().put("new", 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((List<Object>) rendered.traceSafeInput().get("items")).add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> attachment.descriptor().put("name", "invented")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void distinguishesOptionalRequiredPermittedAndProhibitedNulls() {
        String nullable = leaf("pdf", "application/pdf", true);
        assertThat(render("{\"type\":\"object\",\"properties\":{\"file\":" + PDF + "}}", Map.of()).attachments()).isEmpty();
        assertThatThrownBy(() -> render(root(nullable), Map.of())).hasMessageContaining("required");
        assertThat(render(root(nullable), Collections.singletonMap("file", null)).attachments()).isEmpty();
        assertThatThrownBy(() -> render(root(PDF), Collections.singletonMap("file", null))).hasMessageContaining("/file");
        assertThatThrownBy(() -> render(root("{\"type\":\"array\",\"items\":" + PDF + "}"), Map.of("file", Arrays.asList((Object) null)))).hasMessageContaining("/file/0");
        assertThatThrownBy(() -> render(root("{\"type\":\"object\",\"additionalProperties\":" + PDF + "}"), Map.of("file", Collections.singletonMap("x", null)))).hasMessageContaining("/file/x");
    }

    @Test void typedMapsNestedArraysNamedPrecedenceAndPointersPreserveAssociations() {
        Resource image = resource(JPEG, "file.jpg"); resolved.put("ref://file", image);
        String schema = "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"object\",\"properties\":{\"b\":" + IMAGE + "}},"
                + "\"files\":{\"type\":\"array\",\"items\":" + IMAGE + "},\"nested\":{\"type\":\"array\",\"items\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"additionalProperties\":" + IMAGE + "}}},"
                + "\"map\":{\"type\":\"object\",\"properties\":{\"ordinary\":{\"type\":\"integer\"}},\"additionalProperties\":" + IMAGE + "}},\"additionalProperties\":" + IMAGE + "}";
        Map<String, Object> input = new LinkedHashMap<>(); input.put("a.b", image); input.put("a", Map.of("b", "ref://file"));
        input.put("files[0]", image); input.put("files", List.of(image)); input.put("a/b~c", image); input.put("", image);
        input.put("nested", List.of(List.of(Map.of("inside", image)))); input.put("map", Map.of("ordinary", 3, "file", image));
        var result = render(schema, input);
        assertThat(result.attachments().stream().map(LoomspanAttachment::fieldPath)).containsExactly("/a.b", "/a/b", "/files[0]", "/files/0", "/a~1b~0c", "/", "/nested/0/0/inside", "/map/file");
        assertThat(result.attachments()).allSatisfy(a -> assertThat(a.resource()).isSameAs(image));
        assertThat((Map<String, Object>) result.traceSafeInput().get("map")).containsEntry("ordinary", 3);
        assertThat(result.traceSafeInput().keySet()).containsExactlyElementsOf(input.keySet());
        assertThatThrownBy(() -> render(schema, Map.of("map", Map.of("ordinary", image)))).hasMessageContaining("/map/ordinary");
    }

    @Test void parentAndSiblingFailuresOccurBeforeReadingAnyResource() {
        var reads = new AtomicInteger();
        Resource neverRead = new AbstractResource() {
            public String getDescription() { return "never read"; }
            public InputStream getInputStream() { reads.incrementAndGet(); throw new AssertionError("read invalid input"); }
        };
        String schema = "{\"type\":\"object\",\"required\":[\"file\",\"count\"],\"properties\":{\"file\":" + IMAGE + ",\"count\":{\"type\":\"integer\"},"
                + "\"rows\":{\"type\":\"array\",\"minItems\":1,\"maxItems\":2,\"items\":" + IMAGE + "}},\"additionalProperties\":false}";
        List<Map<String, Object>> invalid = List.of(Map.of("file", neverRead), Map.of("file", neverRead, "count", "1"),
                Map.of("file", neverRead, "count", 1, "extra", true), Map.of("file", neverRead, "count", 1, "rows", List.of()),
                Map.of("file", neverRead, "count", 1, "rows", List.of(neverRead, neverRead, neverRead)), Map.of("file", neverRead, "count", 1, "rows", Map.of()));
        for (var input : invalid) assertThatThrownBy(() -> render(schema, input)).isInstanceOf(IllegalArgumentException.class);
        assertThat(reads).hasValue(0);
    }

    @Test void referenceAssertionsApplyToActualTextAndNotResourceNamesOrBytes() {
        Resource image = resource(JPEG, "x.jpg"); resolved.put("ref://accepted", image); resolved.put("ref://other", image);
        String constrained = IMAGE.replace("\"x-loomspan-attachment\"", "\"enum\":[\"ref://accepted\"],\"minLength\":10,\"maxLength\":14,\"format\":\"uri\",\"x-loomspan-attachment\"");
        assertThat(render(root(constrained), Map.of("file", "ref://accepted")).attachments()).hasSize(1);
        assertThat(render(root(constrained), Map.of("file", image)).attachments()).hasSize(1);
        assertThatThrownBy(() -> render(root(constrained), Map.of("file", "ref://other"))).hasMessageContaining("enum");
        assertThatThrownBy(() -> render(root(IMAGE), Map.of("file", "not-ref"))).hasMessageContaining("invalid_attachment");
    }

    @Test void parsedMimeEqualityHandlesFilenameAndSingleAllowedFallbackWithSignatureControls() {
        for (String mime : List.of("image/jpeg", "IMAGE/JPEG", "ImAgE/JpEg")) for (String name : Arrays.asList("image.jpg", "unknown.extension", null)) {
            var result = render(root(leaf("image", mime, false)), Map.of("file", resource(JPEG, name)));
            assertThat(result.attachments()).hasSize(1); assertThat(result.attachments().getFirst().metadata()).containsEntry("allowedContentTypes", List.of(mime));
        }
        assertThatThrownBy(() -> render(root(leaf("image", "image/png", false)), Map.of("file", resource(JPEG, "image.jpg")))).hasMessageContaining("is not allowed");
        assertThatThrownBy(() -> render(root(IMAGE), Map.of("file", resource(new byte[16], "image.jpg")))).hasMessageContaining("content does not match");
    }

    @Test void nonrepeatableMissingAndNonresourceReferencesFailWithFieldContext() {
        assertThatThrownBy(() -> render(root(IMAGE), Map.of("file", new InputStreamResource(new ByteArrayInputStream(JPEG))))).hasMessageContaining("repeatable Resource");
        assertThatThrownBy(() -> render(root(IMAGE), Map.of("file", "ref://missing"))).hasMessageContaining("/file").hasCauseInstanceOf(IllegalArgumentException.class);
        resolved.put("ref://wrong", "text");
        assertThatThrownBy(() -> render(root(IMAGE), Map.of("file", "ref://wrong"))).hasMessageContaining("not a Resource");
    }

    @Test void unmarkedValuesAndBooleanOpennessDoNotSelectAttachments() {
        for (String additional : List.of("true", "{}")) {
            String schema = "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},\"additionalProperties\":" + additional + "}";
            var input = Map.<String, Object>of("text", "ref://literal", "extra", Map.of("x-loomspan-attachment", "ref://literal"));
            var result = render(schema, input);
            assertThat(result.attachments()).isEmpty(); assertThat(result.traceSafeInput()).isEqualTo(input);
            assertThatThrownBy(() -> render(schema, Map.of("extra", resource(JPEG, "x.jpg")))).hasMessageContaining("non_json_value");
        }
    }

    @Test void mimeParameterValuesAndActualEmptyResourceSizeRemainExact() {
        String mime = "application/pdf; profile=MixedCase";
        var result = render(root(leaf("pdf", mime, false)), Map.of("file", resource(new byte[0], null)));
        assertThat(result.attachments().getFirst().contentType()).isEqualTo(mime);
        assertThat(result.attachments().getFirst().sizeBytes()).isZero();
        assertThat(result.attachments().getFirst().metadata()).containsEntry("allowedContentTypes", List.of(mime));
    }

    @Test void openingAndMidReadFailuresRetainOriginalCauseAndCloseStreams() {
        for (boolean opening : List.of(true, false)) {
            IOException cause = new IOException(opening ? "open failed" : "read failed"); var closed = new AtomicInteger();
            Resource unreadable = new AbstractResource() {
                public String getDescription() { return "unreadable"; } public String getFilename() { return "file.pdf"; }
                public InputStream getInputStream() throws IOException {
                    if (opening) throw cause;
                    return new InputStream() { int n; public int read() throws IOException { if (n++ == 0) return 1; throw cause; } public void close() { closed.incrementAndGet(); } };
                }
            };
            resolved.put("ref://unreadable", unreadable);
            for (Object value : List.of(unreadable, "ref://unreadable")) assertThatThrownBy(() -> render(root(PDF), Map.of("file", value))).hasMessageContaining("/file").hasCause(cause);
            assertThat(closed).hasValue(opening ? 0 : 2);
        }
    }

    @Test void digestReadIsBoundedWithoutContentLengthAndReportsActualCountAndHash() throws Exception {
        for (boolean unknown : List.of(true, false)) {
            var count = new AtomicInteger(); var closed = new AtomicInteger();
            Resource huge = counting(new byte[65536], count, closed, unknown);
            assertThatThrownBy(() -> render(definition(root(PDF)), Map.of("file", huge), 16)).hasMessageContaining("exceeding configured limit");
            assertThat(count).hasValue(17); assertThat(closed).hasValue(1);
        }
        for (int size : List.of(0, 15, 16)) {
            byte[] bytes = new byte[size]; Arrays.fill(bytes, (byte) 7); var count = new AtomicInteger(); var closed = new AtomicInteger();
            var result = render(definition(root(PDF)), Map.of("file", counting(bytes, count, closed, true)), 16).attachments().getFirst();
            assertThat(result.sizeBytes()).isEqualTo((long) size); assertThat(result.sha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            assertThat(count).hasValue(size); assertThat(closed).hasValue(1);
        }
    }
    private static Resource counting(byte[] bytes, AtomicInteger consumed, AtomicInteger closed, boolean unknown) {
        return new AbstractResource() {
            public String getDescription() { return "counted PDF"; } public String getFilename() { return "file.pdf"; }
            public long contentLength() throws IOException { if (unknown) return -1; return super.contentLength(); }
            public InputStream getInputStream() { return new ByteArrayInputStream(bytes) {
                public synchronized int read(byte[] b, int off, int len) { int n = super.read(b, off, len); if (n > 0) consumed.addAndGet(n); return n; }
                public void close() { closed.incrementAndGet(); }
            }; }
        };
    }
}
