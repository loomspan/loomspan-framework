package ai.loomspan.internal.observability.web;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservabilityCursorCodecTest
{
    private final ObservabilityCursorCodec codec = new ObservabilityCursorCodec(new ObservabilityJsonCodec());
    private final UUID instance = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Test
    void roundTripsVersionOneCursorAndAllowsPageSizeChange()
    {
        var cursor = ObservabilityCursorCodec.Cursor.initial(instance, "traces", 42).before(21);
        assertThat(codec.decode(codec.encode(cursor), instance, "traces")).isEqualTo(cursor);
    }

    @Test
    void distinguishesMalformedWrongScopeAndChangedInstance()
    {
        assertThatThrownBy(() -> codec.decode("not+base64", instance, "traces"))
                .isInstanceOf(ObservabilityException.class)
                .extracting(failure -> ((ObservabilityException) failure).problem().code())
                .isEqualTo(ObservabilityProblem.Code.INVALID_CURSOR);
        String encoded = codec.encode(ObservabilityCursorCodec.Cursor.initial(instance, "skills", 0));
        assertThatThrownBy(() -> codec.decode(encoded, instance, "traces"))
                .isInstanceOf(ObservabilityException.class);
        assertThatThrownBy(() -> codec.decode(encoded, UUID.randomUUID(), "skills"))
                .isInstanceOf(ObservabilityException.class)
                .extracting(failure -> ((ObservabilityException) failure).problem().code())
                .isEqualTo(ObservabilityProblem.Code.STALE_CURSOR);
    }
}
