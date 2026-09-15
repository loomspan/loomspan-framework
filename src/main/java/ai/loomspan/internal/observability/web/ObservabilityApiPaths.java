package ai.loomspan.internal.observability.web;

public final class ObservabilityApiPaths
{
    public static final String ROOT = "/_loomspan/observability/v1";
    public static final String INSTANCE = ROOT + "/instance";
    public static final String SKILLS = ROOT + "/skills";
    public static final String ACTIVE = ROOT + "/active-executions";
    public static final String ACTIVITY = ROOT + "/activity";
    public static final String TRACES = ROOT + "/traces";
    public static final String TRACE_ARTIFACT = TRACES + "/{traceId}/artifact";

    private ObservabilityApiPaths() {}
}
