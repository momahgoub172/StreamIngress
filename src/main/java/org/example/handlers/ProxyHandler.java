package org.example.handlers;


import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.example.config.Location;
import org.example.health.HealthCheckRegistry;
import org.example.logging.ServerLogger;


public class ProxyHandler {
    // Timeout settings
    private static final int CONNECT_TIMEOUT = 5000;  // 5 seconds
    private static final int READ_TIMEOUT = 30000;    // 30 seconds

    /**
     * Forward request to backend server
     *
     * @param location    The location configuration
     * @param requestPath The original request path
     * @param method      HTTP method (GET, POST, etc.)
     * @param headers     Request headers from client
     * @param body        Request body (for POST/PUT/PATCH)
     * @param out         PrintWriter for response headers
     * @param binaryOut   OutputStream for response body
     * @param clientIp    Client IP address for X-Forwarded-For
     */
    public static void handle(Location location, String requestPath, String method,
                              Map<String, String> headers, String body,
                              PrintWriter out, OutputStream binaryOut, String clientIp) throws IOException {

        //Check health of backend server
        if (!HealthCheckRegistry.isHealthy(location.getProxyUrl())) {
            ServerLogger.logAccess(clientIp, method, requestPath, 503);
            ServerLogger.logError(clientIp, method, requestPath,
                    new IllegalStateException("Backend not healthy: " + location.getProxyUrl()));
            sendError(out, 503, "Service Unavailable", "The backend server is not available.");
            return;
        }


        String backendUrl = buildBackendUrl(location.getProxyUrl(), requestPath, location.getPath());
        ServerLogger.logInfo("[ProxyHandler] Proxying: " + method + " " + requestPath + " → " + backendUrl);

        HttpURLConnection connection = null;
        try {
            // Create connection to backend
            URL url = URI.create(backendUrl).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setDoInput(true);

            // Set timeouts to prevent hanging
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);

            // Forward headers (with filtering and additions)
            forwardHeaders(connection, headers, location.getProxyUrl(), clientIp);
            // Forward body if present (for POST, PUT, PATCH)
            if (body != null && !body.isEmpty() &&
                    (method.equals("POST") || method.equals("PUT") || method.equals("PATCH"))) {
                connection.setDoOutput(true);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body.getBytes());
                    os.flush();
                }
            }

            // Get response from backend after processing request
            int statusCode = connection.getResponseCode();
            String statusMessage = connection.getResponseMessage();
            ServerLogger.logInfo("[ProxyHandler] Backend responded: " + statusCode + " " + statusMessage);

            // Send response status
            out.print("HTTP/1.1 " + statusCode + " " + statusMessage + "\r\n");
            // Forward response headers
            Map<String, List<String>> responseHeaders = connection.getHeaderFields();
            for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
                String headerName = entry.getKey();
                if (headerName != null && !headerName.equalsIgnoreCase("Transfer-Encoding")) {
                    for (String headerValue : entry.getValue()) {
                        out.print(headerName + ": " + headerValue + "\r\n");
                    }
                }
            }
            out.print("\r\n");
            out.flush();

            // Forward response body
            InputStream responseStream;
            if (statusCode >= 400) {
                responseStream = connection.getErrorStream();
            } else {
                responseStream = connection.getInputStream();
            }

            if (responseStream != null) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = responseStream.read(buffer)) != -1) {
                    binaryOut.write(buffer, 0, bytesRead);
                }
                binaryOut.flush();
                //responseStream.close();
            }

            ServerLogger.logAccess(clientIp, method, requestPath, statusCode);

        } catch (SocketTimeoutException e) {
            ServerLogger.logError(clientIp, method, requestPath, e);
            ServerLogger.logAccess(clientIp, method, requestPath, 504);
            sendError(out, 504, "Gateway Timeout",
                    "The backend server did not respond in time.");
        } catch (IOException e) {
            ServerLogger.logError(clientIp, method, requestPath, e);
            ServerLogger.logAccess(clientIp, method, requestPath, 502);
            sendError(out, 502, "Bad Gateway",
                    "Could not connect to backend server: " + location.getProxyUrl());
        }
    }


    private static void forwardHeaders(HttpURLConnection connection, Map<String, String> headers, String proxyTarget, String clientIp) throws IOException {
        String host = extractHost(proxyTarget);
        connection.setRequestProperty("Host", host);
        // Add X-Forwarded headers
        //Records the real client IP address and the entire proxy chain it passed through
        connection.setRequestProperty("X-Forwarded-For", clientIp);
        connection.setRequestProperty("X-Forwarded-Protocol", "http");

        // Forward other headers (skip hop-by-hop headers)
        for (Map.Entry<String, String> header : headers.entrySet()) {
            String key = header.getKey();
            String value = header.getValue();
            if (isHopByHopHeader(key)) {
                continue;
            }
            // Don't override headers we already set
            if (key.equalsIgnoreCase("Host") ||
                    key.startsWith("X-Forwarded-")) {
                continue;
            }
            connection.setRequestProperty(key, value);
        }

    }

    private static boolean isHopByHopHeader(String key) {
        String lower = key.toLowerCase();
        return lower.equals("connection") ||
                lower.equals("keep-alive") ||
                lower.equals("proxy-connection") ||
                lower.equals("proxy-authenticate") ||
                lower.equals("proxy-authorization") ||
                lower.equals("transfer-encoding") ||
                lower.equals("te") ||
                lower.equals("trailer") ||
                lower.equals("upgrade");
    }

    /**
     * Extract host:port from URL
     */
    private static String extractHost(String url) {
        try {
            URL u = URI.create(url).toURL();
            if (u.getPort() != -1) {
                return u.getHost() + ":" + u.getPort();
            }
            return u.getHost();
        } catch (Exception e) {
            return "localhost";
        }
    }

    /**
     * Build the target URL for the backend
     * Strips the location prefix from the request path
     */
    private static String buildBackendUrl(String proxyUrl, String requestPath, String path) {
        String pathAfterLocation = requestPath;
        if (pathAfterLocation.startsWith(path)) {
            //api/users/123 --> /users/123
            pathAfterLocation = pathAfterLocation.substring(path.length());
        }
        if (!pathAfterLocation.startsWith("/")) {
            // users/123 --> /users/123
            pathAfterLocation = "/" + pathAfterLocation;
        }
        // Remove trailing slash from proxy target
        String target = proxyUrl;
        if (target.endsWith("/")) {
            target = target.substring(0, target.length() - 1); // remove trailing slash (last character)
        }
        return target + pathAfterLocation;
    }


    /**
     * Send error response to client
     */
    private static void sendError(PrintWriter out, int statusCode, String message, String details) {
        String html = "<!DOCTYPE html>" +
                "<html><head><title>" + statusCode + " " + message + "</title>" +
                "<style>" +
                "body { font-family: sans-serif; text-align: center; padding: 50px; background: #f5f5f5; }" +
                "h1 { color: #e74c3c; }" +
                "p { color: #666; }" +
                ".error-code { font-size: 72px; color: #e74c3c; margin: 0; }" +
                "</style></head>" +
                "<body>" +
                "<div class='error-code'>" + statusCode + "</div>" +
                "<h1>" + message + "</h1>" +
                "<p>" + details + "</p>" +
                "</body></html>";

        byte[] content = html.getBytes();

        out.print("HTTP/1.1 " + statusCode + " " + message + "\r\n");
        out.print("Content-Type: text/html; charset=UTF-8\r\n");
        out.print("Content-Length: " + content.length + "\r\n");
        out.print("Connection: close\r\n");
        out.print("\r\n");
        out.print(html);
        out.flush();
    }

}
