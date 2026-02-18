package com.itclink.modbuslib;

import android.content.Context;

import com.itclink.modbuslib.protocol.ModbusProtocol;
import com.itclink.modbuslib.slave.RegisterMap;
import com.itclink.modbuslib.slave.SlaveEngine;
import com.itclink.modbuslib.slave.SlaveRequestHandler;
import com.itclink.modbuslib.slave.TcpSlaveTransport;
import com.itclink.modbuslib.slave.UsbRtuSlaveTransport;
import com.itclink.modbuslib.transport.UsbSerialConfig;
import com.itclink.modbuslib.util.ModbusLog;

/**
 * Public facade for Modbus Slave (Server) mode.
 * Makes the Android device act as a Modbus slave that responds to master requests.
 *
 * <pre>
 * ModbusSlave slave = new ModbusSlaveBuilder(context)
 *     .protocol(ModbusProtocol.TCP)
 *     .slaveId(1)
 *     .port(502)
 *     .build();
 *
 * // Set register values
 * slave.getRegisterMap().setHoldingRegister(0, 1234);
 * slave.getRegisterMap().setInputRegister(0, 5678);
 *
 * // Start listening
 * slave.start();
 *
 * // Update values at any time
 * slave.getRegisterMap().setHoldingRegister(0, 9999);
 *
 * // Stop
 * slave.stop();
 * </pre>
 */
public class ModbusSlave {

    private static final String TAG = "ModbusSlave";

    private final Context context;
    private final ModbusProtocol protocol;
    private final RegisterMap registerMap;
    private final SlaveEngine engine;

    // Transport (only one is active)
    private TcpSlaveTransport tcpTransport;
    private UsbRtuSlaveTransport rtuTransport;

    // Config
    private final int port;
    private final UsbSerialConfig serialConfig;

    ModbusSlave(Context context, ModbusProtocol protocol, int slaveId, int port,
                UsbSerialConfig serialConfig, RegisterMap registerMap, SlaveRequestHandler handler) {
        this.context = context;
        this.protocol = protocol;
        this.registerMap = registerMap;
        this.port = port;
        this.serialConfig = serialConfig;

        this.engine = new SlaveEngine(slaveId, registerMap, protocol);
        if (handler != null) {
            this.engine.setHandler(handler);
        }
    }

    /**
     * Start the slave. Begins listening for master connections/requests.
     */
    public void start() throws Exception {
        if (protocol == ModbusProtocol.TCP) {
            tcpTransport = new TcpSlaveTransport(port, engine);
            tcpTransport.setHandler(engine.getSlaveId() > 0 ? null : null); // handler is in engine
            tcpTransport.start();
            ModbusLog.i(TAG, "TCP Slave started on port " + port + " (ID=" + engine.getSlaveId() + ")");
        } else {
            rtuTransport = new UsbRtuSlaveTransport(context, serialConfig, engine);
            rtuTransport.start();
            ModbusLog.i(TAG, "RTU Slave started (ID=" + engine.getSlaveId() + ")");
        }
    }

    /**
     * Stop the slave. Closes all connections.
     */
    public void stop() {
        if (tcpTransport != null) {
            tcpTransport.stop();
            tcpTransport = null;
        }
        if (rtuTransport != null) {
            rtuTransport.stop();
            rtuTransport = null;
        }
        ModbusLog.i(TAG, "Slave stopped");
    }

    /**
     * Check if the slave is currently running.
     */
    public boolean isRunning() {
        if (tcpTransport != null) return tcpTransport.isRunning();
        if (rtuTransport != null) return rtuTransport.isRunning();
        return false;
    }

    /**
     * Get the RegisterMap to read/write register values.
     * Values set here will be served to masters when they send read requests.
     */
    public RegisterMap getRegisterMap() {
        return registerMap;
    }

    /**
     * Convenience: set a holding register value.
     */
    public void setHoldingRegister(int address, int value) {
        registerMap.setHoldingRegister(address, value);
    }

    /**
     * Convenience: set an input register value.
     */
    public void setInputRegister(int address, int value) {
        registerMap.setInputRegister(address, value);
    }

    /**
     * Convenience: set a coil value.
     */
    public void setCoil(int address, boolean value) {
        registerMap.setCoil(address, value);
    }

    /**
     * Convenience: set a discrete input value.
     */
    public void setDiscreteInput(int address, boolean value) {
        registerMap.setDiscreteInput(address, value);
    }

    /**
     * Get the number of connected TCP clients. Returns 0 for RTU.
     */
    public int getClientCount() {
        if (tcpTransport != null) return tcpTransport.getClientCount();
        return 0;
    }

    public ModbusProtocol getProtocol() { return protocol; }
    public int getSlaveId() { return engine.getSlaveId(); }
    public long getRequestCount() { return engine.getRequestCount(); }
    public long getErrorCount() { return engine.getErrorCount(); }
}
