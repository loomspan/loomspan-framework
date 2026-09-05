package ai.loomspan.internal.vfs;

import ai.loomspan.internal.core.LoomspanSession;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.nio.file.Path;
import java.util.Objects;

public class SessionLocalVirtualFileSystem implements VirtualFileSystem
{
    private final Path rootDirectory;

    public SessionLocalVirtualFileSystem(Path rootDirectory)
    {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory must not be null").toAbsolutePath().normalize();
    }

    @Override
    public Resource resolve(LoomspanSession session, VfsRef ref)
    {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(ref, "ref must not be null");
        Path sessionRoot = sessionRoot(session);
        Path resolved = sessionRoot.resolve(ref.relativePath()).normalize();
 
        if (!resolved.startsWith(sessionRoot))
        {
            throw new IllegalArgumentException("Ref '" + ref.raw() + "' escapes the session namespace");
        }
 
        Resource resource = new FileSystemResource(resolved);
        if (!resource.exists())
        {
            throw new IllegalArgumentException("Unknown ref '" + ref.raw() + "' for session '" + session.getSessionId() + "'");
        }
 
        return resource;
    }

    public Path sessionRoot(LoomspanSession session)
    {
        Objects.requireNonNull(session, "session must not be null");
        Path sessionRoot = rootDirectory.resolve(session.getSessionId()).normalize();
 
        if (!sessionRoot.startsWith(rootDirectory))
        {
            throw new IllegalArgumentException("Session '" + session.getSessionId() + "' escapes the VFS root");
        }
 
        return sessionRoot;
    }
}
