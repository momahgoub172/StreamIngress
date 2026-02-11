package org.example;

import org.example.config.ConfigFileProcessor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;


//TODO : Replace Jacson --> Implement DSL for configuration and implement Lexer, Parser, AST .........
//TODO :



public class Main {
    public static void main(String[] args) throws Exception {

        ConfigFileProcessor.load("C:\\Users\\Mmahgoub\\IdeaProjects\\HttpFromTcp\\src\\main\\java\\org\\example\\config.yaml");


        ServerSocket serverSocket = new ServerSocket(1010);

        while (true) {
            Socket socket = serverSocket.accept();
            System.out.println("Client connected");

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // Parse the entire request
            Request request = parseRequest(in);

            if (request.requestLine.Method.equals("GET") && request.requestLine.RequestTarget.equals("/")) {
                // Serve static files
                //serveStaticFile(request, out);
            }

            Response response = Response.ok("Hello, World! from server", "text/plain");
            response.send(out);
            // Close connection
            socket.close();
        }
    }

    public static Request parseRequest(BufferedReader in) throws Exception {
        // Step 1: Read the request line (first line)
        String firstLine = in.readLine();
        if (firstLine == null || firstLine.isEmpty()) {
            throw new Exception("Invalid HTTP request: empty request line");
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
}