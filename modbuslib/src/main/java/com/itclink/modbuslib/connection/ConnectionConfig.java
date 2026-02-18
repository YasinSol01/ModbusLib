package com.itclink.modbuslib.connection;

import com.itclink.modbuslib.engine.ModbusTimingConfig;
import com.itclink.modbuslib.protocol.ModbusProtocol;
import com.itclink.modbuslib.transport.UsbSerialConfig;

public class ConnectionConfig {
    private final ModbusProtocol protocol;

    // TCP fields
    private String host;
    private int port = 502;

    // RTU fields
    private UsbSerialConfig serialConfig;

    // Common
    private ModbusTimingConfig timingConfig;
    private boolean autoReconnect = true;

    private ConnectionConfig(ModbusProtocol protocol) {
        this.protocol = protocol;
    }

    public ModbusProtocol getProtocol() { return protocol; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public UsbSerialConfig getSerialConfig() { return serialConfig; }
    public ModbusTimingConfig getTimingConfig() { return timingConfig; }
    public boolean isAutoReconnect() { return autoReconnect; }

    public static class Builder {
        private final ConnectionConfig config;

        public Builder(ModbusProtocol protocol) {
            config = new ConnectionConfig(protocol);
            config.timingConfig = protocol == ModbusProtocol.RTU
                    ? ModbusTimingConfig.forRTU()
                    : ModbusTimingConfig.forTCP();
        }

        public Builder host(String host) { config.host = host; return this; }
        public Builder port(int port) { config.port = port; return this; }
        public Builder serialConfig(UsbSerialConfig sc) { config.serialConfig = sc; return this; }
        public Builder timingConfig(ModbusTimingConfig tc) { config.timingConfig = tc; return this; }
        public Builder autoReconnect(boolean ar) { config.autoReconnect = ar; return this; }

        public ConnectionConfig build() { return config; }
    }
}
