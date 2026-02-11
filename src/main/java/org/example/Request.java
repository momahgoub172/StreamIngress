package org.example;

import java.util.Map;

public class Request {
    public RequestLine requestLine;
    public Map<String, String> headers;
    public String body;



    public Request(RequestLine requestLine, Map<String, String> headers, String body) {
        this.requestLine = requestLine;
        this.headers = headers;
        this.body = body;
    }
}
