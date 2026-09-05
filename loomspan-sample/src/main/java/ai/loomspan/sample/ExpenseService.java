package ai.loomspan.sample;

import org.springframework.stereotype.Service;
import ai.loomspan.api.SkillMethod;

import java.util.List;
import java.util.Map;

@Service
public class ExpenseService {

    @jakarta.annotation.security.PermitAll
    @SkillMethod(name = "expenseLookup", description = "Returns a fake list of recent expenses.")
    public List<Map<String, Object>> getLatestExpenses() {
        return List.of(
            Map.of("category", "Software", "amount", 120.00, "date", "2026-03-20"),
            Map.of("category", "Hardware", "amount", 1450.00, "date", "2026-03-21")
        );
    }
}
