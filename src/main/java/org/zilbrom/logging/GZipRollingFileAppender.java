package org.zilbrom.logging;

import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.core.appender.rolling.DirectFileRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.RolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.*;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.apache.logging.log4j.core.net.Advertiser;
import org.apache.logging.log4j.core.util.Constants;
import org.zilbrom.logging.strategies.GZipDefaultRolloverStrategy;
import org.zilbrom.logging.strategies.GZipDirectWriteRolloverStrategy;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * An appender that writes to .gz files and can roll over at intervals.
 */
@Plugin(name = GZipRollingFileAppender.PLUGIN_NAME, category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE,
        printObject = true)
public class GZipRollingFileAppender extends AbstractOutputStreamAppender<GZipRollingFileManager> {
    public static final String PLUGIN_NAME = "GZipRollingFileAppender";
    public static final String GZIP_EXTENSION = ".gz";

    private final String fileName;
    private final String filePattern;
    private final Advertiser advertiser;

    private Object advertisement;

    private GZipRollingFileAppender(final String name, final Layout<? extends Serializable> layout, final Filter filter,
                                    final GZipRollingFileManager manager, final String fileName,
                                    final String filePattern, final boolean ignoreExceptions,
                                    final boolean immediateFlush, final Advertiser advertiser) {
        super(name, layout, filter, ignoreExceptions, immediateFlush, Property.EMPTY_ARRAY, manager);

        if (advertiser != null) {
            final Map<String, String> configuration = new HashMap<>(layout.getContentFormat());
            configuration.put("contentType", layout.getContentType());
            configuration.put("name", name);
            advertisement = advertiser.advertise(configuration);
        }

        this.fileName = fileName;
        this.filePattern = filePattern;
        this.advertiser = advertiser;
    }

    @Override
    public boolean stop(long timeout, TimeUnit timeUnit) {
        setStopping();
        final boolean stopped = super.stop(timeout, timeUnit, false);

        if (advertiser != null) {
            advertiser.unadvertise(advertisement);
        }

        setStopped();
        return stopped;
    }

    @Override
    public void append(LogEvent event) {
        getManager().checkRollover(event);

        try {
            if (Constants.ENABLE_DIRECT_ENCODERS) {
                directEncodeEvent(event);
            } else {
                writeByteArrayToManager(event);
            }
        } catch (final AppenderLoggingException exception) {
            error("Unable to write to stream GZIPRollingFileManager for appender" + getName() + ": " + exception);
            throw exception;
        }
    }

    /**
     * Returns the File name for the Appender.
     * @return The file name.
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Returns the file pattern used when rolling over.
     * @return The file pattern.
     */
    public String getFilePattern() {
        return filePattern;
    }

    /**
     * Returns the triggering policy.
     * @param <T> TriggeringPolicy type
     * @return The TriggeringPolicy
     */
    public <T extends TriggeringPolicy> T getTriggeringPolicy() {
        return getManager().getTriggeringPolicy();
    }

    @PluginBuilderFactory
    public static <B extends GZipRollingFileAppender.Builder<B>> B newBuilder() {
        return new GZipRollingFileAppender.Builder<B>().asBuilder();
    }

    /**
     * Builds FileAppender instances.
     * @param <B> The type to build
     */
    public static class Builder<B extends GZipRollingFileAppender.Builder<B>>
            extends AbstractOutputStreamAppender.Builder<B>
            implements org.apache.logging.log4j.core.util.Builder<GZipRollingFileAppender> {
        @PluginBuilderAttribute
        @Required
        private String filePattern;

        @PluginBuilderAttribute
        private String fileName;

        @PluginBuilderAttribute
        private boolean append = true;

        @PluginBuilderAttribute
        private boolean locking;

        @PluginBuilderAttribute
        private boolean advertise;

        @PluginBuilderAttribute
        private String advertiseUri;

        @PluginBuilderAttribute
        private boolean createOnDemand;

        @PluginElement("Policy")
        @Required
        private TriggeringPolicy policy;

        @PluginElement("Strategy")
        private RolloverStrategy strategy;

        public GZipRollingFileAppender build() {
            // Even though some variables may be annotated with @Required,
            // we must still perform validation here for call sites that build builders programmatically.
            final boolean isBufferedTo = isBufferedIo();
            final int bufferSize = getBufferSize();

            if (getName() == null) {
                LOGGER.error("GZipRollingFileAppender '{}': No name provided.", getName());
                return null;
            }

            if (!isBufferedTo && bufferSize > 0) {
                LOGGER.warn("GZipRollingFileAppender '{}': The bufferSize is set to {} but bufferedTo is not true",
                        getName(), getBufferSize());
            }

            if (filePattern == null) {
                LOGGER.error("GZipRollingFileAppender '{}': No file name pattern provided.", getName());
                return null;
            }

            if (policy == null) {
                LOGGER.error("GZIPRollingFileAppender '{}': No TriggeringPolicy provided.", getName());
                return null;
            }

            if (strategy == null) {
                strategy = fileName != null
                        ? GZipDefaultRolloverStrategy.createStrategy(null, null, null,
                        null, true, getConfiguration())
                        : GZipDirectWriteRolloverStrategy.createStrategy(null, null,
                        true, getConfiguration());
            } else if (fileName == null && !(strategy instanceof DirectFileRolloverStrategy)) {
                LOGGER.error ("GZipRollingFileAppender '{}':"
                        + " When no file name is provided a DirectFilenameRolloverStrategy must be configured",
                        getName());
                return null;
            }

            fileName += GZIP_EXTENSION;
            filePattern += GZIP_EXTENSION;
            final  Layout<? extends Serializable> layout = getOrCreateLayout();
            final GZipRollingFileManager manager = GZipRollingFileManager.getFileManager(fileName, filePattern, append,
                    isBufferedTo, policy, strategy, advertiseUri, layout, bufferSize, isImmediateFlush(),
                    createOnDemand, getConfiguration());

            if (manager == null) {
                return null;
            }

            manager.initialize();
            return new GZipRollingFileAppender(getName(), layout, getFilter(), manager, fileName, filePattern,
                    isIgnoreExceptions(), isImmediateFlush(), advertise ? getConfiguration().getAdvertiser() : null);
        }

        public String getAdvertiseUri() {
            return advertiseUri;
        }

        public String getFileName() {
            return fileName;
        }

        public boolean isAdvertise() {
            return advertise;
        }

        public boolean isAppend() {
            return append;
        }

        public boolean isCreateOnDemand() {
            return createOnDemand;
        }

        public boolean isLocking() {
            return locking;
        }

        public String getFilePattern() {
            return filePattern;
        }

        public TriggeringPolicy getPolicy() {
            return policy;
        }

        public RolloverStrategy getStrategy() {
            return strategy;
        }

        public B withAdvertise(final boolean advertise) {
            this.advertise = advertise;
            return asBuilder();
        }

        public B withAdvertiseUri(final String advertiseUri) {
            this.advertiseUri = advertiseUri;
            return asBuilder();
        }

        public B withAppend(final boolean append) {
            this.append = append;
            return asBuilder();
        }

        public B withFileName(final String fileName) {
            this.fileName = fileName;
            return asBuilder();
        }

        public B withCreateOnDemand(final boolean createOnDemand) {
            this.createOnDemand = createOnDemand;
            return asBuilder();
        }

        public B withLocking(final boolean locking) {
            this.locking = locking;
            return asBuilder();
        }

        public B withFilePattern(final String filePattern) {
            this.filePattern = filePattern;
            return asBuilder();
        }

        public B withPolicy(final TriggeringPolicy policy) {
            this.policy = policy;
            return asBuilder();
        }

        public B withStrategy(final RolloverStrategy strategy) {
            this.strategy = strategy;
            return asBuilder();
        }
    }
}
