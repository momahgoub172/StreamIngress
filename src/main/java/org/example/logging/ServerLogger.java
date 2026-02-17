package org.example.logging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerLogger {
    private static volatile boolean initialized = false;
    private static final Logger ACCESS_LOGGER = Logger.getLogger("server.access");
    private static final Logger ERROR_LOGGER  = Logger.getLogger("server.error");



    /**
     * Initialize loggers with file handlers.
     *
     * @param accessLogPath absolute or relative path to the access log file
     * @param errorLogPath  absolute or relative path to the error log file
     */
    public static synchronized void init(String accessLogPath, String errorLogPath) {
        if (initialized) {
            return;
        }
        Objects.requireNonNull(accessLogPath, "accessLogPath");
        Objects.requireNonNull(errorLogPath, "errorLogPath");

        try {
            configureLogger(ACCESS_LOGGER, accessLogPath, Level.INFO, new AccessLogFormatter());
            configureLogger(ERROR_LOGGER, errorLogPath, Level.SEVERE, new ErrorLogFormatter());
            initialized = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static void configureLogger(Logger logger,
                                        String filePath,
                                        Level level,
                                        Formatter formatter) throws IOException
    {

        logger.setUseParentHandlers(false); // avoid double logging to console

        // Ensure directory exists
        Path path = Paths.get(filePath);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        // Append mode, simple rotation by size/count can be added later if needed
        FileHandler handler = new FileHandler(filePath, true);
        handler.setFormatter(formatter);
        handler.setLevel(level);

        logger.setLevel(level);
        logger.addHandler(handler);
    }


    public static void logInfo(String msg) {
        ACCESS_LOGGER.info(msg);
    }

    /**
     * Log a successful or failed HTTP request.
     *
     * @param clientIp    remote IP address (may be null if unknown)
     * @param method      HTTP method (GET, POST, ...)
     * @param path        request path or target
     * @param statusCode  HTTP status code returned to the client
     */
    public static void logAccess(String clientIp,
                                 String method,
                                 String path,
                                 int statusCode) {

        if (!initialized) {
            return; // or throw, depending on how strict you want to be
        }

        String ip   = clientIp != null ? clientIp : "-";
        String m    = method   != null ? method   : "-";
        String p    = path     != null ? path     : "-";

        String msg = String.format(
                "ip=%s method=%s path=%s status=%d",
                ip, m, p, statusCode
        );

        ACCESS_LOGGER.info(msg);
    }


    /**
     * Log an error with as much request context as you have.
     *
     * @param clientIp remote IP address (may be null)
     * @param method   HTTP method (may be null if parsing failed)
     * @param path     request path or target (may be null if parsing failed)
     * @param t        exception to log
     */
    public static void logError(String clientIp,
                                String method,
                                String path,
                                Throwable t) {

        if (!initialized) {
            return;
        }

        String ip   = clientIp != null ? clientIp : "-";
        String m    = method   != null ? method   : "-";
        String p    = path     != null ? path     : "-";

        String msg = String.format(
                "ip=%s method=%s path=%s error=%s",
                ip, m, p, (t != null ? t.toString() : "-")
        );

        // SEVERE with throwable captures stack trace into the log file
        ERROR_LOGGER.log(Level.SEVERE, msg, t);
    }

    /**
     * Convenience overload when you don't have request context.
     */
    public static void logError(Throwable t) {
        logError(null, null, null, t);
    }
}
