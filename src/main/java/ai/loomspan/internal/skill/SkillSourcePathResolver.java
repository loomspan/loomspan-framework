package ai.loomspan.internal.skill;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
        if (source.suppliedLabel() != null)
        {
            return source.suppliedLabel();
        }
        return resolve(source.locationPattern(), source.resource());
    }

    public void validateLocation(String location)
    {
        int patternIndex = firstPatternIndex(location);
        int separator = patternIndex < 0
                ? Math.max(location.lastIndexOf('/'), location.lastIndexOf('\\'))
                : Math.max(location.lastIndexOf('/', patternIndex), location.lastIndexOf('\\', patternIndex));
        int relativeStart = Math.max(separator + 1, location.indexOf(':') + 1);
        String relativePath = location.substring(relativeStart);
        boolean encodedLocation = SCHEME.matcher(location).find()
                && !DRIVE.matcher(location).find() && !location.startsWith("classpath:");
        requireLowercase(validate(encodedLocation ? decode(relativePath) : relativePath));
    }

    public void validateResource(String location, Resource resource)
    {
        requireLowercase(resolve(location, resource));
    }

    private String resolve(String location, Resource resource)
    {
        int patternIndex = firstPatternIndex(location);
        if (patternIndex < 0)
        {
            try
            {
                URI uri = actualUri(resource);
                String path = decode(uri.toString());
                return validate(path.substring(path.lastIndexOf('/') + 1));
            }
            catch (IOException ex)
            {
                throw unsafe(resource, "resource filename cannot be resolved", ex);
            }
        }

        int separator = Math.max(location.lastIndexOf('/', patternIndex), location.lastIndexOf('\\', patternIndex));
        String rootPattern = separator < 0 ? location.substring(0, patternIndex) : location.substring(0, separator + 1);
        try
        {
            URI resourceUri = actualUri(resource);
            List<URI> roots = new ArrayList<>();
            for (Resource root : resolver.getResources(rootPattern))
            {
                if (root.exists())
                {
                    roots.add(directoryUri(actualUri(root)));
                }
            }
            return roots.stream()
                    .filter(root -> isBelow(root, resourceUri))
                    .max(Comparator.comparingInt(uri -> uri.toString().length()))
                    .map(root -> validate(relativize(root, resourceUri)))
                    .orElseThrow(() -> unsafe(resource, "matched resource cannot be relativized against its configured root", null));
        }
        catch (IOException ex)
        {
            throw unsafe(resource, "configured root cannot be resolved", ex);
        }
    }

    private static int firstPatternIndex(String value)
    {
        // Resource prefixes such as classpath*: are not part of the path pattern.
        int pathStart = value.indexOf(':') + 1;
        int result = -1;
        for (char marker : new char[] {'*', '?', '{'})
        {
            int found = value.indexOf(marker, pathStart);
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
        return decode(resource.toString().substring(root.toString().length()));
    }

    private static URI actualUri(Resource resource) throws IOException
    {
        URI uri = resource.getURI().normalize();
        if ("file".equals(uri.getScheme()))
        {
            return Path.of(uri).toRealPath(LinkOption.NOFOLLOW_LINKS).toUri();
        }
        return uri;
    }

    private static String decode(String path)
    {
        return org.springframework.util.StringUtils.uriDecode(path, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void requireLowercase(String relativePath)
    {
        String[] components = relativePath.split("/");
        for (int index = 0; index < components.length; index++)
        {
            String component = components[index];
            if (!component.equals(component.toLowerCase(Locale.ROOT)))
            {
                String kind = index == components.length - 1 ? "filename" : "directory";
                throw new IllegalStateException("Skill definition relative path '" + relativePath
                        + "' contains uppercase " + kind + " component '" + component + "'");
            }
        }
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

    private static IllegalStateException unsafe(Resource resource, String detail, Throwable cause)
    {
        return new IllegalStateException(
                "Cannot derive a safe descriptive source path for " + resource.getDescription() + ": " + detail,
                cause);
    }
}
