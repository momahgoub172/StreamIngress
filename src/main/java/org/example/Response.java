package org.example;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

public class Response {
    StatusLine statusLine;
    Map<String, String> headers;
    String body;

    public Response(StatusLine statusLine, Map<String, String> headers, String body) {
        this.statusLine = statusLine;
        this.headers = headers;
        this.body = body;
    }

    public void send(PrintWriter out) {
        // 1. Send status line
        out.print(statusLine.HttpVersion + " " + statusLine.StatusCode + " " + statusLine.ReasonPhrase + "\r\n");

        // 2. Send headers
        for (Map.Entry<String, String> header : headers.entrySet()) {
            out.print(header.getKey() + ": " + header.getValue() + "\r\n");
        }

        // 3. Send empty line (separator between headers and body)
        out.print("\r\n");

        // 4. Send body (if exists)
        if (body != null && !body.isEmpty()) {
            out.print(body);
        }

        // Flush to ensure everything is sent
        out.flush();
    }


    public static Response ok(String body, String contentType) {
        StatusLine statusLine = new StatusLine("HTTP/1.1", 200, "OK");
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", contentType);
        headers.put("Content-Length", String.valueOf(body.getBytes().length));
        return new Response(statusLine, headers, body);
    }

    public static Response notFound(String body) {
        StatusLine statusLine = new StatusLine("HTTP/1.1", 404, "Not Found");
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/html");
        headers.put("Content-Length", String.valueOf(body.getBytes().length));
        return new Response(statusLine, headers, body);
    }

    public static Response badRequest(String body) {
        StatusLine statusLine = new StatusLine("HTTP/1.1", 400, "Bad Request");
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/plain");
        headers.put("Content-Length", String.valueOf(body.getBytes().length));
        return new Response(statusLine, headers, body);
    }

    public static Response created(String body, String contentType) {
        StatusLine statusLine = new StatusLine("HTTP/1.1", 201, "Created");
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", contentType);
        headers.put("Content-Length", String.valueOf(body.getBytes().length));
        return new Response(statusLine, headers, body);
    }

    public static Response internalServerError(String body) {
        StatusLine statusLine = new StatusLine("HTTP/1.1", 500, "Internal Server Error");
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/plain");
        headers.put("Content-Length", String.valueOf(body.getBytes().length));
        return new Response(statusLine, headers, body);
    }

    public static Response noContent() {
        StatusLine statusLine = new StatusLine("HTTP/1.1", 204, "No Content");
        Map<String, String> headers = new HashMap<>();
        return new Response(statusLine, headers, "");
    }
}
