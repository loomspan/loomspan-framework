package ai.loomspan.internal.observability.web;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public final class ObservabilityCursorCodec
{
    private static final int MAX_ENCODED_LENGTH = 4096;
    private final ObservabilityJsonCodec json;

    public ObservabilityCursorCodec(ObservabilityJsonCodec json)
    {
        this.json = json;
    }

    public String encode(Cursor cursor)
    {
        try
        {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.write(cursor));
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("Cursor could not be encoded", ex);
        }
    }

    public Cursor decode(String encoded, UUID instanceId, String collection)
    {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_ENCODED_LENGTH
                || encoded.indexOf('=') >= 0)
        {
            throw invalid();
        }
        final Cursor cursor;
        try
        {
            byte[] decoded = Base64.getUrlDecoder().decode(encoded.getBytes(StandardCharsets.US_ASCII));
            cursor = json.read(decoded, Cursor.class);
        }
        catch (Exception ex)
        {
            throw invalid();
        }
        if (cursor.version() != 1 || cursor.instanceId() == null || cursor.collection() == null
                || cursor.order() == null || cursor.filter() == null
                || cursor.highWater() < 0 || cursor.beforeOrdinal() < 0)
        {
            throw invalid();
        }
        if (!collection.equals(cursor.collection()) || !"keyset".equals(cursor.order())
                || !"none".equals(cursor.filter()))
        {
            throw invalid();
        }
        if ("skills".equals(collection))
        {
            if (cursor.highWater() != 0 || cursor.beforeOrdinal() != 0) throw invalid();
        }
        else if (cursor.afterName() != null || cursor.beforeOrdinal() > cursor.highWater())
        {
            throw invalid();
        }
        if (!cursor.instanceId().equals(instanceId.toString()))
        {
            throw new ObservabilityException(
                    410, ObservabilityProblem.Code.STALE_CURSOR, "The continuation belongs to another application instance");
        }
        return cursor;
    }

    private static ObservabilityException invalid()
    {
        return new ObservabilityException(400, ObservabilityProblem.Code.INVALID_CURSOR, "The continuation is invalid");
    }

    public record Cursor(
            int version,
            String instanceId,
            String collection,
            String order,
            String filter,
            long highWater,
            long beforeOrdinal,
            String afterName)
    {
        public static Cursor initial(UUID instanceId, String collection, long highWater)
        {
            return new Cursor(1, instanceId.toString(), collection, "keyset", "none", highWater, 0, null);
        }

        public Cursor before(long ordinal)
        {
            return new Cursor(version, instanceId, collection, order, filter, highWater, ordinal, null);
        }

        public Cursor after(String name)
        {
            return new Cursor(version, instanceId, collection, order, filter, highWater, 0, name);
        }
    }
}
