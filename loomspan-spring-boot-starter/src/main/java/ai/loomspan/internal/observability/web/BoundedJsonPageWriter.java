package ai.loomspan.internal.observability.web;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class BoundedJsonPageWriter
{
    public static final int DEFAULT_PAGE_SIZE = 1000;
    public static final int MAX_PAGE_SIZE = 5000;
    public static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;
    private final ObservabilityJsonCodec json;

    public BoundedJsonPageWriter(ObservabilityJsonCodec json)
    {
        this.json = json;
    }

    public int pageSize(String value)
    {
        if (value == null)
        {
            return DEFAULT_PAGE_SIZE;
        }
        try
        {
            int parsed = Integer.parseInt(value);
            if (parsed < 1 || parsed > MAX_PAGE_SIZE) throw invalid();
            return parsed;
        }
        catch (NumberFormatException ex)
        {
            throw invalid();
        }
    }

    public <T> byte[] write(
            List<T> fetched,
            int requestedSize,
            Function<List<T>, Object> envelopeFactory)
    {
        int available = Math.min(fetched.size(), requestedSize);
        if (available == 0)
        {
            byte[] empty = serializePage(fetched, 0, envelopeFactory);
            if (empty.length > MAX_RESPONSE_BYTES) throw limitExceeded();
            return empty;
        }
        byte[] best = serializePage(fetched, 1, envelopeFactory);
        if (best.length > MAX_RESPONSE_BYTES) throw limitExceeded();
        int bestCount = 1;

        while (bestCount < available)
        {
            int candidate = Math.min(available, Math.multiplyExact(bestCount, 2));
            byte[] candidateBytes = serializePage(fetched, candidate, envelopeFactory);
            if (candidateBytes.length <= MAX_RESPONSE_BYTES)
            {
                best = candidateBytes;
                bestCount = candidate;
                continue;
            }
            int low = bestCount + 1;
            int high = candidate - 1;
            while (low <= high)
            {
                int middle = low + ((high - low) / 2);
                byte[] middleBytes = serializePage(fetched, middle, envelopeFactory);
                if (middleBytes.length <= MAX_RESPONSE_BYTES)
                {
                    best = middleBytes;
                    bestCount = middle;
                    low = middle + 1;
                }
                else
                {
                    high = middle - 1;
                }
            }
            break;
        }
        return best;
    }

    public byte[] writeObject(Object value)
    {
        try
        {
            return json.write(value);
        }
        catch (Exception ex)
        {
            throw new ObservabilityException(
                    500, ObservabilityProblem.Code.APPLICATION_ERROR,
                    "The observability response could not be serialized");
        }
    }

    private static ObservabilityException invalid()
    {
        return new ObservabilityException(
                400, ObservabilityProblem.Code.INVALID_REQUEST, "pageSize must be an integer from 1 through 5000");
    }

    private <T> byte[] serializePage(
            List<T> fetched,
            int count,
            Function<List<T>, Object> envelopeFactory)
    {
        try
        {
            List<T> emitted = new ArrayList<>(fetched.subList(0, count));
            return json.write(envelopeFactory.apply(List.copyOf(emitted)));
        }
        catch (Exception ex)
        {
            throw new ObservabilityException(
                    500, ObservabilityProblem.Code.APPLICATION_ERROR,
                    "The observability response could not be serialized");
        }
    }

    private static ObservabilityException limitExceeded()
    {
        return new ObservabilityException(
                429, ObservabilityProblem.Code.LIMIT_EXCEEDED,
                "The observability response exceeds the configured limit");
    }
}
