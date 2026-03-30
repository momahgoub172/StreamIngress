package org.example;

import org.example.config.ConfigFileProcessor;
import org.example.config.Location;
import org.example.config.Server;
import org.example.config.ServerConfig;
import org.example.handlers.ProxyHandler;
import org.example.handlers.StaticFileHandler;
import org.example.logging.ServerLogger;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

//TODO : Replace Jacson --> Implement DSL for configuration and implement Lexer, Parser, AST .........
//TODO : Refactor Main.java

public class Main {
    public static void main(String[] args) throws Exception {

        ServerConfig config = ConfigFileProcessor
                .load("/home/goba/programming/projects/java/StreamIngress/src/main/java/org/example/config.yaml");
        Server server = config.getServers().getFirst();
        int port = server.getListen();

        ServerLogger.init(config.getAccessLog(), config.getErrorLog());
        ServerLogger.logInfo("[Main] Server starting on port " + port);

        // ServerSocketChannel yields SocketChannel on accept so static files can use
        // FileChannel.transferTo (zero-copy / sendfile on Linux).
        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
            serverChannel.bind(new InetSocketAddress(port));
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                while (true) {
                    SocketChannel clientChannel = serverChannel.accept();
                    executor.submit(() -> {
                        Socket socket = clientChannel.socket();
                        try (clientChannel) {
                            // TODO: Make this configurable
                            socket.setSoTimeout(30_000);

                            String clientIp = socket.getInetAddress().getHostAddress();
                            ServerLogger.logInfo("[Main] Client connected from " + clientIp);

                            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                            OutputStream rawOut = socket.getOutputStream();
                            PrintWriter out = new PrintWriter(new BufferedOutputStream(rawOut), true);


                            //TODO : make it configurable
                            var maxRequests = 100;  // prevent one client hogging the connection forever
                            var requestsCount = 0;

                            while (requestsCount < maxRequests) {

                                String method = null;
                                String requestPath = null;
                                boolean shouldClose = true;
                                try {
                                    Request request = parseRequest(in);
                                    if (request == null) {
                                        System.out.println("[debug] null request, closing connection");
                                        break;
                                    }
                                    System.out.println("[debug] got request #" + requestsCount + " " + request.requestLine.Method + " " + request.requestLine.RequestTarget);
                                    method = request.requestLine.Method;
                                    requestPath = request.requestLine.RequestTarget;
                                    int q = requestPath.indexOf('?');
                                    if (q >= 0)
                                        requestPath = requestPath.substring(0, q);

                                    shouldClose = shouldCloseConnection(request);
                                    requestsCount++;

                                    Location location = findMatchingLocation(server.getLocations(), requestPath);
                                    if (location != null && location.isStatic()) {
                                        StaticFileHandler.handle(location, requestPath, method, out, rawOut,
                                                clientChannel, clientIp,
                                                request.headers, shouldClose);
                                    } else if (location != null && location.isProxy()) {
                                        ProxyHandler.handle(location, requestPath, method,
                                                request.headers, request.body, out, rawOut, clientIp,shouldClose);
                                    }
                                    else {
                                        // no matching location → 404
                                        sendNotFound(out, shouldClose);
                                    }
                                }
                                catch (java.net.SocketTimeoutException e) {
                                    break; // idle timeout, close quietly
                                } 
                                catch (Exception e) {
                                    ServerLogger.logError(clientIp, method, requestPath, e);
                                    ServerLogger.logAccess(clientIp, method, requestPath, 500);
                                    Response response = Response.internalServerError("Internal Server Error");
                                    response.send(out);
                                    break;
                                }

                            }

                        } catch (IOException e) {
                            ServerLogger.logError(null, null, null, e);
                        }
                    });
                }
            }
        }
    }


    private static void sendNotFound(PrintWriter out, boolean shouldClose) {
        String html = "<html><body><h1>404 Not Found</h1></body></html>";
        out.print("HTTP/1.1 404 Not Found\r\n");
        out.print("Content-Type: text/html\r\n");
        out.print("Content-Length: " + html.getBytes().length + "\r\n");
        out.print("Connection: " + (shouldClose ? "close" : "keep-alive") + "\r\n");
        out.print("\r\n");
        out.print(html);
        out.flush();
    }

    /**
     * Find the location that best matches the request path (longest path prefix
     * wins).
     */
    private static Location findMatchingLocation(List<Location> locations, String requestPath) {
        Location best = null;
        int bestLen = -1;
        for (Location loc : locations) {
            String path = loc.getPath();
            if (path == null)
                continue;
            if (!requestPath.startsWith(path))
                continue;
            // Require exact match or path boundary (e.g. "/" matches "/", "/images" matches
            // "/images" and "/images/...")
            if (path.length() > bestLen && (path.equals("/") || path.length() == requestPath.length()
                    || requestPath.charAt(path.length()) == '/')) {
                best = loc;
                bestLen = path.length();
            }
        }
        return best;
    }

    public static Request parseRequest(BufferedReader in) throws Exception {
        // Step 1: Read the request line (first line)
        String firstLine = in.readLine();
        if (firstLine == null || firstLine.isEmpty()) {
            return null; // client closed or idle — was: throw new Exception(...)
        }

        String[] requestLineParts = firstLine.split(" ");
        if (requestLineParts.length != 3) {
            throw new Exception("Invalid HTTP request line format");
        }

        RequestLine requestLine = new RequestLine();
        requestLine.Method = requestLineParts[0];
        requestLine.RequestTarget = requestLineParts[1];
        requestLine.HttpVersion = requestLineParts[2];

        // Step 2: Read headers until we hit an empty line
        Map<String, String> headersMap = new HashMap<>();
        String line;
        while ((line = in.readLine()) != null) {
            // Empty line indicates end of headers
            if (line.isEmpty()) {
                break;
            }

            // Parse header (format: "Key: Value")
            int colonIndex = line.indexOf(":");
            if (colonIndex > 0) {
                String headerName = line.substring(0, colonIndex).trim();
                String headerValue = line.substring(colonIndex + 1).trim();
                headersMap.put(headerName, headerValue);
            }
        }

        // Step 3: Read body if Content-Length is present
        String body = "";
        if (headersMap.containsKey("Content-Length")) {
            int contentLength = Integer.parseInt(headersMap.get("Content-Length"));
            if (contentLength > 0) {
                char[] bodyChars = new char[contentLength];
                int totalRead = 0;

                // Read exactly contentLength characters
                while (totalRead < contentLength) {
                    int read = in.read(bodyChars, totalRead, contentLength - totalRead);
                    if (read == -1) {
                        break; // End of stream
                    }
                    totalRead += read;
                }

                body = new String(bodyChars, 0, totalRead);
            }
        }

        return new Request(requestLine, headersMap, body);
    }

    private static boolean shouldCloseConnection(Request request) {
        var httpVersion = request.requestLine.HttpVersion;
        var connection = request.headers.getOrDefault("Connection", "").toLowerCase();
        if (httpVersion.equals("HTTP/1.1")) {
            // HTTP/1.1 defaults to keep-alive unless client says close
            return connection.equals("close");
        } else {
            // HTTP/1.0 defaults to close unless client says keep-alive
            return !connection.contains("keep-alive");
        }

    }
}