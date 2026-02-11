package org.example;

public class StatusLine {
    String HttpVersion;
    int StatusCode;
    String ReasonPhrase;

    public StatusLine(String httpVersion, int statusCode, String reasonPhrase) {
        this.HttpVersion = httpVersion;
        this.StatusCode = statusCode;
        this.ReasonPhrase = reasonPhrase;
    }
}
