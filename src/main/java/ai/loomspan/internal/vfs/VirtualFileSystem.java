package ai.loomspan.internal.vfs;

import ai.loomspan.internal.core.LoomspanSession;
import org.springframework.core.io.Resource;

public interface VirtualFileSystem
{
    default Resource resolve(LoomspanSession session, String ref)
    {
        return resolve(session, VfsRef.parse(ref));
    }

    Resource resolve(LoomspanSession session, VfsRef ref);
}
