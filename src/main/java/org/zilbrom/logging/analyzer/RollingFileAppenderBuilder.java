package org.zilbrom.logging.analyzer;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.OnStartupTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.PatternLayout;

public class RollingFileAppenderBuilder {
    public static CompositeTriggeringPolicy createPolicy() {
        OnStartupTriggeringPolicy startupTriggeringPolicy = OnStartupTriggeringPolicy.createPolicy(0);

        SizeBasedTriggeringPolicy sizePolicy = SizeBasedTriggeringPolicy
                .createPolicy(SpeedRollingFileAppenderAnalyzer.ROLLING_FILE_SIZE);

        return CompositeTriggeringPolicy.createPolicy(startupTriggeringPolicy, sizePolicy);
    }

    public static DefaultRolloverStrategy createStrategy(Configuration configuration) {
        return DefaultRolloverStrategy
                .newBuilder()
                .withMax("1000")
                .withStopCustomActionsOnError(false)
                .withConfig(configuration)
                .build();
    }

    public static PatternLayout createPattern() {
        return PatternLayout
                .newBuilder()
                .withPattern(SpeedRollingFileAppenderAnalyzer.LAYOUT_PATTERN)
                .build();
    }

    public static Appender createAppender(String fileName, String filePatternName, Configuration configuration) {
        return RollingFileAppender
                .newBuilder()
                .setName(RollingFileAppender.PLUGIN_NAME)
                .withFileName(fileName)
                .withFilePattern(filePatternName)
                .withPolicy(RollingFileAppenderBuilder.createPolicy())
                .withStrategy(RollingFileAppenderBuilder.createStrategy(configuration))
                .setLayout(RollingFileAppenderBuilder.createPattern())
                .build();
    }

    public static AppenderRef[] createAppenderRefs() {
        AppenderRef ref = AppenderRef.createAppenderRef("STDOUT", Level.DEBUG, null);
        return new AppenderRef[] {ref};
    }
}
