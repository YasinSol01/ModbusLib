package com.itclink.modbuslib.protocol;

public interface ModbusFrameBuilder {
    byte[] buildReadFrame(int slaveId, int functionCode, int address, int quantity);
    byte[] buildWriteSingleCoilFrame(int slaveId, int address, boolean value);
    byte[] buildWriteSingleRegisterFrame(int slaveId, int address, int value);
    byte[] buildWriteMultipleRegistersFrame(int slaveId, int address, int[] values);
    byte[] buildWriteMultipleCoilsFrame(int slaveId, int address, boolean[] values);
    int getExpectedResponseLength(int functionCode, int quantity);
}
