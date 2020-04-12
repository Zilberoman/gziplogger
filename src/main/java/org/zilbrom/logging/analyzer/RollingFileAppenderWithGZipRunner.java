package org.zilbrom.logging.analyzer;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.util.Date;
import java.util.zip.Deflater;

public class RollingFileAppenderWithGZipRunner {
    private static final String ROLLING_FILE_NAME = "out/out/rolling-file/gzip/file.log";
    private static final String ROLLING_FILE_PATTERN_NAME = ROLLING_FILE_NAME + "%d{yyyy-MM-dd}.%i.gz";

    private RollingFileAppenderWithGZipRunner(){}

    public static void run(long cycleSize) {
        LoggerContext loggerContext = prepareRollingFileAppender();
        Logger logger = loggerContext.getLogger(RollingFileAppenderWithGZipRunner.class.getName());
        long startTime = System.currentTimeMillis();
        logger.info("RollingFileAppender with gzip started in {}", new Date(startTime));
        SpeedRollingFileAppenderAnalyzer.runCycle(logger, cycleSize);
        long endTime = System.currentTimeMillis();
        logger.info("RollingFileAppender with gzip finished in {}", new Date(endTime));
        logger.info("RollingFileAppender with gzip worked ======> {}", endTime - startTime);
    }

    private static LoggerContext prepareRollingFileAppender() {
        LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = loggerContext.getConfiguration();

        Appender appender = RollingFileAppenderBuilder.createAppender(ROLLING_FILE_NAME, ROLLING_FILE_PATTERN_NAME,
                configuration);
        configuration.addAppender(appender);
        appender.start();
        LoggerConfig loggerConfig = LoggerConfig.createLogger(true, Level.ALL,
                RollingFileAppenderWithGZipRunner.class.getName(), null,
                RollingFileAppenderBuilder.createAppenderRefs(), null, configuration, null);
        loggerConfig.addAppender(appender, null, null);
        configuration.addLogger(RollingFileAppenderWithGZipRunner.class.getName(), loggerConfig);
        loggerContext.updateLoggers();
        return loggerContext;
    }

    public static Appender createAppender(Configuration configuration) {
        DefaultRolloverStrategy strategy = DefaultRolloverStrategy
                .newBuilder()
                .withMax("1000")
                .withCompressionLevelStr(String.valueOf(Deflater.DEFAULT_COMPRESSION))
                .withStopCustomActionsOnError(false)
                .withConfig(configuration)
                .build();

        return RollingFileAppender
                .newBuilder()
                .setName(RollingFileAppender.PLUGIN_NAME)
                .withFileName(ROLLING_FILE_NAME)
                .withFilePattern(ROLLING_FILE_PATTERN_NAME)
                .withPolicy(RollingFileAppenderBuilder.createPolicy())
                .withStrategy(strategy)
                .setLayout(RollingFileAppenderBuilder.createPattern())
                .build();
    }
}
