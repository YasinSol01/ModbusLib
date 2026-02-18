package com.itclink.modbuslib.engine;

public class ModbusTimingConfig {
    private int responseTimeoutMs;
    private int interFrameDelayMs;
    private int maxRetries;
    private int retryDelayMs;
    private int maxQueueSize;

    private ModbusTimingConfig() {}

    public int getResponseTimeoutMs() { return responseTimeoutMs; }
    public int getInterFrameDelayMs() { return interFrameDelayMs; }
    public int getMaxRetries() { return maxRetries; }
    public int getRetryDelayMs() { return retryDelayMs; }
    public int getMaxQueueSize() { return maxQueueSize; }

    public static ModbusTimingConfig forRTU() {
        ModbusTimingConfig c = new ModbusTimingConfig();
        c.responseTimeoutMs = 500;
        c.interFrameDelayMs = 10;
        c.maxRetries = 3;
        c.retryDelayMs = 50;
        c.maxQueueSize = 50;
        return c;
    }

    public static ModbusTimingConfig forTCP() {
        ModbusTimingConfig c = new ModbusTimingConfig();
        c.responseTimeoutMs = 1500;
        c.interFrameDelayMs = 30;
        c.maxRetries = 2;
        c.retryDelayMs = 100;
        c.maxQueueSize = 50;
        return c;
    }

    public static ModbusTimingConfig custom(int timeoutMs, int interFrameMs, int retries, int retryDelayMs, int queueSize) {
        ModbusTimingConfig c = new ModbusTimingConfig();
        c.responseTimeoutMs = timeoutMs;
        c.interFrameDelayMs = interFrameMs;
        c.maxRetries = retries;
        c.retryDelayMs = retryDelayMs;
        c.maxQueueSize = queueSize;
        return c;
    }
}
