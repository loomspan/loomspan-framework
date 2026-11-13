package ai.loomspan.internal.serialization;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class YamlValuesTest {
    @Test void ordinaryConfigurationDecodingRetainsNumericKinds() {
        String yaml = "decimal: 1.25\ninteger: 3\nlarge: 123456789012345678901234567890\nnonfinite: .nan\ndate: !!timestamp 2026-11-13";
        for (Object value : new Object[] { YamlValues.read(yaml), YamlValues.read(stream(yaml)),
                YamlValues.documents(stream(yaml)).iterator().next() }) {
            Map<?, ?> map = (Map<?, ?>) value;
            assertInstanceOf(Double.class, map.get("decimal"));
            assertInstanceOf(Integer.class, map.get("integer"));
            assertInstanceOf(java.math.BigInteger.class, map.get("large"));
            assertTrue(Double.isNaN((Double) map.get("nonfinite")));
            assertEquals("2026-11-13", map.get("date"));
        }
    }

    private ByteArrayInputStream stream(String yaml) { return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)); }
}
