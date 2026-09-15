package ai.loomspan.internal.observability.web;

public final class ObservabilityProblemMapper
{
    public ObservabilityProblem map(Throwable failure)
    {
        Throwable current = failure;
        while (current != null)
        {
            if (current instanceof ObservabilityException observability)
            {
                return observability.problem();
            }
            current = current.getCause();
        }
        return new ObservabilityProblem(
                500, ObservabilityProblem.Code.APPLICATION_ERROR, "The observability request could not be completed");
    }
}
