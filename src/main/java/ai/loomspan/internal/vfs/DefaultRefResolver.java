package ai.loomspan.internal.vfs;

import ai.loomspan.internal.core.LoomspanSession;
import java.util.Objects;
import java.util.regex.Pattern;

public class DefaultRefResolver implements RefResolver
{
    private static final Pattern STRICT_REF_PATTERN = Pattern.compile("^ref://\\S+$");

    private final VirtualFileSystem virtualFileSystem;

    public DefaultRefResolver(VirtualFileSystem virtualFileSystem)
    {
        this.virtualFileSystem = Objects.requireNonNull(virtualFileSystem, "virtualFileSystem must not be null");
    }

    @Override
    public Object resolveArgument(Object value, LoomspanSession session)
    {
        Objects.requireNonNull(session, "session must not be null");
        if (!(value instanceof String text) || !STRICT_REF_PATTERN.matcher(text).matches())
        {
            return value;
        }
        return virtualFileSystem.resolve(session, text);
    }
}
