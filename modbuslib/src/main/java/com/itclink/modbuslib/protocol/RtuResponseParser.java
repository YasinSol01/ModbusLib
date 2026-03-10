package com.itclink.modbuslib.protocol;

import com.itclink.modbuslib.command.ModbusCommand;

/**
 * RTU response parser with CRC validation.
 */
public class RtuResponseParser implements ModbusResponseParser {

    private static final String TAG = "RtuParser";
    private final ModbusRtuFrameBuilder frameBuilder;

    public RtuResponseParser() {
        this.frameBuilder = new ModbusRtuFrameBuilder();
    }

    @Override
    public boolean isValidFrame(byte[] frame) {
        if (frame == null || frame.length < 4 || frame.length > 256) return false;
        return frameBuilder.verifyCRC(frame);
    }

    @Override
    public boolean isErrorResponse(byte[] frame) {
        if (frame == null || frame.length < 3) return false;
        return (frame[1] & 0x80) != 0;
    }

    @Override
    public int getErrorCode(byte[] frame) {
        if (!isErrorResponse(frame) || frame.length < 3) return -1;
        return frame[2] & 0xFF;
    }

    @Override
    public boolean matchesCommand(byte[] response, ModbusCommand command) {
        if (response == null || response.length < 5) return false;

        int respSlaveId = response[0] & 0xFF;
        int respFunc = response[1] & 0xFF;

        if ((respFunc & 0x80) != 0) {
            // Error response - check base function code
            return respSlaveId == command.slaveId && (respFunc & 0x7F) == command.functionCode;
        }

        return respSlaveId == command.slaveId && respFunc == command.functionCode;
    }

    @Override
    public int[] extractRegisters(byte[] response) {
        if (response == null || response.length < 5) return new int[0];
        int byteCount = response[2] & 0xFF;
        if (byteCount % 2 != 0) return new int[0];
        // Accept frame with at least the data bytes (3 header + byteCount), CRC optional
        if (response.length < 3 + byteCount) return new int[0];

        int registerCount = byteCount / 2;
        int[] registers = new int[registerCount];
        for (int i = 0; i < registerCount; i++) {
            int hi = 3 + i * 2;
            int lo = 4 + i * 2;
            if (lo >= response.length) break;
            registers[i] = ((response[hi] & 0xFF) << 8) | (response[lo] & 0xFF);
        }
        return registers;
    }

    @Override
    public int[] extractCoils(byte[] response, int quantity) {
        if (response == null || response.length < 4) return new int[0];
        int byteCount = response[2] & 0xFF;
        // Accept frame if it has at least the data bytes (3 header + byteCount data).
        // CRC bytes (2) may be missing if the slave omits CRC_H or data arrived before CRC.
        if (response.length < 3 + byteCount) return new int[0];

        int[] coils = new int[quantity];
        for (int i = 0; i < quantity; i++) {
            int byteIndex = i / 8;
            int bitIndex = i % 8;
            int respIndex = 3 + byteIndex;
            if (byteIndex < byteCount && respIndex < response.length) {
                coils[i] = ((response[respIndex] >> bitIndex) & 1);
            }
        }
        return coils;
    }

    @Override
    public int[] extractData(byte[] response, ModbusCommand command) {
        switch (command.functionCode) {
            case 0x03: case 0x04:
                return extractRegisters(response);
            case 0x01: case 0x02:
                return extractCoils(response, command.quantity);
            case 0x05: case 0x06: case 0x0F: case 0x10:
                return new int[]{1}; // Success indicator
            default:
                return new int[0];
        }
    }
}
