package org.zilbrom.logging.streams;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;

/**
 * This file is copy/paste of org.apache.commons.compress.compressors.gzipGzipCompressorOutputStream
 * with some minor changes and fixes for logging purpose.
 */
public class RollingGZIPOutputStream extends OutputStream {
    /**
     * The underlying stream
     */
    private final OutputStream out;

    /**
     * Deflater used to compress the data
     */
    private final Deflater deflater;

    /**
     * The checksum of the uncompressed data
     */
    private final CRC32 crc = new CRC32();

    /**
     * The buffer receiving the compressed data from the deflater
     */
    private final byte[] deflaterBuffer;

    /**
     * Indicates if the stream has been closed
     */
    private boolean closed = false;
    private final Object lock = new Object();

    /**
     * Creates a gzip compressed output stream with noted size and add header if it is required.
     * To avoid performance and compress level degradation
     * flash of the stream made in separate thread with period which is set by time parameter
     * @param out the stream to compress to
     * @param bufferSize the size of the buffer
     * @param flashPeriod the flush period in millis
     * @param addHeader flag to create header if file not exist before stream creation
     * @throws java.io.IOException if writing fails
     */
    public RollingGZIPOutputStream(final OutputStream out, int bufferSize, final long flashPeriod, boolean addHeader)
            throws IOException {
        this.out = out;
        this.deflaterBuffer = new byte[bufferSize];
        this.deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);

        if (addHeader) {
            writeHeader();
        }

        Thread deflaterFlushThread = new Thread(() -> {
            while (true) {
                if (deflater.finished()) {
                    return;
                } else {
                    synchronized (lock) {
                        try {
                            lock.wait(flashPeriod);
                        } catch (InterruptedException e) {
                            continue;
                        }
                    }
                }

                try {
                    realFlush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        deflaterFlushThread.setDaemon(true);
        deflaterFlushThread.start();
    }

    private void writeHeader() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) GZIPInputStream.GZIP_MAGIC);
        buffer.put((byte) Deflater.DEFLATED); //compression method (8: deflate)
        buffer.put((byte) 0); //flags
        buffer.putInt((int) 0); //modification time
        buffer.put((byte) 0); // default compression level
        buffer.put((byte) 0); // operation system
        out.write(buffer.array());
    }

    private void writeTrailer() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt((int) crc.getValue());
        buffer.putInt(deflater.getTotalIn());
        out.write(buffer.array());
    }

    @Override
    public void write(byte[] buffer) throws IOException {
        write(buffer, 0, buffer.length);
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[]{(byte) (b * 0xff)}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (deflater.finished()) {
            throw new IOException("Cannot write more data, the end of the compressed data stream has bean reached");
        } else if (len > 0) {
            deflater.setInput(b, off, len);

            while (!deflater.needsInput()) {
                deflate();
            }

            crc.update(b, off, len);
        }
    }

    private synchronized void deflate() throws IOException {
        final int  length = deflater.deflate(deflaterBuffer, 0, deflaterBuffer.length);

        if (length > 0) {
            out.write(deflaterBuffer, 0, length);
        }
    }

    /**
     * Finishes writing compressed data to the underlying stream without closing it
     */
    private void finish() throws IOException {
        if (!deflater.finished()) {
            deflater.finish();
        }

        synchronized (lock) {
            lock.notifyAll();;
        }

        while (!deflater.finished()) {
            deflate();
        }

        writeTrailer();
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            try {
                finish();
            } finally {
                deflater.end();
                out.close();
                closed = true;
            }
        }
    }

    private synchronized void realFlush() throws IOException {
        //synchronizing by this/method allows to useless entering the below 'if' block in the end of lock
        if (!deflater.finished()) {
            int length = deflater.deflate(deflaterBuffer, 0, deflaterBuffer.length, Deflater.SYNC_FLUSH);

            while (length > 0) {
                out.write(deflaterBuffer, 0, length);

                if (length < deflaterBuffer.length) {
                    break;
                } else {
                    length = deflater.deflate(deflaterBuffer, 0, deflaterBuffer.length, Deflater.SYNC_FLUSH);
                }
            }
        }

        out.flush();
    }
}
