package org.example.health;

import org.example.config.Location;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

public class HealthChecker {

    // How often to run health checks
    private static final int CHECK_INTERVAL_SECONDS = 10;

    // Timeouts for each individual health check
    private static final int CHECK_CONNECT_TIMEOUT_MS = 2000; // after 2 seconds of trying to connect we give up
    private static final int CHECK_READ_TIMEOUT_MS = 2000; // when connected to server after 2 seconds of trying to read we give up


    private static ScheduledExecutorService scheduler;


    /**
     * Start periodic health checks for all proxy locations.
     * Safe to call once during server startup.
     */
    public static synchronized void start(List<Location > locations) {
    }

    /**
     * Stop health checks and shut down the background thread.
     */
    public static synchronized void stop() {
    }

    /**
     * Perform a single health check for a backend URL.
     * Returns true if reachable and returns a 2xx–3xx status code.
     */
    private static boolean checkBackend(String proxyUrl){
        return false;
    }




}
