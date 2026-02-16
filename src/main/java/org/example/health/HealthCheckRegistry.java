package org.example.health;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class HealthCheckRegistry {
    private static final ConcurrentMap<String, Boolean> health = new ConcurrentHashMap<>();

    public static void register(String proxyUrl) {
        health.putIfAbsent(proxyUrl, true); // default optimistic
    }

    public static void setHealthy(String proxyUrl, boolean isHealthy) {
        health.put(proxyUrl, isHealthy);
    }

    public static boolean isHealthy(String proxyUrl) {
        return health.getOrDefault(proxyUrl, true);
    }

    public static Set<String> getAllProxyUrls() {
        return health.keySet();
    }
}
