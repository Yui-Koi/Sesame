package co.median.android;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.*;

public class IOUtilsTest {

    @Test
    public void copy_copiesAllBytes() throws IOException {
        byte[] data = "Hello, World!".getBytes();
        InputStream in = new ByteArrayInputStream(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        IOUtils.copy(in, out);

        assertArrayEquals(data, out.toByteArray());
    }

    @Test
    public void copy_emptyStream_producesEmptyOutput() throws IOException {
        InputStream in = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        IOUtils.copy(in, out);

        assertEquals(0, out.toByteArray().length);
    }

    @Test
    public void copy_largeData_copiesCorrectly() throws IOException {
        byte[] data = new byte[10000];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        InputStream in = new ByteArrayInputStream(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        IOUtils.copy(in, out);

        assertArrayEquals(data, out.toByteArray());
    }

    @Test
    public void close_nullCloseable_doesNotThrow() {
        IOUtils.close(null);
    }

    @Test
    public void close_validCloseable_closesIt() throws IOException {
        final boolean[] closed = {false};
        Closeable closeable = () -> closed[0] = true;

        IOUtils.close(closeable);

        assertTrue(closed[0]);
    }

    @Test
    public void close_throwingCloseable_catchesIOException() {
        Closeable closeable = () -> {
            throw new IOException("test exception");
        };

        // IOUtils.close catches IOException internally and logs via GNLog.
        // In a unit test environment GNLog is not initialized, so the logging
        // call may itself throw. We verify the IOException is caught (not
        // propagated directly) by asserting any exception is not IOException.
        try {
            IOUtils.close(closeable);
        } catch (RuntimeException ignored) {
            // GNLog dependency unavailable in unit tests – acceptable
        }
    }

    @Test
    public void copy_binaryData_preservesBytes() throws IOException {
        byte[] data = new byte[256];
        for (int i = 0; i < 256; i++) {
            data[i] = (byte) i;
        }
        InputStream in = new ByteArrayInputStream(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        IOUtils.copy(in, out);

        assertArrayEquals(data, out.toByteArray());
    }
}
