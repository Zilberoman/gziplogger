package org.zilbrom.logging.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zilbrom.logging.streams.RollingGZIPOutputStream;

import java.io.FileOutputStream;
import java.io.IOException;

public class GZipAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(GZipAnalyzer.class);
    private static final long DEFAULT_CYCLE_SIZE = 10000000L;

    public static void main(String[] args) {
        long cycleSize;
        RollingGZIPOutputStream outputStream;
        FileOutputStream fileStream;

        if (args.length == 0) {
            cycleSize = DEFAULT_CYCLE_SIZE;
        } else {
            try {
                cycleSize = Long.parseLong(args[0]);
            } catch (NumberFormatException numberFormatException) {
                cycleSize = DEFAULT_CYCLE_SIZE;
            }
        }

        try {
            fileStream = new FileOutputStream("direct.gz");
            outputStream = new RollingGZIPOutputStream(fileStream, 10000, 1000, true);
        } catch (IOException ioException) {
            ioException.printStackTrace();
            return;
        }

        for (long index = 0; index < cycleSize; index++) {
            String outputString = "\t - line number : " + index;
            log.info(outputString);
            outputString += '\n';

            try {
                outputStream.write(outputString.getBytes());
                outputStream.flush();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }

        try {
            outputStream.close();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }
}
