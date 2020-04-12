package org.zilbrom.logging.analyzer;

import org.zilbrom.logging.analyzer.runners.AsyncGZipRollingFileAppenderRunner;

public class MultyThreadAnalyzer {
    private static final byte DEFAULT_NUMBER_OF_THREADS = 4;

    public static void main(String[] args) {
        byte threadNumbers;

        if (args.length == 0) {
            threadNumbers = DEFAULT_NUMBER_OF_THREADS;
        } else {
            try {
                threadNumbers = Byte.parseByte(args[1]);
            } catch (NumberFormatException nfe) {
                threadNumbers = DEFAULT_NUMBER_OF_THREADS;
            }
        }

        AsyncGZipRollingFileAppenderRunner.run(threadNumbers);
    }
}
