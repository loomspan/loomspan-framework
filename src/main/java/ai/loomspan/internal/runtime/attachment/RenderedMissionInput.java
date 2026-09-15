package ai.loomspan.internal.runtime.attachment;

import java.util.List;
import java.util.Map;

public record RenderedMissionInput(
        String userText,
        List<LoomspanAttachment> attachments,
        Map<String, Object> traceSafeInput)
{
    public RenderedMissionInput
    {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        traceSafeInput = traceSafeInput == null ? Map.of() : Map.copyOf(traceSafeInput);
    }
}
