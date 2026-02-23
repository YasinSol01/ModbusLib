package com.itclink.modbuslib;

import android.content.Context;

import com.itclink.modbuslib.callback.ModbusBatchCallback;
import com.itclink.modbuslib.callback.ModbusCallback;
import com.itclink.modbuslib.command.BatchReadRequest;
import com.itclink.modbuslib.connection.ConnectionConfig;
import com.itclink.modbuslib.connection.ConnectionState;
import com.itclink.modbuslib.engine.ModbusEngine;
import com.itclink.modbuslib.engine.ModbusTimingConfig;
import com.itclink.modbuslib.exception.ModbusTransportException;
import com.itclink.modbuslib.protocol.ModbusFrameBuilder;
import com.itclink.modbuslib.protocol.ModbusProtocol;
import com.itclink.modbuslib.protocol.ModbusResponseParser;
import com.itclink.modbuslib.protocol.ModbusRtuFrameBuilder;
import com.itclink.modbuslib.protocol.ModbusTcpFrameBuilder;
import com.itclink.modbuslib.protocol.RtuResponseParser;
import com.itclink.modbuslib.protocol.TcpResponseParser;
import com.itclink.modbuslib.transport.ModbusTransport;
import com.itclink.modbuslib.transport.TcpTransport;
import com.itclink.modbuslib.transport.UsbRtuTransport;
import com.itclink.modbuslib.util.ModbusLog;

/**
 * Public facade for Modbus communication.
 * Supports both TCP and RTU protocols with a unified API.
 *
 * <pre>
 * ModbusClient client = new ModbusClientBuilder(context)
 *     .protocol(ModbusProtocol.TCP)
 *     .host("10.12.92.200").port(502)
 *     .build();
 *
 * client.connect();
 * client.readHoldingRegisters(1, 0, 10, callback, false);
 * client.disconnect();
 * </pre>
 */
public class ModbusClient {

    private static final String TAG = "ModbusClient";

    private final Context context;
    private final ConnectionConfig config;
    private final ModbusEngine engine;
    private final ModbusTransport transport;

    ModbusClient(Context context, ConnectionConfig config) {
        this.context = context;
        this.config = config;

        ModbusProtocol protocol = config.getProtocol();
        ModbusTimingConfig timing = config.getTimingConfig();

        // Create protocol-specific components
        ModbusFrameBuilder frameBuilder;
        ModbusResponseParser responseParser;

        if (protocol == ModbusProtocol.TCP) {
            frameBuilder = new ModbusTcpFrameBuilder();
            responseParser = new TcpResponseParser();
            transport = new TcpTransport(config);
        } else {
            frameBuilder = new ModbusRtuFrameBuilder();
            responseParser = new RtuResponseParser();
            transport = new UsbRtuTransport(context, config);
        }

        // Create engine
        engine = new ModbusEngine(protocol, frameBuilder, responseParser, timing);
        engine.setTransport(transport);
    }

    /**
     * Constructor สำหรับ custom transport (เช่น NativeSerialTransport)
     * ใช้ RTU framing เสมอ - เรียกผ่าน ModbusClientBuilder.transport()
     */
    ModbusClient(Context context, ConnectionConfig config, ModbusTransport injectedTransport) {
        this.context = context;
        this.config = config;
        this.transport = injectedTransport;

        ModbusTimingConfig timing = config.getTimingConfig();

        // Custom transport ใช้ RTU framing เสมอ
        engine = new ModbusEngine(
            ModbusProtocol.RTU,
            new ModbusRtuFrameBuilder(),
            new RtuResponseParser(),
            timing
        );
        engine.setTransport(transport);
    }

    // ===== CONNECTION =====

    public void connect() throws ModbusTransportException {
        transport.connect();
        engine.start();
        ModbusLog.i(TAG, "ModbusClient connected (" + config.getProtocol() + ")");
    }

    public void disconnect() {
        engine.stop();
        transport.disconnect();
        ModbusLog.i(TAG, "ModbusClient disconnected");
    }

    public boolean isConnected() {
        return transport.isConnected();
    }

    public ConnectionState getConnectionState() {
        return transport.getState();
    }

    // ===== READ OPERATIONS (Async) =====

    /** FC 0x01 - Read Coils */
    public void readCoils(int slaveId, int address, int quantity, ModbusCallback callback, boolean highPriority) {
        engine.addCommand(slaveId, 0x01, address, quantity, null, callback, highPriority);
    }

    /** FC 0x02 - Read Discrete Inputs */
    public void readDiscreteInputs(int slaveId, int address, int quantity, ModbusCallback callback, boolean highPriority) {
        engine.addCommand(slaveId, 0x02, address, quantity, null, callback, highPriority);
    }

    /** FC 0x03 - Read Holding Registers */
    public void readHoldingRegisters(int slaveId, int address, int quantity, ModbusCallback callback, boolean highPriority) {
        engine.addCommand(slaveId, 0x03, address, quantity, null, callback, highPriority);
    }

    /** FC 0x04 - Read Input Registers */
    public void readInputRegisters(int slaveId, int address, int quantity, ModbusCallback callback, boolean highPriority) {
        engine.addCommand(slaveId, 0x04, address, quantity, null, callback, highPriority);
    }

    // ===== WRITE OPERATIONS (Async) =====

    /** FC 0x05 - Write Single Coil */
    public void writeSingleCoil(int slaveId, int address, boolean value, ModbusCallback callback, boolean highPriority) {
        engine.addCommand(slaveId, 0x05, address, 1, new int[]{value ? 1 : 0}, callback, highPriority);
    }

    /** FC 0x06 - Write Single Register */
    public void writeSingleRegister(int slaveId, int address, int value, ModbusCallback callback, boolean highPriority) {
        engine.addCommand(slaveId, 0x06, address, 1, new int[]{value}, callback, highPriority);
    }

    /** FC 0x0F - Write Multiple Coils */
    public void writeMultipleCoils(int slaveId, int address, boolean[] values, ModbusCallback callback, boolean highPriority) {
        int[] intValues = new int[values.length];
        for (int i = 0; i < values.length; i++) intValues[i] = values[i] ? 1 : 0;
        engine.addCommand(slaveId, 0x0F, address, values.length, intValues, callback, highPriority);
    }

    /** FC 0x10 - Write Multiple Registers */
    public void writeMultipleRegisters(int slaveId, int address, int[] values, ModbusCallback callback, boolean highPriority) {
        engine.addCommand(slaveId, 0x10, address, values.length, values, callback, highPriority);
    }

    // ===== BATCH OPERATIONS =====

    /** Execute multiple reads as a batch */
    public void batchRead(BatchReadRequest request, ModbusBatchCallback callback) {
        engine.batchRead(request, callback);
    }

    // ===== SYNC OPERATIONS (blocking - call from background thread) =====

    /** Synchronous read registers. Returns null on failure. Must be called from a background thread. */
    public int[] readRegistersSync(int slaveId, int functionCode, int address, int quantity) {
        return engine.readRegistersSync(slaveId, functionCode, address, quantity);
    }

    /** Synchronous write register. Returns true on success. Must be called from a background thread. */
    public boolean writeRegisterSync(int slaveId, int address, int value) {
        return engine.writeRegisterSync(slaveId, 0x06, address, value);
    }

    // ===== ENGINE CONTROL =====

    /** Pause command processing (queued commands are preserved) */
    public void pause() { engine.pause(); }

    /** Resume command processing */
    public void resume() { engine.resume(); }

    // ===== INFO =====

    public ModbusProtocol getProtocol() { return config.getProtocol(); }
    public String getStats() { return engine.getStats(); }
    public int getConsecutiveErrors() { return engine.getConsecutiveErrors(); }

    /** Get the underlying transport for advanced use */
    public ModbusTransport getTransport() { return transport; }
}
