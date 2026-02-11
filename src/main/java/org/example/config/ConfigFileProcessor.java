package org.example.config;
import java.io.File;
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Processes configuration files (YAML) and converts them to ServerConfig objects
 * This is the main entry point for loading configuration
 */
public class ConfigFileProcessor {
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    /**
     * Load and parse a YAML configuration file
     *
     * @param configFilePath Path to the config.yaml file
     * @return Validated ServerConfig object ready to use
     * @throws ConfigException if configuration is invalid
     * @throws IOException if file cannot be read
     */
    public static ServerConfig load(String configFilePath) throws ConfigException, IOException {
        System.out.println("Loading config file: " + configFilePath);
        File configFile = new File(configFilePath);

        // Validate file
        if (!configFile.exists()) {
            throw new IOException("Configuration file not found: " + configFilePath);
        }
        if (!configFile.canRead()) {
            throw new IOException("Cannot read configuration file: " + configFilePath);
        }

        ServerConfig serverConfig;
        try {
            serverConfig = yamlMapper.readValue(configFile, ServerConfig.class);
        } catch (Exception e) {
            throw new ConfigException("Invalid configuration file: " + e.getMessage());
        }

        // Validate the configuration
        ConfigValidator.validate(serverConfig);
        // Print summary
        printConfigSummary(serverConfig);

        return serverConfig;
    }

    /**
     * Load configuration with default path
     */
    public static ServerConfig load() throws ConfigException, IOException {
        return load("config.yaml");
    }


    /**
     * Print a summary of the loaded configuration
     */
    private static void printConfigSummary(ServerConfig config) {
        System.out.println("\n========== Configuration Summary ==========");

        if (config.getAccessLog() != null) {
            System.out.println("Access Log: " + config.getAccessLog());
        }

        if (config.getErrorLog() != null) {
            System.out.println("Error Log: " + config.getErrorLog());
        }

        System.out.println("\nServers:");
        for (Server server : config.getServers()) {
            System.out.println("  - Port: " + server.getListen());
            if (server.getServerName() != null) {
                System.out.println("    Server Name: " + server.getServerName());
            }
            System.out.println("    Locations: " + server.getLocations().size());
            for (Location loc : server.getLocations()) {
                System.out.println("      • " + loc.getPath() + " (" + loc.getType() + ")");
            }
        }

        System.out.println("==========================================\n");
    }



}
