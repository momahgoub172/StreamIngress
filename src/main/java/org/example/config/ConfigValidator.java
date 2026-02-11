package org.example.config;

/**
 * Validates the loaded configuration to ensure it's correct and complete
 */
public class ConfigValidator {
    /**
     * Validate the entire server configuration
     * Throws ConfigException if validation fails
     */
    public static void validate(ServerConfig config) throws ConfigException {
        System.out.println("Validation Complete");
        if(config == null) {
            throw new ConfigException("Configuration is null");
        }

        //Validate access log
        if(config.getAccessLog() == null || config.getAccessLog().isEmpty()) {
            throw new ConfigException("Access log not found in configuration");
        }
        //Validate error log
        if(config.getErrorLog() == null || config.getErrorLog().isEmpty()) {
            throw new ConfigException("Error log not found in configuration");
        }
        //Validate servers
        if(config.getServers() == null || config.getServers().isEmpty()) {
            throw new ConfigException("No servers found in configuration");
        }

        for(Server server : config.getServers()) {
            validateServer(server, config);
        }

    }

    private static void validateServer(Server server , ServerConfig config) throws ConfigException {
        if(server.getListen() < 1 || server.getListen() > 65535) {
            throw new ConfigException("Invalid port number for server: " + server.getListen());
        }

        //Validate locations
        if (server.getLocations() == null || server.getLocations().isEmpty()) {
            throw new ConfigException("No locations found for server: " + server.getListen());
        }

        for(Location location : server.getLocations()) {
            validateLocation(location,server ,config);
        }


    }

    private static void validateLocation(Location location,Server server ,ServerConfig config) {
        if (location.getPath() == null || location.getPath().isEmpty()) {
            throw new ConfigException("No path found for location: " + location.getPath());
        }

        if (location.getType() == null || location.getType().isEmpty()) {
            throw new ConfigException("No type found for location: " + location.getPath());
        }

        if (!location.isStatic() && !location.isProxy()) {
            throw new ConfigException("Invalid type for location: " + location.getPath());
        }

        if(location.isStatic()){
            ValidateStaticLocation(location,server ,config);
        }
        else{
            //TODO : implemented after support proxy
            System.out.println("ValidateProxyLocation");
            //ValidateProxyLocation(location,server ,config);
        }
    }

    private static void ValidateStaticLocation(Location location, Server server, ServerConfig config) throws ConfigException {
        if (location.getRoot() == null || location.getRoot().isEmpty()) {
            throw new ConfigException("No root found for location: " + location.getPath());
        }
        java.io.File file = new java.io.File(location.getRoot());
        if (!file.exists()) {
            throw new ConfigException("Root directory for location: " + location.getPath() + " does not exist");
        }
        if(!file.isDirectory()) {
            throw new ConfigException("Root directory for location: " + location.getPath() + " is not a directory");
        }
    }


}
