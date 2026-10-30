package ai.loomspan.internal.runtime.step;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class StepActionSelectionTest {
    @Test void commonParserNormalizesOnlyNamespaceAndEnforcesLimits() {
        var arguments = new LinkedHashMap<String,Object>(); arguments.put("value",null);
        var raw = StepAction.callTool("one","Tax-calculateTax",arguments);
        var selected = raw.canonicalSelection();
        assertThat(raw.toolName()).isEqualTo("Tax-calculateTax");
        assertThat(selected.toolName()).isEqualTo("tax-calculateTax");
        assertThat(selected.toolArguments()).isSameAs(arguments);
        assertThat(StepAction.callTool("one","tax-CalculateTax",arguments).canonicalSelection().toolName()).isEqualTo("tax-CalculateTax");
        for (String invalid : List.of("calculateTax","tax-a-b","tax/a"," tax-calculateTax","n".repeat(33)+"-x","tax-"+"x".repeat(61)))
            assertThatThrownBy(() -> StepAction.callTool("one",invalid,arguments).canonicalSelection()).isInstanceOf(IllegalArgumentException.class);
        for (String boundary : List.of("a_b-c_d","n".repeat(32)+"-"+"x".repeat(31)))
            assertThat(StepAction.callTool("one",boundary,arguments).canonicalSelection().toolName()).isEqualTo(boundary);
    }
}
