package ai.loomspan.internal.runtime.trace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class TraceFilePublication
{
    private TraceFilePublication() {}

    static void publish(Path activePath, Path completedPath) throws IOException
    {
        Files.move(activePath, completedPath, StandardCopyOption.ATOMIC_MOVE);
    }
}
