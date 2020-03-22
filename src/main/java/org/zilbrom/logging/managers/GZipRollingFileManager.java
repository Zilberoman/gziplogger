package org.zilbrom.logging.managers;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.core.appender.ConfigurationFactoryData;
import org.apache.logging.log4j.core.appender.ManagerFactory;
import org.apache.logging.log4j.core.appender.rolling.RollingFileManager;
import org.apache.logging.log4j.core.appender.rolling.RolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.util.Constants;
import org.apache.logging.log4j.core.util.FileUtils;
import org.zilbrom.logging.streams.RollingCountingOutputStream;
import org.zilbrom.logging.streams.RollingGZIPOutputStream;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Date;

public class GZipRollingFileManager extends RollingFileManager {
    private static GZipRollingFileManagerFactory factory = new GZipRollingFileManagerFactory();
    private static long flushPeriod = Constants.MILLIS_IN_SECONDS;

    private static RollingCountingOutputStream countingOutputStream;

    protected GZipRollingFileManager(LoggerContext loggerContext, String fileName, String pattern, OutputStream os,
                                     boolean append, boolean createOnDemand, long size, long time,
                                     TriggeringPolicy triggeringPolicy, RolloverStrategy rolloverStrategy,
                                     String advertiseUri, Layout<? extends Serializable> layout, boolean writeHeader,
                                     ByteBuffer buffer) {
        super(loggerContext, fileName, pattern, os, append, createOnDemand, size, time, triggeringPolicy,
                rolloverStrategy, advertiseUri, layout, "", "", "", writeHeader, buffer);
    }

    public static GZipRollingFileManager getFileManager(final String fileName, final String pattern,
                                                        final boolean append, final boolean bufferedIO,
                                                        final TriggeringPolicy policy, final RolloverStrategy strategy,
                                                        final String advertiseURI,
                                                        final Layout<? extends Serializable> layout,
                                                        final int bufferSize, final boolean immediateFlush,
                                                        final boolean createOnDemand,
                                                        final Configuration configuration) {
        String name = fileName == null ? pattern : fileName;
        return (GZipRollingFileManager) getManager(name,
                new FactoryData(fileName, pattern, append, bufferedIO, policy, strategy, advertiseURI, layout,
                        bufferSize, immediateFlush, createOnDemand, configuration), factory);
    }

    /**
     * Factory to create a RollingFileManager.
     */
    private static class GZipRollingFileManagerFactory implements ManagerFactory<GZipRollingFileManager, FactoryData> {
        /**
         * Creates a RollingFileManager
         *
         * @param name The name of the entity to manage.
         * @param data The data required to create the entity.
         * @return a RollingFileManager.
         */
        public GZipRollingFileManager createManager(final String name, final FactoryData data) {
            long size = 0L;
            boolean writeHeader = !data.append;
            File file = null;
            boolean newFileCreated = !data.createOnDemand;

            if (data.fileName != null) {
                file = new File(data.fileName);
                writeHeader = writeHeader || !file.exists();

                try {
                    FileUtils.makeParentDirs(file);
                    newFileCreated = newFileCreated && file.createNewFile();
                    LOGGER.trace("New file '{}' created = {}", name, newFileCreated);
                } catch (final IOException ioException) {
                    LOGGER.error("Unable to create file {}", name, ioException);
                    return null;
                }

                size = data.append ? file.length() : 0;
            }

            try {
                final int actualSize = data.bufferedIO ? data.bufferedSize : Constants.ENCODER_BYTE_BUFFER_SIZE;
                final ByteBuffer buffer = ByteBuffer.wrap(new byte[actualSize]);
                OutputStream os = null;
                final long time = data.createOnDemand || file == null ? System.currentTimeMillis() : file.lastModified();

                if (!data.createOnDemand && data.fileName != null) {
                    FileOutputStream fileOutputStream = new FileOutputStream(file, data.append);
                    countingOutputStream = new RollingCountingOutputStream(fileOutputStream);

                    //Add gzip header if new file was created
                    os = new RollingGZIPOutputStream(countingOutputStream, actualSize, flushPeriod, newFileCreated);
                }

                return new GZipRollingFileManager(data.getLoggerContext(), data.fileName, data.pattern, os, data.append,
                        data.createOnDemand, size, time, data.policy, data.strategy, data.advertiseURI, data.layout,
                        writeHeader, buffer);
            } catch (final IOException exception) {
                LOGGER.error("RollingFileManager ({}) {}", name, exception, exception);
            }

            return null;
        }
    }

    //override to make visible for unit tests
    @Override
    protected synchronized void write(final byte[] bytes, final int offset, final int length,
                                      final boolean immediateFlush) {
        super.write(bytes, offset, length, immediateFlush);
    }

    private static class FactoryData extends ConfigurationFactoryData {
        private final String fileName;
        private final String pattern;
        private final boolean append;
        private final boolean bufferedIO;
        private final int bufferedSize;
        private final boolean immediateFlush;
        private final boolean createOnDemand;
        private final TriggeringPolicy policy;
        private final RolloverStrategy strategy;
        private final String advertiseURI;
        private final Layout<? extends Serializable> layout;

        /**
         * Creates the data for the factory.
         *
         * @param pattern The pattern.
         * @param append The append flag.
         * @param bufferedIO The bufferedIO flag.
         * @param layout The Layout.
         * @param bufferSize The buffer size.
         * @param immediateFlush flush on every write or not
         * @param createOnDemand true if you want to lazy-create the file (a.k.a. on-demand)
         * @param configuration The configuration
         */
        public FactoryData(final String fileName, final String pattern, final boolean append, final boolean bufferedIO,
                           final TriggeringPolicy policy, final RolloverStrategy strategy, final String advertiseURI,
                           final Layout<? extends Serializable> layout, final int bufferSize,
                           final boolean immediateFlush, final boolean createOnDemand,
                           final Configuration configuration) {
            super(configuration);
            this.fileName = fileName;
            this.pattern = pattern;
            this.append = append;
            this.bufferedIO = bufferedIO;
            this.bufferedSize = bufferSize;
            this.policy = policy;
            this.strategy = strategy;
            this.advertiseURI =advertiseURI;
            this.layout = layout;
            this.immediateFlush = immediateFlush;
            this.createOnDemand = createOnDemand;
        }

        public TriggeringPolicy getTriggeringPolicy() {
            return policy;
        }

        public RolloverStrategy getRolloverStrategy() {
            return strategy;
        }

        @Override
        public String toString() {
            return super.toString() + "[pattern = " + pattern + ", append = " + append + ", bufferedIO = " + bufferedIO
                    + ", bufferedSize = " + bufferedSize + ", policy = " + policy + ", strategy = " + strategy
                    + ", advertiseURI = " + advertiseURI + ", layout = " + layout + "]";
        }
    }

    @Override
    public void updateData(Object data) {
        final FactoryData factoryData = (FactoryData) data;
        setRolloverStrategy(factoryData.getRolloverStrategy());
        setTriggeringPolicy(factoryData.getTriggeringPolicy());
    }

    @Override
    protected OutputStream createOutputStream() throws FileNotFoundException {
        String fileName = getFileName();
        LOGGER.debug("Now writing to {} at {}", fileName, new Date());

        if (fileName.endsWith(".")) {
            fileName = fileName.substring(0, fileName.length() - 1);
        }

        FileOutputStream fileOutputStream = new FileOutputStream(fileName, isAppend());
        countingOutputStream = new RollingCountingOutputStream(fileOutputStream);

        try {
            return new RollingGZIPOutputStream(countingOutputStream, Constants.ENCODER_BYTE_BUFFER_SIZE, flushPeriod,
                    true);
        } catch (IOException ioException) {
            LOGGER.error("RollingGZIPOutputStream creating exception after rolling", ioException);
            return countingOutputStream;
        }
    }

    @Override
    public long getFileSize() {
        size = countingOutputStream.getCurrentStreamSize();
        return size + byteBuffer.position();
    }

    @Override
    protected synchronized void writeToDestination(byte[] bytes, int offset, int length) {
        if (isLocking()) {
            try {
                @SuppressWarnings("resourse")
                final FileChannel channel = ((FileOutputStream) getOutputStream()).getChannel();

                /*
                    Lock the whole file. This could be optimized to only lock from the current file position.
                    Note that locking may be advisory on some systems and mandatory on others,
                    so locking just from current position would allow reading on systems where locking is mandatory.
                     Also, Java 6 will throw an exception
                     if the region of the file is already locked by another FileChannel in the same JVM.
                      Hopefully, that will be avoided since every file should have a single file manager -
                      unless two different files settings are configured that somehow map to the same file.
                 */
                try(final FileLock lock = channel.lock(0, Long.MAX_VALUE, false)) {
                    writeToStream(bytes, offset, length);
                }
            } catch (final IOException ioException) {
                throw new AppenderLoggingException("Unable to obtain lock on {}", getName(), ioException);
            }
        } else {
            writeToStream(bytes, offset, length);
        }

        size = countingOutputStream.getCurrentStreamSize();
    }

    private synchronized void writeToStream(byte[] bytes, int offset, int length) {
        try {
            getOutputStream().write(bytes, offset, length);
        } catch (final IOException ioException) {
            throw new AppenderLoggingException("Error writing to stream {}", getName(), ioException);
        }
    }
}
