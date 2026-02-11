package org.example.config;


/**
 * Custom exception for configuration errors
 */
public class ConfigException extends RuntimeException {
    public ConfigException(String message) {
        super(message);
    }
}
