package org.example.logging;


import java.time.Instant;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class ErrorLogFormatter extends Formatter {

    @Override
    public String format(LogRecord record) {
        StringBuilder sb = new StringBuilder();
        String timestamp = Instant.ofEpochMilli(record.getMillis()).toString();

        sb.append("time=").append(timestamp)
                .append(" level=").append(record.getLevel().getName())
                .append(" ").append(record.getMessage())
                .append(System.lineSeparator());

        Throwable t = record.getThrown();
        if (t != null) {
            // Include full stack trace
            sb.append(getStackTrace(t));
        }

        return sb.toString();
    }

    private String getStackTrace(Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t).append(System.lineSeparator());
        for (StackTraceElement element : t.getStackTrace()) {
            sb.append("\tat ").append(element).append(System.lineSeparator());
        }
        Throwable cause = t.getCause();
        while (cause != null) {
            sb.append("Caused by: ").append(cause).append(System.lineSeparator());
            for (StackTraceElement element : cause.getStackTrace()) {
                sb.append("\tat ").append(element).append(System.lineSeparator());
            }
            cause = cause.getCause();
        }
        return sb.toString();
    }
}
