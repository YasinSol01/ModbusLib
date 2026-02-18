package com.itclink.modbuslib;

import android.content.Context;

import com.itclink.modbuslib.protocol.ModbusProtocol;
import com.itclink.modbuslib.slave.RegisterMap;
import com.itclink.modbuslib.slave.SlaveRequestHandler;
import com.itclink.modbuslib.transport.UsbSerialConfig;

/**
 * Builder for creating ModbusSlave instances.
 *
 * <pre>
 * // TCP Slave on port 502
 * ModbusSlave slave = new ModbusSlaveBuilder(context)
 *     .protocol(ModbusProtocol.TCP)
 *     .slaveId(1)
 *     .port(502)
 *     .build();
 *
 * // RTU Slave via USB
 * ModbusSlave slave = new ModbusSlaveBuilder(context)
 *     .protocol(ModbusProtocol.RTU)
 *     .slaveId(1)
 *     .serialConfig(new UsbSerialConfig.Builder()
 *         .baudRate(9600).parity(UsbSerialConfig.PARITY_EVEN).build())
 *     .build();
 * </pre>
 */
public class ModbusSlaveBuilder {

    private final Context context;
    private ModbusProtocol protocol;
    private int slaveId = 1;
    private int port = 502;
    private UsbSerialConfig serialConfig;
    private RegisterMap registerMap;
    private SlaveRequestHandler handler;

    public ModbusSlaveBuilder(Context context) {
        this.context = context.getApplicationContext();
    }

    public ModbusSlaveBuilder protocol(ModbusProtocol protocol) {
        this.protocol = protocol;
        return this;
    }

    public ModbusSlaveBuilder slaveId(int slaveId) {
        this.slaveId = slaveId;
        return this;
    }

    public ModbusSlaveBuilder port(int port) {
        this.port = port;
        return this;
    }

    public ModbusSlaveBuilder serialConfig(UsbSerialConfig serialConfig) {
        this.serialConfig = serialConfig;
        return this;
    }

    /** Provide a custom RegisterMap. If not set, a default one is created. */
    public ModbusSlaveBuilder registerMap(RegisterMap registerMap) {
        this.registerMap = registerMap;
        return this;
    }

    /** Set a custom request handler for intercepting read/write events. */
    public ModbusSlaveBuilder requestHandler(SlaveRequestHandler handler) {
        this.handler = handler;
        return this;
    }

    public ModbusSlave build() {
        if (protocol == null) {
            throw new IllegalStateException("Protocol must be set (RTU or TCP)");
        }
        if (registerMap == null) {
            registerMap = new RegisterMap();
        }
        return new ModbusSlave(context, protocol, slaveId, port, serialConfig, registerMap, handler);
    }
}
