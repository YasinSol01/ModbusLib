package com.itclink.modbuslib;

import android.content.Context;

import com.itclink.modbuslib.connection.ConnectionConfig;
import com.itclink.modbuslib.engine.ModbusTimingConfig;
import com.itclink.modbuslib.protocol.ModbusProtocol;
import com.itclink.modbuslib.transport.ModbusTransport;
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
    private ModbusTransport customTransport; // inject custom transport (e.g. NativeSerialTransport)

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

    /**
     * ใช้ custom transport แทน UsbRtuTransport (เช่น NativeSerialTransport สำหรับ /dev/ttyS*)
     * ถ้าตั้งค่านี้ ไม่ต้องเรียก protocol() เพราะจะใช้ RTU framing เสมอ
     */
    public ModbusClientBuilder transport(ModbusTransport transport) {
        this.customTransport = transport;
        return this;
    }

    public ModbusClient build() {
        // Custom transport path (e.g. NativeSerialTransport for /dev/ttyS*)
        if (customTransport != null) {
            ConnectionConfig.Builder configBuilder = new ConnectionConfig.Builder(ModbusProtocol.RTU);
            configBuilder.autoReconnect(autoReconnect);
            if (timingConfig != null) configBuilder.timingConfig(timingConfig);
            return new ModbusClient(context, configBuilder.build(), customTransport);
        }

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
