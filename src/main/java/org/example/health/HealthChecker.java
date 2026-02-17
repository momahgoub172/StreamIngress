package org.example.health;

import org.example.config.Location;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class HealthChecker {

    // How often to run health checks
    private static final int CHECK_INTERVAL_SECONDS = 10;


    // Timeouts for each individual health check
    //TODO: make configurable
    private static final int CHECK_CONNECT_TIMEOUT_MS = 2000; // after 2 seconds of trying to connect we give up
    private static final int CHECK_READ_TIMEOUT_MS = 2000; // when connected to server after 2 seconds of trying to read we give up


    private static ScheduledExecutorService scheduler;


    /**
     * Start periodic health checks for all proxy locations.
     * Safe to call once during server startup.
     */
    public static synchronized void start(List<Location> locations) {
        if (scheduler != null) {
            return;
        }

        Set<String> proxyUrls = new HashSet<>();

        if (locations != null) {
            for (Location location : locations) {
                if (location != null && location.isProxy() && location.getProxyUrl() != null) {
                    String proxyUrl = location.getProxyUrl().trim();
                    if (!proxyUrl.isEmpty()) {
                        proxyUrls.add(proxyUrl);
                        HealthCheckRegistry.register(proxyUrl);
                    }
                }
            }
        }

        if (proxyUrls.isEmpty()) {
            System.out.println("[HealthChecker] No proxy backends found, skipping health checks.");
            return;
        }


        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        System.out.println("[HealthChecker] Starting health checks for backends: " + proxyUrls);

        scheduler.scheduleAtFixedRate(() -> {
            for (String proxyUrl : HealthCheckRegistry.getAllProxyUrls()) {
                boolean isHealthy = checkBackend(proxyUrl);
                boolean previousHealthy = HealthCheckRegistry.isHealthy(proxyUrl);
                if (isHealthy != previousHealthy) {
                    HealthCheckRegistry.setHealthy(proxyUrl, isHealthy);
                    System.out.println("[HealthChecker] Backend " + proxyUrl + " is " + (isHealthy ? "healthy" : "unhealthy"));
                }
            }
        }, 0, CHECK_INTERVAL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
    }

    /**
     * Stop health checks and shut down the background thread.
     */
    public static synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
            System.out.println("[HealthChecker] Stopped.");
        }
    }

    /**
     * Perform a single health check for a backend URL.
     * Returns true if reachable and returns a 2xx–3xx status code.
     */
    private static boolean checkBackend(String proxyUrl) {
        if (proxyUrl == null || proxyUrl.isEmpty()) { return false; }
        HttpURLConnection connection = null;
        try {
            URL url = new URL(proxyUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CHECK_CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(CHECK_READ_TIMEOUT_MS);
            connection.connect();
            int responseCode = connection.getResponseCode();
            return responseCode >= 200 && responseCode < 400;
        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }


}
