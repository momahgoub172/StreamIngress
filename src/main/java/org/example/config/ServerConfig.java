package org.example.config;

import java.util.List;

public class ServerConfig {
    // Global settings
    private String accessLog;
    private String errorLog;

    // List of server blocks (each server listens on a port)
    private List<Server> servers;


    public String getAccessLog() {
        return accessLog;
    }

    public void setAccessLog(String accessLog) {
        this.accessLog = accessLog;
    }

    public String getErrorLog() {
        return errorLog;
    }

    public void setErrorLog(String errorLog) {
        this.errorLog = errorLog;
    }

    public List<Server> getServers() {
        return servers;
    }

    public void setServers(List<Server> servers) {
        this.servers = servers;
    }
}
