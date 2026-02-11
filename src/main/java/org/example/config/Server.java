package org.example.config;

import java.util.List;

public class Server {
    private int listen;           // Port number (e.g., 80, 8080)
    private String serverName;    // Domain name (e.g., "example.com")
    private List<Location> locations;  // Routes/locations for this server


    Server() {
    }

    public int getListen() {
        return listen;
    }

    public void setListen(int listen) {
        this.listen = listen;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public List<Location> getLocations() {
        return locations;
    }

    public void setLocations(List<Location> locations) {
        this.locations = locations;
    }

    @Override
    public String toString() {
        return "Server{" +
                "listen=" + listen +
                ", serverName='" + serverName + '\'' +
                ", locations=" + locations +
                '}';
    }

}
