package com.itclink.modbuslib.protocol;

import com.itclink.modbuslib.command.ModbusCommand;

/**
 * TCP response parser with MBAP header and Transaction ID validation.
 */
public class TcpResponseParser implements ModbusResponseParser {

    private static final int MBAP_HEADER_SIZE = 6;

    @Override
    public boolean isValidFrame(byte[] frame) {
        if (frame == null || frame.length < MBAP_HEADER_SIZE + 2) return false;

        // Check protocol ID (must be 0x0000)
        int protocolId = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
        if (protocolId != 0) return false;

        // Check length field
        int declaredLength = ((frame[4] & 0xFF) << 8) | (frame[5] & 0xFF);
        int actualLength = frame.length - MBAP_HEADER_SIZE;
        return declaredLength == actualLength;
    }

    @Override
    public boolean isErrorResponse(byte[] frame) {
        if (frame == null || frame.length < MBAP_HEADER_SIZE + 2) return false;
        int functionCode = frame[MBAP_HEADER_SIZE + 1] & 0xFF; // UnitID at +0, FC at +1
        return (functionCode & 0x80) != 0;
    }

    @Override
    public int getErrorCode(byte[] frame) {
        if (!isErrorResponse(frame) || frame.length < MBAP_HEADER_SIZE + 3) return -1;
        return frame[MBAP_HEADER_SIZE + 2] & 0xFF;
    }

    @Override
    public boolean matchesCommand(byte[] response, ModbusCommand command) {
        if (response == null || response.length < MBAP_HEADER_SIZE + 2) return false;

        int unitId = response[MBAP_HEADER_SIZE] & 0xFF;
        int respFunc = response[MBAP_HEADER_SIZE + 1] & 0xFF;

        if ((respFunc & 0x80) != 0) {
            return unitId == command.slaveId && (respFunc & 0x7F) == command.functionCode;
        }
        return unitId == command.slaveId && respFunc == command.functionCode;
    }

    /** Check if response Transaction ID matches expected */
    public boolean matchesTransactionId(byte[] response, int expectedTxId) {
        if (response == null || response.length < 2) return false;
        int txId = ((response[0] & 0xFF) << 8) | (response[1] & 0xFF);
        return txId == expectedTxId;
    }

    /** Extract PDU (everything after MBAP header) */
    public byte[] extractPDU(byte[] frame) {
        if (frame == null || frame.length <= MBAP_HEADER_SIZE) return null;
        byte[] pdu = new byte[frame.length - MBAP_HEADER_SIZE];
        System.arraycopy(frame, MBAP_HEADER_SIZE, pdu, 0, pdu.length);
        return pdu;
    }

    @Override
    public int[] extractRegisters(byte[] response) {
        // PDU: [UnitID][FC][ByteCount][Data...]
        byte[] pdu = extractPDU(response);
        if (pdu == null || pdu.length < 4) return new int[0];

        int byteCount = pdu[2] & 0xFF;
        if (byteCount % 2 != 0 || pdu.length < 3 + byteCount) return new int[0];

        int registerCount = byteCount / 2;
        int[] registers = new int[registerCount];
        for (int i = 0; i < registerCount; i++) {
            registers[i] = ((pdu[3 + i * 2] & 0xFF) << 8) | (pdu[4 + i * 2] & 0xFF);
        }
        return registers;
    }

    @Override
    public int[] extractCoils(byte[] response, int quantity) {
        byte[] pdu = extractPDU(response);
        if (pdu == null || pdu.length < 4) return new int[0];

        int byteCount = pdu[2] & 0xFF;
        int[] coils = new int[quantity];
        for (int i = 0; i < quantity && i < byteCount * 8; i++) {
            int byteIndex = i / 8;
            int bitIndex = i % 8;
            if (3 + byteIndex < pdu.length) {
                coils[i] = ((pdu[3 + byteIndex] >> bitIndex) & 1);
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
                return new int[]{1};
            default:
                return new int[0];
        }
    }
}
