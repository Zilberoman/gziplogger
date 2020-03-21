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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;

/**
 * When rolling over,
 * <code>GZipDefaultRolloverStrategy</code> renames gzip files according to an algorithm as described below.
 * The GZipDefaultRolloverStrategy extends DefaultRolloverStrategy.
 */
@Plugin(name = GZipDefaultRolloverStrategy.STRATEGY_NAME, category = Core.CATEGORY_NAME, printObject = true)
public class GZipDefaultRolloverStrategy extends AbstractRolloverStrategy {
    public static final String STRATEGY_NAME = "GZipDefaultRolloverStrategy";
    private static final int MIN_WINDOW_SIZE = 1;
    private static final int DEFAULT_WINDOW_SIZE = 7;

    /**
     * Creates the GZipRolloverStrategy.
     *
     * @param max The maximum number of files to keep
     * @param min The minimum number of files to keep
     * @param fileIndex If set to "max" (the default),
     *                 files with a higher index will be never than files with a smaller index.
     *                 If set to "min", file renaming and the counter will follow the Fixed Window Strategy.
     * @param customActions custom actions to perform asynchronously after rollover
     * @param stopCustomActionsOnError whether to stop executing asynchronous actions if an error occurs
     * @param config The Configuration
     * @return A GZipDefaultRolloverStrategy
     */
    @PluginFactory
    public static GZipDefaultRolloverStrategy createStrategy(@PluginAttribute("max") final String max,
                                                             @PluginAttribute("min") final String min,
                                                             @PluginAttribute("fileIndex") final String fileIndex,
                                                             @PluginElement("Actions") final Action[] customActions,
                                                             @PluginAttribute(value = "stopCustomActionsOnError",
                                                                     defaultBoolean = true)
                                                                 final boolean stopCustomActionsOnError,
                                                             @PluginConfiguration final Configuration config) {
        int minIndex, maxIndex;
        boolean useMax;

        if (fileIndex != null && fileIndex.equalsIgnoreCase("nomax")) {
            minIndex = Integer.MIN_VALUE;
            maxIndex = Integer.MAX_VALUE;
            useMax = false;
        } else {
            useMax = fileIndex == null || fileIndex.equalsIgnoreCase("max");
            minIndex = MIN_WINDOW_SIZE;

            if (min != null) {
                minIndex = Integer.parseInt(min);

                if (minIndex < 1) {
                    LOGGER.error("Minimum window size too small. Limited to " + MIN_WINDOW_SIZE);
                    minIndex = MIN_WINDOW_SIZE;
                }
            }

            maxIndex = DEFAULT_WINDOW_SIZE;

            if (max != null) {
                maxIndex = Integer.parseInt(max);

                if (maxIndex < minIndex) {
                    maxIndex = Math.max(minIndex, DEFAULT_WINDOW_SIZE);
                    LOGGER.error("Maximum window size must be greater than the minimum window size. Set to {}",
                            maxIndex);
                }
            }
        }

        return new GZipDefaultRolloverStrategy(minIndex, maxIndex, useMax, Deflater.DEFAULT_COMPRESSION,
                config.getStrSubstitutor(), customActions, stopCustomActionsOnError);
    }

    /**
     * Index for oldest retained log file.
     */
    private final int maxIndex;

    /**
     * Index for most recent log file
     */
    private final int minIndex;
    private final boolean useMax;
    private final int compressionLevel;
    private final List<Action> customActions;
    private final boolean stopCustomActionsOnError;

    /**
     * Constructs a new instance.
     *
     * @param minIndex The minimum index.
     * @param maxIndex The maximum index.
     * @param customActions custom actions to perform asynchronously after rollover
     * @param stopCustomActionsOnError whether to stop executing asynchronous actions if an error occurs
     */
    protected GZipDefaultRolloverStrategy(final int minIndex, final int maxIndex, final boolean useMax,
                                          final int compressionLevel, final StrSubstitutor strSubstitutor,
                                          final Action[] customActions, final boolean stopCustomActionsOnError) {
        super(strSubstitutor);
        this.minIndex = minIndex;
        this.maxIndex = maxIndex;
        this.useMax = useMax;
        this.compressionLevel = compressionLevel;
        this.stopCustomActionsOnError = stopCustomActionsOnError;
        this.customActions = customActions == null ? Collections.emptyList() : Arrays.asList(customActions);
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    public int getMaxIndex() {
        return maxIndex;
    }

    public int getMinIndex() {
        return minIndex;
    }

    public boolean isUseMax() {
        return useMax;
    }

    public List<Action> getCustomActions() {
        return customActions;
    }

    public boolean isStopCustomActionsOnError() {
        return stopCustomActionsOnError;
    }

    private int purge(final int lowIndex, final int highIndex, final RollingFileManager manager) {
        return useMax ? purgeAscending(lowIndex, highIndex, manager) : purgeDescending(lowIndex, highIndex, manager);
    }

    /**
     * Purges and renames old log files in preparation for rollover.
     * The oldest file will have the smallest index, the newest the highest.
     *
     * @param lowIndex low index. Log file associated with low index will be deleted if needed.
     * @param highIndex high index.
     * @param manager The RollingFileManager
     * @return true if purge was successful and rollover should be attempted
     */
    private int purgeAscending(final int lowIndex, final int highIndex, final RollingFileManager manager) {
        final SortedMap<Integer, Path> eligibleFiles = getEligibleFiles(manager);
        final int maxFiles = highIndex - lowIndex + 1;
        boolean renameFiles = false;

        while (eligibleFiles.size() >= maxFiles) {
            try {
                LOGGER.debug("Eligible files: {}", eligibleFiles);
                Integer key = eligibleFiles.firstKey();
                LOGGER.debug("Deleting {}", eligibleFiles.get(key).toFile().getAbsoluteFile());
                Files.delete(eligibleFiles.get(key));
                eligibleFiles.remove(key);
                renameFiles = true;
            } catch(IOException ioe) {
                LOGGER.error("Unable to delete {}, {}", eligibleFiles.firstKey(), ioe.getMessage(), ioe);
                break;
            }
        }

        final StringBuilder buf = new StringBuilder();

        if (renameFiles) {
            for (Map.Entry<Integer, Path> entry: eligibleFiles.entrySet()) {
                buf.setLength(0);
                manager.getPatternProcessor().formatFileName(strSubstitutor, buf, entry.getKey() - 1);
                Action action = this.createFileRenameAction(entry, buf);

                try {
                    LOGGER.debug("GZipDefaultRolloverStrategy.purgeAscending executing {}", action);

                    if (!action.execute()) {
                        return -1;
                    }
                } catch (final Exception exception) {
                    LOGGER.warn("Exceptions during purge in GZipRollingFileAppender", exception);
                    return  -1;
                }
            }
        }

        int compositeIndex = !eligibleFiles.isEmpty() && eligibleFiles.lastKey() < highIndex
                ? eligibleFiles.lastKey() + 1
                : highIndex;
        return eligibleFiles.size() > 0 ? compositeIndex : lowIndex;
    }

    /**
     * Purges and renames old log files in preparation for rollover.
     * The newest file will have the smallest index, the oldest will have the highest.
     *
     * @param lowIndex low index
     * @param highIndex high index. Log file associated with high index will be deleted if needed
     * @param manager The RollingFileManager
     * @return true if purge was successful and rollover should be attempted.
     */
    private int purgeDescending(final int lowIndex, final int highIndex, final RollingFileManager manager) {
        // Retrieve the files in descending order, so the highest key will be first
        final SortedMap<Integer, Path> eligibleFiles = getEligibleFiles(manager, false);
        final int maxFiles = highIndex - lowIndex + 1;

        while(eligibleFiles.size() >= maxFiles) {
            try {
                Integer key = eligibleFiles.firstKey();
                Files.delete(eligibleFiles.get(key));
                eligibleFiles.remove(key);
            } catch (IOException ioException) {
                LOGGER.error("Unable to delete {}, {}", eligibleFiles.firstKey(), ioException.getMessage(),
                        ioException);
                break;
            }
        }

        final StringBuilder buf = new StringBuilder();

        for (Map.Entry<Integer, Path> entry: eligibleFiles.entrySet()) {
            buf.setLength(0);
            manager.getPatternProcessor().formatFileName(strSubstitutor, buf, entry.getKey() + 1);
            Action action = this.createFileRenameAction(entry, buf);

            try {
                LOGGER.debug("GZipDefaultRolloverStrategy.purgeDescending executing {}", action);

                if (!action.execute()) {
                    return -1;
                }
            } catch (final Exception exception) {
                LOGGER.warn("Exception during purge in GZipRollingFileAppender", exception);
                return -1;
            }
        }

        return lowIndex;
    }

    /**
     * Performs the rollover.
     *
     * @param manager The RollingFileManager name for current active log file.
     * @return A RolloverDescription
     * @throws SecurityException if an error occurs
     */
    @Override
    public RolloverDescription rollover(RollingFileManager manager) throws SecurityException {
        int fileIndex;

        if (minIndex == Integer.MIN_VALUE) {
            final SortedMap<Integer, Path> eligibleFiles = getEligibleFiles(manager);
            fileIndex = eligibleFiles.size() > 0 ? eligibleFiles.lastKey() + 1 : 1;
        } else {
            if (maxIndex < 0) {
                return null;
            }

            final long startNanos = System.nanoTime();
            fileIndex = purge(minIndex, maxIndex, manager);

            if (fileIndex < 0) {
                return  null;
            }

            if (LOGGER.isTraceEnabled()) {
                final double durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                LOGGER.trace("GZipDefaultRolloverStrategy.purge took {} milliseconds", durationMillis);
            }
        }

        final StringBuilder buf = new StringBuilder(255);
        manager.getPatternProcessor().formatFileName(strSubstitutor, buf, fileIndex);
        final String currentFileName = manager.getFileName();
        String renameTo = buf.toString();

        if (currentFileName.equals(renameTo)) {
            LOGGER.warn("Attempt to rename file {} to itself will be ignored", currentFileName);
            return new RolloverDescriptionImpl(currentFileName, false, null, null);
        }

        final FileRenameAction renameAction = new FileRenameAction(new File(currentFileName), new File(renameTo),
                manager.isRenameEmptyFiles());
        final Action asyncAction = new CompositeAction(getCustomActions(), isStopCustomActionsOnError());
        return new RolloverDescriptionImpl(currentFileName, false, renameAction, asyncAction);
    }

    @Override
    public String toString() {
        return "GZipDefaultRolloverStrategy(min = " + minIndex + ", max = " + maxIndex + ", useMax = " + useMax + ")";
    }

    private FileRenameAction createFileRenameAction(Map.Entry<Integer, Path> entry, StringBuilder buf) {
        File file = entry.getValue().toFile();
        String currentName = file.getName();
        String renameTo = buf.toString();
        int suffixLength = suffixLength(renameTo);

        if (suffixLength > 0 && suffixLength(currentName) == 0) {
            renameTo = renameTo.substring(0, renameTo.length() - suffixLength);
        }

        return new FileRenameAction(file, new File(renameTo), true);
    }
}
