package ai.loomspan.internal.observability.web;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservabilityArtifactStreamTest
{
    @Test
    void streamsExactBytesInBoundedChunksAndRejectsShortRead() throws Exception
    {
        byte[] bytes = new byte[ObservabilityArtifactStream.COPY_BUFFER_BYTES * 3 + 7];
        java.util.Arrays.fill(bytes, (byte) 0x5a);
        CapturingOutput output = new CapturingOutput();

        ObservabilityArtifactStream.copyExactly(new ByteArrayInputStream(bytes), output, bytes.length);

        assertThat(output.bytes()).isEqualTo(bytes);
        assertThatThrownBy(() -> ObservabilityArtifactStream.copyExactly(
                new ByteArrayInputStream(new byte[3]), new CapturingOutput(), 4))
                .isInstanceOf(EOFException.class);
    }

    @Test
    void fixedTransferTimeoutIsFiveMinutes()
    {
        assertThat(ObservabilityDeliveryLimits.ARTIFACT_DOWNLOAD_TIMEOUT)
                .isEqualTo(java.time.Duration.ofMinutes(5));
    }

    private static final class CapturingOutput extends ServletOutputStream
    {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        @Override public boolean isReady() { return true; }
        @Override public void setWriteListener(WriteListener writeListener) {}
        @Override public void write(int value) { output.write(value); }
        @Override public void write(byte[] bytes, int offset, int length) { output.write(bytes, offset, length); }
        byte[] bytes() { return output.toByteArray(); }
    }
}
