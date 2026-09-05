package ai.loomspan.internal.runtime.planning;

import ai.loomspan.internal.runtime.evidence.EvidenceCoverageValidator;
import ai.loomspan.internal.runtime.state.ExecutionStateService;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;

import java.util.function.Supplier;

/** Test-only access to the internal deterministic plan-ID construction path. */
public final class PlanningServiceTestFactory {

    private PlanningServiceTestFactory() {
    }

    public static DefaultPlanningService withPlanIds(
            ExecutionStateService stateService,
            Supplier<String> planIdSupplier) {
        LoomspanJacksonCodecs codecs = LoomspanJacksonCodecs.defaults();
        return new DefaultPlanningService(stateService,
                codecs.planningJson(),
                codecs.planningYaml(),
                new PlanTaskConstraintValidator(),
                new EvidenceCoverageValidator(),
                planIdSupplier);
    }
}
