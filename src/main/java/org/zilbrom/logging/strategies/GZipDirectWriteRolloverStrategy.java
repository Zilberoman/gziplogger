package org.zilbrom.logging.strategies;

import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.appender.rolling.AbstractRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.RollingFileManager;
import org.apache.logging.log4j.core.appender.rolling.RolloverDescription;
import org.apache.logging.log4j.core.appender.rolling.RolloverDescriptionImpl;
import org.apache.logging.log4j.core.appender.rolling.action.Action;
import org.apache.logging.log4j.core.appender.rolling.action.CompositeAction;
import org.apache.logging.log4j.core.appender.rolling.action.FileRenameAction;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.*;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;
import org.zilbrom.logging.appenders.GZipRollingFileAppender;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;

/**
 * When rolling over,
 * <code>DirectWriteRolloverStrategy</code> writes directly to the gzip file as resolved by the file pattern.
 * Files will be renamed files according to an algorithm as described below.
 */
@Plugin(name = GZipDefaultRolloverStrategy.STRATEGY_NAME, category = Core.CATEGORY_NAME, printObject = true)
public class GZipDirectWriteRolloverStrategy extends AbstractRolloverStrategy {
    public static final String STRATEGY_NAME = "GZipDirectWriteRolloverStrategy";
    private static final int DEFAULT_MAX_FILES = 7;

    /**
     * Creates the GZipDirectWriteRolloverStrategy.
     *
     * @param maxFiles The maximum number of files that match the date portion of the pattern to keep
     * @param customActions custom actions to perform asynchronously after rollover
     * @param stopCustomActionsOnError whether to stop executing asynchronous actions if an error occurs
     * @param config The Configuration
     * @return A GZipDirectWriteRolloverStrategy.
     */
    @PluginFactory
    public static GZipDirectWriteRolloverStrategy createStrategy(@PluginAttribute("maxFiles") final String maxFiles,
                                                                 @PluginElement("Actions") final Action[] customActions,
                                                                 @PluginAttribute(value = "stopCustomActionsOnError",
                                                                         defaultBoolean = true)
                                                                     final boolean stopCustomActionsOnError,
                                                                 @PluginConfiguration final Configuration config) {
        int maxIndex = Integer.MAX_VALUE;

        if (maxFiles != null) {
            maxIndex = Integer.parseInt(maxFiles);

            if (maxIndex < 0) {
                maxIndex = Integer.MAX_VALUE;
            } else if (maxIndex < 2) {
                LOGGER.error("Maximum files too small. Limited to " + DEFAULT_MAX_FILES);
                maxIndex = DEFAULT_MAX_FILES;
            }
        }

        return new GZipDirectWriteRolloverStrategy(maxIndex, config.getStrSubstitutor(), customActions,
                stopCustomActionsOnError);
    }

    /**
     * Index for most recent log file.
     */
    private final int maxFiles;
    private final int compressionLevel;
    private final List<Action> customActions;
    private final boolean stopCustomActionsOnError;
    private volatile String currentFileName;
    private int nextIndex = -1;

    /**
     * Constructs a nes instance.
     *
     * @param maxFiles The minimum index.
     * @param customActions custom actions to perform asynchronously after rollover
     * @param stopCustomActionsOnError whether to stop executing asynchronous actions if an error occurs
     */
    protected GZipDirectWriteRolloverStrategy(final int maxFiles, final StrSubstitutor strSubstitutor,
                                              final Action[] customActions, final boolean stopCustomActionsOnError) {
        super(strSubstitutor);
        this.maxFiles = maxFiles;
        this.compressionLevel = Deflater.DEFAULT_COMPRESSION;
        this.stopCustomActionsOnError = stopCustomActionsOnError;
        this.customActions = customActions == null ? Collections.emptyList() : Arrays.asList(customActions);
    }

    public int getMaxFiles() {
        return maxFiles;
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    public List<Action> getCustomActions() {
        return customActions;
    }

    public boolean isStopCustomActionsOnError() {
        return stopCustomActionsOnError;
    }

    private int purge(final RollingFileManager manager) {
        SortedMap<Integer, Path> eligibleFiles = getEligibleFiles(manager);
        LOGGER.debug("Fount {} eligible files, max is {}", eligibleFiles.size(), maxFiles);

        while (eligibleFiles.size() >= maxFiles) {
            try {
                Integer key = eligibleFiles.firstKey();
                Files.delete(eligibleFiles.get(key));
                eligibleFiles.remove(key);
            } catch (IOException ioException) {
                LOGGER.error("Unable to delete {}", eligibleFiles.firstKey(), ioException);
                break;
            }
        }

        return eligibleFiles.size() > 0 ? eligibleFiles.lastKey() : 1;
    }

    public String getCurrentFileName(final RollingFileManager manager) {
        if (currentFileName == null) {
            SortedMap<Integer, Path> eligibleFiles = getEligibleFiles(manager);
            int currentIndex = nextIndex > 0 ? nextIndex : eligibleFiles.size();
            final int fileIndex = eligibleFiles.size() > 0 ? currentIndex : 1;
            final StringBuilder buf = new StringBuilder(255);
            manager.getPatternProcessor().formatFileName(strSubstitutor, buf,true, fileIndex);
            int suffixLength = suffixLength(buf.toString());
            currentFileName = suffixLength > 0 ? buf.substring(0, buf.length() - suffixLength) : buf.toString();
        }

        return currentFileName;
    }

    /**
     * Perform the rollover.
     *
     * @param manager The RollingFileManager name for current active log file.
     * @return A RolloverDescription
     * @throws SecurityException if an error occurs
     */
    @Override
    public RolloverDescription rollover(RollingFileManager manager) throws SecurityException {
        LOGGER.debug("Rolling " + currentFileName);

        if (maxFiles < 0) {
            return null;
        }

        final long startNanos = System.nanoTime();
        final int fileIndex = purge(manager);

        if (LOGGER.isTraceEnabled()) {
            final double durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            LOGGER.trace("GZipDirectWriteRolloverStrategy.purge() took {} milliseconds", durationMillis);
        }

        final String sourceName = currentFileName;
        currentFileName = null;
        nextIndex = fileIndex + 1;
        Action action = new FileRenameAction(new File(sourceName),
                new File(sourceName + GZipRollingFileAppender.GZIP_EXTENSION), true);

        try {
            LOGGER.debug("GZipDirectWriteRolloverStrategy.rollover executing {}", action);

            if (!action.execute()) {
                LOGGER.warn("Exception during rename file in GZipRollingFileAppender");
            }
        } catch (final Exception exception) {
            LOGGER.warn("Exception during rollover in GZipRollingFileAppender", exception);
        }

        final Action asyncAction = new CompositeAction(customActions, stopCustomActionsOnError);
        return new RolloverDescriptionImpl(sourceName, false, null, asyncAction);
    }

    @Override
    public String toString() {
        return "GZipDirectWriteRolloverStrategy(maxFiles = " + maxFiles + ')';
    }
}
