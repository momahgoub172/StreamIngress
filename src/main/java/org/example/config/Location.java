package org.example.config;


/**
 * Represents a location (route) within a server
 * Defines how to handle requests matching a specific path
 */
public class Location {
    private String path; // URL path prefix (e.g., "/", "/api", "/images")
    private String type; // "static" or "proxy"
    private String root; // Root directory for static content
    private String index; // Default index file for static content


    Location() {}


    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public boolean isStatic() {
        return "static".equalsIgnoreCase(type);
    }

    public boolean isProxy() {
        return "proxy".equalsIgnoreCase(type);
    }

    @Override
    public String toString() {
        return "Location{" +
                "path='" + path + '\'' +
                ", type='" + type + '\'' +
                ", root='" + root + '\'' +
                ", index='" + index + '\'' +
                '}';
    }

}
