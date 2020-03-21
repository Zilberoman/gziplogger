package org.zilbrom.logging.streams;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An OutputStream that counts the number of bytes written.
 */
public class RollingCountingOutputStream  extends FilterOutputStream {
    private long currentStreamSize;
    private long lastRecordSize;

    /**
     * Wraps another output stream, counting the number of bytes written.
     * @param out the output stream to be wrapped
     */
    public RollingCountingOutputStream(OutputStream out) {
        super(checkNotNull(out));
    }

    /**
     * Returns the number of bytes written
     */
    public long getCurrentStreamSize() {
        return currentStreamSize;
    }

    /**
     * Returns the number of bytes of the last record.
     */
    public long getLastRecordSize() {
        return lastRecordSize;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        lastRecordSize = len;
        currentStreamSize += len;
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        lastRecordSize = 1;
        currentStreamSize++;
    }

    // Overriding close() because FilterOutputStream's close() method pre-JDK8 has had behavior:
    // It silently ignores any exception thrown by flush(). Instead, just close the delegate stream.
    // It should flush itself if necessary.
    @Override
    public void close() throws IOException {
        out.close();
    }
}
