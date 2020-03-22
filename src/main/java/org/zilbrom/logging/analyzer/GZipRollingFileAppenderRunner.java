package org.zilbrom.logging.analyzer;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.OnStartupTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.zilbrom.logging.appenders.GZipRollingFileAppender;
import org.zilbrom.logging.strategies.GZipDefaultRolloverStrategy;

import java.util.Date;

public class GZipRollingFileAppenderRunner {
    private static final String GZIP_ROLLING_FILE_NAME = "out/out/gzip-rolling-file/file.log";
    private static final String GZIP_ROLLING_FILE_PATTERN_NAME = GZIP_ROLLING_FILE_NAME + ".%d{yyyy-MM-dd}.%i";

    private GZipRollingFileAppenderRunner(){}

    public static void run(long cycleSize) {
        LoggerContext loggerContext = prepareGZipRollingFileAppender();
        Logger logger = loggerContext.getLogger(GZipRollingFileAppenderRunner.class.getName());
        long startTime = System.currentTimeMillis();
        logger.info("GZipRollingFileAppender started in {}", new Date(startTime));
        SpeedRollingFileAppenderAnalyzer.runCycle(logger, cycleSize);
        long endTime = System.currentTimeMillis();
        logger.info("GZipRollingFileAppender finished in {}", new Date(endTime));
        logger.info("GZipRollingFileAppender worked =======> {}", endTime - startTime);
    }

    private static LoggerContext prepareGZipRollingFileAppender() {
        LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = loggerContext.getConfiguration();
        Appender appender = createAppender(configuration);
        configuration.addAppender(appender);
        appender.start();
        AppenderRef ref = AppenderRef.createAppenderRef("STDOUT", Level.DEBUG, null);
        AppenderRef[] refs = new AppenderRef[] {ref};
        LoggerConfig loggerConfig = LoggerConfig.createLogger(false, Level.ALL,
                GZipRollingFileAppenderRunner.class.getName(), null, refs, null, configuration,
                null);
        loggerConfig.addAppender(appender, null, null);
        configuration.addLogger(GZipRollingFileAppenderRunner.class.getName(), loggerConfig);
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
        GZipDefaultRolloverStrategy strategy = GZipDefaultRolloverStrategy.createStrategy("1000", null,
                null, null, false, configuration);
        return GZipRollingFileAppender
                .newBuilder()
                .setName(GZipRollingFileAppender.PLUGIN_NAME)
                .withFileName(GZIP_ROLLING_FILE_NAME)
                .withFilePattern(GZIP_ROLLING_FILE_PATTERN_NAME)
                .withPolicy(policy).withStrategy(strategy)
                .setLayout(layout)
                .build();
    }
}
