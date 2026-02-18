package com.itclink.modbuslib.command;

import com.itclink.modbuslib.callback.ModbusCallback;

public class ModbusCommand {
    public int slaveId;
    public int functionCode;
    public int address;
    public int quantity;
    public int[] values;
    public ModbusCallback callback;
    public boolean highPriority;
    public long timestamp;

    public ModbusCommand() {
        this.timestamp = System.nanoTime();
    }

    public ModbusCommand(int slaveId, int functionCode, int address, int quantity,
                         int[] values, ModbusCallback callback, boolean highPriority) {
        this.slaveId = slaveId;
        this.functionCode = functionCode;
        this.address = address;
        this.quantity = quantity;
        this.values = values;
        this.callback = callback;
        this.highPriority = highPriority;
        this.timestamp = System.nanoTime();
    }
}
