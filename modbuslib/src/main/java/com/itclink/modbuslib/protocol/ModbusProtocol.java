package com.itclink.modbuslib.protocol;

public enum ModbusProtocol {
    RTU(1, 247, 500, 10, 3),
    TCP(0, 255, 1500, 30, 2);

    private final int slaveIdMin;
    private final int slaveIdMax;
    private final int defaultTimeoutMs;
    private final int defaultInterFrameMs;
    private final int defaultRetries;

    ModbusProtocol(int slaveIdMin, int slaveIdMax, int defaultTimeoutMs,
                   int defaultInterFrameMs, int defaultRetries) {
        this.slaveIdMin = slaveIdMin;
        this.slaveIdMax = slaveIdMax;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.defaultInterFrameMs = defaultInterFrameMs;
        this.defaultRetries = defaultRetries;
    }

    public int getSlaveIdMin() { return slaveIdMin; }
    public int getSlaveIdMax() { return slaveIdMax; }
    public int getDefaultTimeoutMs() { return defaultTimeoutMs; }
    public int getDefaultInterFrameMs() { return defaultInterFrameMs; }
    public int getDefaultRetries() { return defaultRetries; }
}
