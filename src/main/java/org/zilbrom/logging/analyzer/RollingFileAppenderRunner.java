package org.zilbrom.logging.analyzer;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.util.Date;

public class RollingFileAppenderRunner {
    private static final String ROLLING_FILE_NAME = "out/out/rolling-file/file.log";
    private static final String ROLLING_FILE_PATTERN_NAME = ROLLING_FILE_NAME + "%d{yyyy-MM-dd}.%i";

    private RollingFileAppenderRunner(){}

    public static void run(long cycleSize) {
        LoggerContext loggerContext = prepareRollingFileAppender();
        Logger logger = loggerContext.getLogger(RollingFileAppenderRunner.class.getName());
        long startTime = System.currentTimeMillis();
        logger.info("RollingFileAppender started in {}", new Date(startTime));
        SpeedRollingFileAppenderAnalyzer.runCycle(logger, cycleSize);
        long endTime = System.currentTimeMillis();
        logger.info("RollingFileAppender finished in {}", new Date(endTime));
        logger.info("RollingFileAppender worked =====> {}", endTime - startTime);
    }

    private static LoggerContext prepareRollingFileAppender() {
        LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = loggerContext.getConfiguration();

        Appender appender = RollingFileAppenderBuilder.createAppender(ROLLING_FILE_NAME, ROLLING_FILE_PATTERN_NAME,
                configuration);

        configuration.addAppender(appender);
        appender.start();

        LoggerConfig loggerConfig = LoggerConfig.createLogger(true, Level.ALL,
                RollingFileAppenderRunner.class.getName(), null,
                RollingFileAppenderBuilder.createAppenderRefs(), null, configuration, null);
        loggerConfig.addAppender(appender, null, null);
        configuration.addLogger(RollingFileAppenderRunner.class.getName(), loggerConfig);
        loggerContext.updateLoggers();
        return loggerContext;
    }
}
