package ai.loomspan.internal.runtime.attachment;

import org.springframework.core.io.ByteArrayResource;
import java.util.Map;

/** Local media input for ordinary native request serialization checks. */
public final class OrdinaryAttachmentFixture {
    private OrdinaryAttachmentFixture() { }
    public static LoomspanAttachment image(byte[] bytes) {
        return new LoomspanAttachment("/image", "fixture.png", "image/png", AttachmentMediaType.IMAGE,
                "resource", (long) bytes.length, null, Map.of(), new ByteArrayResource(bytes));
    }
}
