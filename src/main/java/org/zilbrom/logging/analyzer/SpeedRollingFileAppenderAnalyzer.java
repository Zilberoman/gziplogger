package org.zilbrom.logging.analyzer;

import org.apache.logging.log4j.core.Logger;
import org.zilbrom.logging.analyzer.runners.GZipRollingFileAppenderRunner;
import org.zilbrom.logging.analyzer.runners.RollingFileAppenderRunner;
import org.zilbrom.logging.analyzer.runners.RollingFileAppenderWithGZipRunner;

import java.util.Random;

public class SpeedRollingFileAppenderAnalyzer {
    public static final String LAYOUT_PATTERN =
            "%-5p %d{dd-MM-yyyy HH:mm:ss,SSS} (%c{3})%X{txnInfo} - %notEmpty{%marker: }%m%n";
    public static final  String ROLLING_FILE_SIZE = "50MB";
    private static final long DEFAULT_CYCLE_SIZE = 10000000L;
    private static final Random random = new Random();

    public static void main(String[] args) {
        long cycleSize;

        try {
            cycleSize = args[0] == null ? DEFAULT_CYCLE_SIZE : Long.parseLong(args[0]);
        } catch (Exception e) {
            cycleSize = DEFAULT_CYCLE_SIZE;
        }

        //RollingFileAppender
//        RollingFileAppenderRunner.run(cycleSize);

        //RollingFileAppender with gzip
//        RollingFileAppenderWithGZipRunner.run(cycleSize);

        //GZipRollingFileAppender
        GZipRollingFileAppenderRunner.run(cycleSize);
    }

    public static void  runCycle(Logger logger, long cycleSize) {
        for (long i = 0; i < cycleSize; i++) {
            logger.debug("Line number: " + i + " random value is " + random.nextLong());
            logger.info("Line number: " + i + " random value is " + random.nextLong());
            logger.error("Line number: " + i + " random value is " + random.nextLong());
            logger.warn("Line number: " + i + " random value is " + random.nextLong());
        }
    }
}
