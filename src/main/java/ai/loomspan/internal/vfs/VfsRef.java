package ai.loomspan.internal.vfs;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

record VfsRef(String raw, String relativePath)
{
    private static final String SCHEME = "ref://";

    public VfsRef
    {
        Objects.requireNonNull(raw, "raw must not be null");
        Objects.requireNonNull(relativePath, "relativePath must not be null");
    }

    public static VfsRef parse(String raw)
    {
        Objects.requireNonNull(raw, "ref must not be null");
        if (!raw.startsWith(SCHEME))
        {
            throw new IllegalArgumentException("Malformed ref '" + raw + "': ref must use the 'ref://' scheme");
        }

        String relativePath = canonicalize(raw.substring(SCHEME.length()));
        if (relativePath.isBlank())
        {
            throw new IllegalArgumentException("Malformed ref '" + raw + "': ref path must not be empty");
        }

        return new VfsRef(raw, relativePath);
    }

    private static String canonicalize(String path)
    {
        String normalizedSeparators = path.replace('\\', '/');
        Deque<String> segments = new ArrayDeque<>();
 
        for (String segment : normalizedSeparators.split("/+"))
        {
            if (segment.isEmpty() || ".".equals(segment))
            {
                continue;
            }
            if ("..".equals(segment) && !segments.isEmpty() && !"..".equals(segments.peekLast()))
            {
                segments.removeLast();
                continue;
            }
            segments.addLast(segment);
        }
 
        return String.join("/", segments);
    }
}
