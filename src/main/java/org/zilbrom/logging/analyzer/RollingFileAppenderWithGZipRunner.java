package org.zilbrom.logging.analyzer;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.OnStartupTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

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
        logger.info("RollingFileAppender with gzip worked ======> {}", endTime - startTime;
    }

    private static LoggerContext prepareRollingFileAppender() {
        LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = loggerContext.getConfiguration();

        PatternLayout layout = PatternLayout
                .newBuilder()
                .withPattern(SpeedRollingFileAppenderAnalyzer.LAYOUT_PATTERN)
                .build();

        OnStartupTriggeringPolicy startupTriggeringPolicy = OnStartupTriggeringPolicy.createPolicy(0);

        SizeBasedTriggeringPolicy sizePolicy = SizeBasedTriggeringPolicy
                .createPolicy(SpeedRollingFileAppenderAnalyzer.ROLLING_FILE_SIZE);

        CompositeTriggeringPolicy policy = CompositeTriggeringPolicy.createPolicy(startupTriggeringPolicy, sizePolicy);
        DefaultRolloverStrategy strategy = DefaultRolloverStrategy.createStrategy("1000", null, null,
                null, null, false, configuration);

        Appender appender = RollingFileAppender
                .newBuilder()
                .setName(RollingFileAppender.PLUGIN_NAME)
                .withFileName(ROLLING_FILE_NAME)
                .withFilePattern(ROLLING_FILE_PATTERN_NAME)
                .withPolicy(policy)
                .withStrategy(strategy)
                .setLayout(layout)
                .build();

        configuration.addAppender(appender);
        appender.start();
        AppenderRef ref = AppenderRef.createAppenderRef("STDOUT", Level.DEBUG, null);
        AppenderRef[] refs = new AppenderRef[] {ref};
        LoggerConfig loggerConfig = LoggerConfig.createLogger(true, Level.ALL, RollingFileAppenderWithGZipRunner.class.getName(), null, refs, null, configuration, null);
        loggerConfig.addAppender(appender, null, null);
        configuration.addLogger(RollingFileAppenderWithGZipRunner.class.getName(), loggerConfig);
        loggerContext.updateLoggers();
        return loggerContext;
    }

    public static Appender createAppender(Configuration configuration) {
        PatternLayout layout = PatternLayout
                .newBuilder()
                .withPattern(SpeedRollingFileAppenderAnalyzer.LAYOUT_PATTERN)
                .build();

        SizeBasedTriggeringPolicy sizePolicy = SizeBasedTriggeringPolicy
                .createPolicy(SpeedRollingFileAppenderAnalyzer.ROLLING_FILE_SIZE);

        OnStartupTriggeringPolicy startupTriggeringPolicy = OnStartupTriggeringPolicy.createPolicy(0);
        CompositeTriggeringPolicy policy = CompositeTriggeringPolicy.createPolicy(sizePolicy, startupTriggeringPolicy);
        DefaultRolloverStrategy strategy = DefaultRolloverStrategy.createStrategy("1000", null, null,
                String.valueOf(Deflater.DEFAULT_COMPRESSION), null, false,
                configuration);

        return RollingFileAppender
                .newBuilder()
                .withName(RollingFileAppender.PLUGIN_NAME)
                .withFileName(ROLLING_FILE_NAME)
                .withFilePattern(ROLLING_FILE_PATTERN_NAME)
                .withPolicy(policy)
                .withStrategy(strategy)
                .withLayout(layout)
                .build();
;    }
}
