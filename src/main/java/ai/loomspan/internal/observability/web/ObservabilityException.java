package ai.loomspan.internal.observability.web;

public final class ObservabilityException extends RuntimeException
{
    private final ObservabilityProblem problem;

    public ObservabilityException(int status, ObservabilityProblem.Code code, String safeMessage)
    {
        super(safeMessage);
        problem = new ObservabilityProblem(status, code, safeMessage);
    }

    public ObservabilityProblem problem()
    {
        return problem;
    }
}
