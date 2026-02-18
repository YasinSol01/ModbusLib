package com.itclink.modbuslib;

import android.content.Context;

import com.itclink.modbuslib.connection.ConnectionConfig;
import com.itclink.modbuslib.engine.ModbusTimingConfig;
import com.itclink.modbuslib.protocol.ModbusProtocol;
import com.itclink.modbuslib.transport.UsbSerialConfig;

/**
 * Builder for creating ModbusClient instances.
 *
 * <pre>
 * // TCP
 * ModbusClient client = new ModbusClientBuilder(context)
 *     .protocol(ModbusProtocol.TCP)
 *     .host("10.12.92.200").port(502)
 *     .build();
 *
 * // RTU
 * ModbusClient client = new ModbusClientBuilder(context)
 *     .protocol(ModbusProtocol.RTU)
 *     .serialConfig(new UsbSerialConfig.Builder()
 *         .baudRate(9600).parity(UsbSerialConfig.PARITY_EVEN).build())
 *     .build();
 * </pre>
 */
public class ModbusClientBuilder {

    private final Context context;
    private ModbusProtocol protocol;
    private String host;
    private int port = 502;
    private UsbSerialConfig serialConfig;
    private ModbusTimingConfig timingConfig;
    private boolean autoReconnect = true;

    public ModbusClientBuilder(Context context) {
        this.context = context.getApplicationContext();
    }

    public ModbusClientBuilder protocol(ModbusProtocol protocol) {
        this.protocol = protocol;
        return this;
    }

    public ModbusClientBuilder host(String host) {
        this.host = host;
        return this;
    }

    public ModbusClientBuilder port(int port) {
        this.port = port;
        return this;
    }

    public ModbusClientBuilder serialConfig(UsbSerialConfig serialConfig) {
        this.serialConfig = serialConfig;
        return this;
    }

    public ModbusClientBuilder timingConfig(ModbusTimingConfig timingConfig) {
        this.timingConfig = timingConfig;
        return this;
    }

    public ModbusClientBuilder autoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
        return this;
    }

    public ModbusClient build() {
        if (protocol == null) {
            throw new IllegalStateException("Protocol must be set (RTU or TCP)");
        }

        ConnectionConfig.Builder configBuilder = new ConnectionConfig.Builder(protocol);
        configBuilder.autoReconnect(autoReconnect);

        if (protocol == ModbusProtocol.TCP) {
            if (host == null || host.isEmpty()) {
                throw new IllegalStateException("Host is required for TCP protocol");
            }
            configBuilder.host(host).port(port);
        } else {
            if (serialConfig != null) {
                configBuilder.serialConfig(serialConfig);
            }
        }

        if (timingConfig != null) {
            configBuilder.timingConfig(timingConfig);
        }

        return new ModbusClient(context, configBuilder.build());
    }
}
