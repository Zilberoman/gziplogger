package org.zilbrom.logging.analyzer.runners;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.zilbrom.logging.appenders.GZipRollingFileAppender;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AsyncGZipRollingFileAppenderRunner {
    private static final String SOURCE_FILE_PATH = "out/gzip-rolling-file/example.log";

    private AsyncGZipRollingFileAppenderRunner() {}

    public static void run(byte threadsNumber) {
        LoggerContext loggerContext = prepareAsyncGZipRollingFileAppender();
        Logger logger = loggerContext.getLogger(AsyncGZipRollingFileAppenderRunner.class.getName());
        long startTime = System.currentTimeMillis();
        logger.info("AsyncGZipRollingFileAppender started in {}", new Date(startTime));

        try (BufferedReader reader = new BufferedReader(new FileReader(SOURCE_FILE_PATH))) {
            List<String> sources = reader.lines().collect(Collectors.toList());

            ArrayList<Thread> threads = IntStream
                    .range(0, threadsNumber)
                    .mapToObj((int i) -> new Thread(() -> {
                        Logger threadLogger =
                                loggerContext.getLogger(AsyncGZipRollingFileAppenderRunner.class.getName());
                        sources.forEach(threadLogger::debug);
                    }))
                    .collect(Collectors.toCollection(() -> new ArrayList<>(threadsNumber)));

            IntStream.range(0, threadsNumber).forEach(i -> threads.get(i).start());

            long endTime = System.currentTimeMillis();
            logger.info("AsyncGZipRollingFileAppender finished in {}", new Date(endTime));
            logger.info("AsyncGZipRollingFileAppender worked =======> {}", endTime - startTime);
        } catch (IOException ioException) {
            logger.error(ioException);
        }
    }

    private static LoggerContext prepareAsyncGZipRollingFileAppender() {
        LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = loggerContext.getConfiguration();
        Appender appender = GZipRollingFileAppenderRunner.createAppender(configuration);
        // Appender appender = RollingFileAppenderWithGZipRunner.createAppender(configuration);

        configuration.addAppender(appender);
        appender.start();
        AppenderRef ref = AppenderRef.createAppenderRef(GZipRollingFileAppender.PLUGIN_NAME, Level.INFO, null);
        // AppenderRef ref = AppenderRef.createAppenderRef(RollingFileAppender.PLUGIN_NAME, Level.INFO, null);

        AppenderRef[] refs = new AppenderRef[] {ref};

        Appender asyncAppender = AsyncAppender.newBuilder()
                .setName("Async" + GZipRollingFileAppender.PLUGIN_NAME)
                .setAppenderRefs(refs)
                .setBufferSize(20)
                .setConfiguration(configuration)
                .build();

        configuration.addAppender(asyncAppender);
        asyncAppender.start();
        LoggerConfig loggerConfig = LoggerConfig.createLogger(false, Level.ALL,
                AsyncGZipRollingFileAppenderRunner.class.getName(), null, refs, null,
                configuration, null);
        loggerConfig.addAppender(appender, null, null);
        configuration.addLogger(AsyncGZipRollingFileAppenderRunner.class.getName(), loggerConfig);
        loggerContext.updateLoggers();
        return loggerContext;
    }
}
