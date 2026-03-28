package org.example.handlers;

import org.example.config.Location;
import org.example.logging.ServerLogger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class StaticFileHandler {
    // MIME type mapping
    private static final Map<String, String> MIME_TYPES = new HashMap<>();

    static {
        //TODO move to mime config file called mime.yaml
        // Text
        MIME_TYPES.put("html", "text/html");
        MIME_TYPES.put("htm", "text/html");
        MIME_TYPES.put("css", "text/css");
        MIME_TYPES.put("js", "application/javascript");
        MIME_TYPES.put("json", "application/json");
        MIME_TYPES.put("xml", "application/xml");
        MIME_TYPES.put("txt", "text/plain");

        // Images
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("gif", "image/gif");
        MIME_TYPES.put("svg", "image/svg+xml");
        MIME_TYPES.put("ico", "image/x-icon");
        MIME_TYPES.put("webp", "image/webp");

        // Fonts
        MIME_TYPES.put("woff", "font/woff");
        MIME_TYPES.put("woff2", "font/woff2");
        MIME_TYPES.put("ttf", "font/ttf");
        MIME_TYPES.put("otf", "font/otf");

        // Documents
        MIME_TYPES.put("pdf", "application/pdf");
        MIME_TYPES.put("zip", "application/zip");
        MIME_TYPES.put("tar", "application/x-tar");
        MIME_TYPES.put("gz", "application/gzip");
    }

    /**
     * Handle a static file request
     * @param shouldClose 
     */
    public static void handle(Location location,
                              String requestPath,
                              String method,
                              PrintWriter out,
                              OutputStream binaryOut,
                              String clientIp,
                              Map<String, String> headers, 
                              boolean shouldClose) throws IOException {

        boolean isHeadRequest = method.equalsIgnoreCase("HEAD");

        File file = resolveFile(location, requestPath);
        if (!isPathSafe(location.getRoot(), file)) {
            ServerLogger.logAccess(clientIp, method, requestPath, 403);
            ServerLogger.logError(clientIp, method, requestPath,
                    new SecurityException("Forbidden static file access: " + file.getPath()));
            sendError(out, 403, "Forbidden");
            return;
        }

        if (!file.exists()) {
            ServerLogger.logAccess(clientIp, method, requestPath, 404);
            sendError(out, 404, "Not Found");
            return;
        }

        // Handle directories
        if (file.isDirectory()) {
            handleDirectory(location, file, requestPath, method, out, binaryOut, clientIp, isHeadRequest, headers, shouldClose);
            return;
        }

        // Serve file
        serveFile(file, out, binaryOut, isHeadRequest, headers, shouldClose);
        ServerLogger.logAccess(clientIp, method, requestPath, 200);
    }

    /**
     * Handle directory requests
     */
    private static void handleDirectory(Location location,
                                        File directory,
                                        String requestPath,
                                        String method,
                                        PrintWriter out,
                                        OutputStream binaryOut,
                                        String clientIp,
                                        boolean isHeadRequest,
                                        Map<String, String> headers,
                                        boolean shouldClose) throws IOException {
        // Serve index file if exists
        // Try to serve index file
        if (location.getIndex() != null && !location.getIndex().isEmpty()) {
            File indexFile = new File(directory, location.getIndex());
            if (indexFile.exists() && indexFile.isFile()) {
                serveFile(indexFile, out, binaryOut, isHeadRequest, headers, shouldClose);
                ServerLogger.logAccess(clientIp, method, requestPath, 200);
                return;
            }
        }
        // Show directory listing if enabled
        if (location.isDirectoryListingEnabled()) {
            sendDirectoryListing(directory, out, isHeadRequest, headers, shouldClose);
            ServerLogger.logAccess(clientIp, method, requestPath, 200);
        } else {
            sendError(out, 403, "Forbidden");
            ServerLogger.logAccess(clientIp, method, requestPath, 403);
        }
    }

    private static void sendDirectoryListing(File directory, PrintWriter out, boolean isHeadRequest, Map<String, String> headers, boolean shouldClose) throws IOException {


        // Check 'If-Modified-Since' header and handle conditional-get for 304 Not Modified
        String ifModifiedSince = headers.get("If-Modified-Since");
        if (ifModifiedSince != null) {
            try {
                // HTTP/1.1 date format: "EEE, dd MMM yyyy HH:mm:ss zzz"
                DateTimeFormatter formatter = DateTimeFormatter.RFC_1123_DATE_TIME;
                // Parse the header date as an Instant
                ZonedDateTime clientDate = ZonedDateTime.parse(ifModifiedSince, formatter);
                FileTime fileTime = Files.getLastModifiedTime(directory.toPath());
                long fileLastModifiedMillis = fileTime.toMillis();
                long clientMillis = clientDate.toInstant().toEpochMilli();

                // RFC says: If the requested resource has not been modified since the time specified in this field, return 304.
                // 304 responses must NOT include a message body.
                if (fileLastModifiedMillis / 1000 <= clientMillis / 1000) {
                    out.print("HTTP/1.1 304 Not Modified\r\n");
                    out.print("Date: " + formatter.format(ZonedDateTime.now(ZoneId.of("GMT"))) + "\r\n");
                    out.print("Last-Modified: " + formatHttpDate(directory.lastModified()) + "\r\n");
                    out.print(connectionHeader(shouldClose));
                    out.print("\r\n");
                    out.flush();
                    ServerLogger.logAccess(headers.get("Client-IP"), headers.get("Method"), headers.get("Request-Target"), 304);
                    return;
                }
            } catch (Exception e) {
                ServerLogger.logError(headers.get("Client-IP"), headers.get("Method"), headers.get("Request-Target"), e);
                ServerLogger.logAccess(headers.get("Client-IP"), headers.get("Method"), headers.get("Request-Target"), 500);
                sendError(out, 500, "Internal Server Error");
                return;
            }
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n<head>\n");
        html.append("<title>Index of ").append(directory.getName()).append("</title>\n");
        html.append("<style>body{font-family:monospace;margin:40px;}a{display:block;padding:5px;}</style>\n");
        html.append("</head>\n<body>\n");
        html.append("<h1>Index of /").append(directory.getName()).append("/</h1>\n");
        html.append("<hr>\n<ul>\n");

        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                html.append("<li><a href=\"").append(file.getName()).append("/\">").append(file.getName()).append("/</a></li>\n");
            } else {
                long size = file.length();
                html.append("<li><a href=\"").append(file.getName()).append("\">").append(file.getName());
                if (size > 0) {
                    html.append(" (").append(formatFileSize(size)).append(" bytes)");
                }
                html.append("</a></li>\n");
            }
        }
        html.append("</ul>\n");
        html.append("</body>\n</html>\n");
        byte[] content = html.toString().getBytes();
        out.print("HTTP/1.1 200 OK\r\n");
        out.print("Content-Type: text/html\r\n");
        out.print("Content-Length: " + content.length + "\r\n");
        out.print("Last-Modified: " + formatHttpDate(directory.lastModified()) + "\r\n");
        out.print(connectionHeader(shouldClose));
        out.print("\r\n");
        if (!isHeadRequest) {
            out.print(html);
        }
        out.flush();
    }

    /**
     * Format file size for display
     */
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }

    private static void serveFile(File file, PrintWriter out, OutputStream binaryOut, boolean isHeadRequest, Map<String, String> headers, boolean shouldClose) throws IOException {

        //check last modified header// Check 'If-Modified-Since' header and handle conditional-get for 304 Not Modified
        String ifModifiedSince = headers.get("If-Modified-Since");
        if (ifModifiedSince != null) {
            try {
                // HTTP/1.1 date format: "EEE, dd MMM yyyy HH:mm:ss zzz"
                DateTimeFormatter formatter = DateTimeFormatter.RFC_1123_DATE_TIME;
                // Parse the header date as an Instant
                ZonedDateTime clientDate = ZonedDateTime.parse(ifModifiedSince, formatter);
                FileTime fileTime = Files.getLastModifiedTime(file.toPath());
                long fileLastModifiedMillis = fileTime.toMillis();
                long clientMillis = clientDate.toInstant().toEpochMilli();

                // RFC says: If the requested resource has not been modified since the time specified in this field, return 304
                if (fileLastModifiedMillis / 1000 <= clientMillis / 1000) {
                    out.print("HTTP/1.1 304 Not Modified\r\n");
                    out.print("Date: " + formatter.format(ZonedDateTime.now(ZoneId.of("GMT"))) + "\r\n");
                    out.print(connectionHeader(shouldClose));
                    out.print("\r\n");
                    out.flush();
                    ServerLogger.logAccess(headers.get("Client-IP"),headers.get("Method"),headers.get("Request-Target"), 304);
                    return;
                }
            } catch (Exception e) {
                ServerLogger.logError(headers.get("Client-IP"), headers.get("Method"), headers.get("Request-Target"), e);
                ServerLogger.logAccess(headers.get("Client-IP"), headers.get("Method"), headers.get("Request-Target"), 500);
                sendError(out, 500, "Internal Server Error");
                return;
            }
        }

        String mimeType = getMimeType(file.getName());
        long fileSize = file.length();
        long lastModified = file.lastModified();
        // Send response headers
        out.print("HTTP/1.1 200 OK\r\n");
        out.print("Content-Type: " + mimeType + "\r\n");
        out.print("Content-Length: " + fileSize + "\r\n");
        out.print("Last-Modified: " + formatHttpDate(lastModified) + "\r\n");
        out.print(connectionHeader(shouldClose));
        out.print("Cache-Control: max-age=3600\r\n");
        out.print("\r\n");
        out.flush();


        if (!isHeadRequest) {
            // Send file content
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[65536]; //TODO : Make this configurable and investigate why it is 8192
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    binaryOut.write(buffer, 0, bytesRead);
                }
                binaryOut.flush();
            }
            System.out.println("Served: " + file.getPath() + " (" + fileSize + " bytes)");
        }
    }

    /**
     * Format date for HTTP headers
     */
    private static String formatHttpDate(long timestamp) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
        return dateFormat.format(new Date(timestamp));
    }

    /**
     * Get MIME type from file extension
     */
    private static String getMimeType(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            String extension = filename.substring(dotIndex + 1).toLowerCase();
            return MIME_TYPES.getOrDefault(extension, "application/octet-stream");
        }
        return "application/octet-stream";
    }

    /**
     * Send error response
     */
    private static void sendError (PrintWriter out,int statusCode, String message){
        String html = "<html><body><h1>" + statusCode + " " + message + "</h1></body></html>";
        byte[] content = html.getBytes();

        out.print("HTTP/1.1 " + statusCode + " " + message + "\r\n");
        out.print("Content-Type: text/html\r\n");
        out.print("Content-Length: " + content.length + "\r\n");
        out.print("\r\n");
        out.print(html);
        out.flush();
    }

    /**
     * Resolve file path safely
     */
    private static File resolveFile (Location location, String relativePath) throws IOException {
        File root = new File(location.getRoot()).getCanonicalFile();
        return new File(root, relativePath).getCanonicalFile();
    }

    /**
     * Security: Check if the resolved path is inside the root directory
     */
    private static boolean isPathSafe (String rootPath, File resolvedFile) throws IOException {
        File root = new File(rootPath).getCanonicalFile();
        String canonicalPath = resolvedFile.getCanonicalPath();
        String rootPathCanonical = root.getCanonicalPath();
        return canonicalPath.startsWith(rootPathCanonical);
    }


    private static String connectionHeader(boolean shouldClose) {
        return "Connection: " + (shouldClose ? "close" : "keep-alive") + "\r\n";
    }

}