package ai.loomspan.internal.runtime.attachment;

import java.util.Locale;

enum AttachmentMediaType
{
    IMAGE,
    PDF,
    AUDIO,
    VIDEO,
    FILE;

    public static AttachmentMediaType fromManifest(String value)
    {
        if (value == null || value.isBlank())
        {
            return FILE;
        }
        return AttachmentMediaType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
