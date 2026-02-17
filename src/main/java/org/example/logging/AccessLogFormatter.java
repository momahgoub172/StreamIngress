package org.example.logging;

import java.time.Instant;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class AccessLogFormatter extends Formatter {
    @Override
    public String format(LogRecord record) {
        String timestamp = Instant.ofEpochMilli(record.getMillis()).toString();
        // record.getMessage() already contains ip/method/path/status
        return "time=" + timestamp + " " + record.getMessage() + System.lineSeparator();
    }
}
