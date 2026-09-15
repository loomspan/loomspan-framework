package ai.loomspan.internal.runtime.observation.catalog;

import ai.loomspan.internal.skill.YamlSkillSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class SkillSourcePathResolver
{
    private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");
    private static final Pattern DRIVE = Pattern.compile("^[A-Za-z]:");

    private final ResourcePatternResolver resolver;

    public SkillSourcePathResolver()
    {
        this(new PathMatchingResourcePatternResolver());
    }

    public SkillSourcePathResolver(ResourcePatternResolver resolver)
    {
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
    }

    public String resolve(YamlSkillSource source)
    {
        Objects.requireNonNull(source, "source must not be null");
        String location = source.locationPattern();
        int patternIndex = firstPatternIndex(location);
        if (patternIndex < 0)
        {
            return validate(source.resource().getFilename());
        }

        int separator = Math.max(location.lastIndexOf('/', patternIndex), location.lastIndexOf('\\', patternIndex));
        String rootPattern = separator < 0 ? location.substring(0, patternIndex) : location.substring(0, separator + 1);
        try
        {
            URI resourceUri = source.resource().getURI().normalize();
            List<URI> roots = new ArrayList<>();
            for (Resource root : resolver.getResources(rootPattern))
            {
                if (root.exists())
                {
                    roots.add(directoryUri(root.getURI().normalize()));
                }
            }
            return roots.stream()
                    .filter(root -> isBelow(root, resourceUri))
                    .max(Comparator.comparingInt(uri -> uri.toString().length()))
                    .map(root -> validate(relativize(root, resourceUri)))
                    .orElseThrow(() -> unsafe(source, "matched resource cannot be relativized against its configured root"));
        }
        catch (IOException ex)
        {
            throw unsafe(source, "configured root cannot be resolved", ex);
        }
    }

    private static int firstPatternIndex(String value)
    {
        int result = -1;
        for (char marker : new char[] {'*', '?', '{'})
        {
            int found = value.indexOf(marker);
            if (found >= 0 && (result < 0 || found < result))
            {
                result = found;
            }
        }
        return result;
    }

    private static URI directoryUri(URI uri)
    {
        String value = uri.toString();
        return URI.create(value.endsWith("/") ? value : value + "/");
    }

    private static boolean isBelow(URI root, URI resource)
    {
        return resource.toString().startsWith(root.toString());
    }

    private static String relativize(URI root, URI resource)
    {
        return resource.toString().substring(root.toString().length());
    }

    private static String validate(String candidate)
    {
        if (candidate == null)
        {
            throw new IllegalStateException("Skill source path is unavailable");
        }
        String normalized = candidate.replace('\\', '/');
        if (normalized.isBlank()
                || normalized.startsWith("/")
                || SCHEME.matcher(normalized).find()
                || DRIVE.matcher(normalized).find())
        {
            throw new IllegalStateException("Skill source path is unsafe");
        }
        String[] segments = normalized.split("/", -1);
        for (String segment : segments)
        {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment))
            {
                throw new IllegalStateException("Skill source path contains an unsafe segment");
            }
        }
        return String.join("/", segments);
    }

    private static IllegalStateException unsafe(YamlSkillSource source, String detail)
    {
        return unsafe(source, detail, null);
    }

    private static IllegalStateException unsafe(YamlSkillSource source, String detail, Throwable cause)
    {
        return new IllegalStateException(
                "Cannot derive a safe descriptive source path for " + source.resource().getDescription() + ": " + detail,
                cause);
    }
}
