package ai.loomspan.internal.observability.web;

public record ObservabilityProblem(int status, Code code, String message)
{
    public enum Code
    {
        LOOMSPAN_API_KEY_REJECTED,
        INVALID_REQUEST,
        INVALID_CURSOR,
        STALE_CURSOR,
        NOT_FOUND,
        LIVE_MONITORING_UNAVAILABLE,
        LIMIT_EXCEEDED,
        APPLICATION_ERROR
    }
}
