package ai.loomspan.internal.runtime.step;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class StepActionSelectionTest {
    @Test void validatesExactSelectionWithoutReplacingActionOrArguments() {
        var arguments = new LinkedHashMap<String,Object>(); arguments.put("value",null);
        var raw = StepAction.callTool("one", "order-support:find-order", arguments);
        raw.validateSelection();
        assertThat(raw.toolName()).isEqualTo("order-support:find-order");
        assertThat(raw.toolArguments()).isSameAs(arguments);
        for (String invalid : List.of("find-order", "order-support--find-order", "Order-support:find-order",
                "orders:find_order", "orders:lookup ", "orders/lookup", "order-support:" + "a".repeat(50)))
            assertThatThrownBy(() -> StepAction.callTool("one", invalid, arguments).validateSelection())
                    .isInstanceOf(IllegalArgumentException.class);
        for (String boundary : List.of("order-support:find-order", "order-support:" + "a".repeat(49)))
            assertThatCode(() -> StepAction.callTool("one", boundary, arguments).validateSelection()).doesNotThrowAnyException();
        assertThatCode(() -> StepAction.cannotComplete("exact reason").validateSelection()).doesNotThrowAnyException();
    }
}
