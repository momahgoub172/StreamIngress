package org.example.config;
/**
 * Represents a single backend server in an upstream pool
 */
public class BackendServer {
    private String host;      // Hostname or IP address
    private int port;         // Port number

    public BackendServer() {
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
    /**
     * Get the full URL for this backend server
     */
    public String getUrl() {
        return "http://" + host + ":" + port;
    }

    @Override
    public String toString() {
        return "BackendServer{" +
                "host='" + host + '\'' +
                ", port=" + port +
                '}';
    }
}
