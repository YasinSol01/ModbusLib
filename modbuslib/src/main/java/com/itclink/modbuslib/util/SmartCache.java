package com.itclink.modbuslib.util;

import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance cache with TTL (Time-To-Live).
 * Reduces Modbus read operations by caching recent values.
 */
public class SmartCache<T> {

    private static class CachedValue<T> {
        final T value;
        final long expiryTime;
        final long creationTime;

        CachedValue(T value, long ttlMs) {
            this.value = value;
            this.creationTime = System.currentTimeMillis();
            this.expiryTime = creationTime + ttlMs;
        }

        boolean isValid() { return System.currentTimeMillis() < expiryTime; }
        long getAge() { return System.currentTimeMillis() - creationTime; }
    }

    private final ConcurrentHashMap<String, CachedValue<T>> cache = new ConcurrentHashMap<>();
    private final long defaultTtlMs;
    private volatile int hitCount = 0;
    private volatile int missCount = 0;

    public SmartCache(long defaultTtlMs) {
        this.defaultTtlMs = defaultTtlMs;
    }

    public void put(String key, T value) { put(key, value, defaultTtlMs); }

    public void put(String key, T value, long ttlMs) {
        cache.put(key, new CachedValue<>(value, ttlMs));
    }

    public T get(String key) {
        CachedValue<T> cached = cache.get(key);
        if (cached == null) { missCount++; return null; }
        if (!cached.isValid()) { cache.remove(key); missCount++; return null; }
        hitCount++;
        return cached.value;
    }

    public boolean contains(String key) {
        CachedValue<T> cached = cache.get(key);
        if (cached == null) return false;
        if (!cached.isValid()) { cache.remove(key); return false; }
        return true;
    }

    public int cleanupExpired() {
        int removed = 0;
        for (String key : cache.keySet()) {
            CachedValue<T> cached = cache.get(key);
            if (cached != null && !cached.isValid()) {
                cache.remove(key);
                removed++;
            }
        }
        return removed;
    }

    public int size() { return cache.size(); }
    public int getHitCount() { return hitCount; }
    public int getMissCount() { return missCount; }

    public double getHitRate() {
        int total = hitCount + missCount;
        return total > 0 ? (double) hitCount / total * 100 : 0;
    }

    public void clear() {
        cache.clear();
        hitCount = 0;
        missCount = 0;
    }
}
