package com.itclink.modbuslib.protocol;

import com.itclink.modbuslib.util.ModbusValidation;

/**
 * Modbus TCP frame builder with MBAP header.
 * Extracted from MODBUS_PULL_TEST_siammeal ModbusTCP.java
 */
public class ModbusTcpFrameBuilder implements ModbusFrameBuilder {

    private static final int MBAP_HEADER_SIZE = 6;
    private static final int PROTOCOL_ID = 0x0000;
    private int transactionId = 0;

    public synchronized int getNextTransactionId() {
        transactionId = (transactionId % 65535) + 1;
        return transactionId;
    }

    public int getLastTransactionId() {
        return transactionId;
    }

    private byte[] buildMbapHeader(int txId, int pduLength, int unitId) {
        byte[] header = new byte[MBAP_HEADER_SIZE + 1]; // +1 for unit ID
        header[0] = (byte) ((txId >> 8) & 0xFF);
        header[1] = (byte) (txId & 0xFF);
        header[2] = (byte) ((PROTOCOL_ID >> 8) & 0xFF);
        header[3] = (byte) (PROTOCOL_ID & 0xFF);
        int length = pduLength + 1; // PDU + unit ID
        header[4] = (byte) ((length >> 8) & 0xFF);
        header[5] = (byte) (length & 0xFF);
        header[6] = (byte) unitId;
        return header;
    }

    @Override
    public byte[] buildReadFrame(int slaveId, int functionCode, int address, int quantity) {
        if (!ModbusValidation.isValidSlaveId(slaveId, ModbusProtocol.TCP)) return null;
        if (!ModbusValidation.isValidAddress(address)) return null;
        if (!ModbusValidation.isValidQuantity(quantity, functionCode)) return null;

        int txId = getNextTransactionId();
        byte[] frame = new byte[12]; // MBAP(6) + UnitID(1) + FC(1) + Addr(2) + Qty(2)

        // MBAP Header
        frame[0] = (byte) ((txId >> 8) & 0xFF);
        frame[1] = (byte) (txId & 0xFF);
        frame[2] = 0x00; frame[3] = 0x00; // Protocol ID
        frame[4] = 0x00; frame[5] = 0x06; // Length = 6
        frame[6] = (byte) slaveId;

        // PDU
        frame[7] = (byte) functionCode;
        frame[8] = (byte) ((address >> 8) & 0xFF);
        frame[9] = (byte) (address & 0xFF);
        frame[10] = (byte) ((quantity >> 8) & 0xFF);
        frame[11] = (byte) (quantity & 0xFF);

        return frame;
    }

    @Override
    public byte[] buildWriteSingleCoilFrame(int slaveId, int address, boolean value) {
        if (!ModbusValidation.isValidSlaveId(slaveId, ModbusProtocol.TCP)) return null;
        if (!ModbusValidation.isValidAddress(address)) return null;

        int txId = getNextTransactionId();
        byte[] frame = new byte[12];

        frame[0] = (byte) ((txId >> 8) & 0xFF);
        frame[1] = (byte) (txId & 0xFF);
        frame[2] = 0x00; frame[3] = 0x00;
        frame[4] = 0x00; frame[5] = 0x06;
        frame[6] = (byte) slaveId;
        frame[7] = 0x05;
        frame[8] = (byte) ((address >> 8) & 0xFF);
        frame[9] = (byte) (address & 0xFF);
        frame[10] = (byte) (value ? 0xFF : 0x00);
        frame[11] = 0x00;

        return frame;
    }

    @Override
    public byte[] buildWriteSingleRegisterFrame(int slaveId, int address, int value) {
        if (!ModbusValidation.isValidSlaveId(slaveId, ModbusProtocol.TCP)) return null;
        if (!ModbusValidation.isValidAddress(address)) return null;

        int txId = getNextTransactionId();
        byte[] frame = new byte[12];

        frame[0] = (byte) ((txId >> 8) & 0xFF);
        frame[1] = (byte) (txId & 0xFF);
        frame[2] = 0x00; frame[3] = 0x00;
        frame[4] = 0x00; frame[5] = 0x06;
        frame[6] = (byte) slaveId;
        frame[7] = 0x06;
        frame[8] = (byte) ((address >> 8) & 0xFF);
        frame[9] = (byte) (address & 0xFF);
        frame[10] = (byte) ((value >> 8) & 0xFF);
        frame[11] = (byte) (value & 0xFF);

        return frame;
    }

    @Override
    public byte[] buildWriteMultipleRegistersFrame(int slaveId, int address, int[] values) {
        if (!ModbusValidation.isValidSlaveId(slaveId, ModbusProtocol.TCP)) return null;
        if (!ModbusValidation.isValidAddress(address)) return null;
        if (values == null || values.length == 0 || values.length > 123) return null;

        int txId = getNextTransactionId();
        int byteCount = values.length * 2;
        int pduLength = 6 + byteCount; // FC + Addr(2) + Qty(2) + ByteCount + Data
        byte[] frame = new byte[MBAP_HEADER_SIZE + 1 + pduLength]; // MBAP + UnitID + PDU

        frame[0] = (byte) ((txId >> 8) & 0xFF);
        frame[1] = (byte) (txId & 0xFF);
        frame[2] = 0x00; frame[3] = 0x00;
        int length = pduLength + 1; // PDU + UnitID
        frame[4] = (byte) ((length >> 8) & 0xFF);
        frame[5] = (byte) (length & 0xFF);
        frame[6] = (byte) slaveId;
        frame[7] = 0x10;
        frame[8] = (byte) ((address >> 8) & 0xFF);
        frame[9] = (byte) (address & 0xFF);
        frame[10] = (byte) ((values.length >> 8) & 0xFF);
        frame[11] = (byte) (values.length & 0xFF);
        frame[12] = (byte) byteCount;
        for (int i = 0; i < values.length; i++) {
            frame[13 + i * 2] = (byte) ((values[i] >> 8) & 0xFF);
            frame[14 + i * 2] = (byte) (values[i] & 0xFF);
        }

        return frame;
    }

    @Override
    public byte[] buildWriteMultipleCoilsFrame(int slaveId, int address, boolean[] values) {
        if (!ModbusValidation.isValidSlaveId(slaveId, ModbusProtocol.TCP)) return null;
        if (!ModbusValidation.isValidAddress(address)) return null;
        if (values == null || values.length == 0 || values.length > 1968) return null;

        int txId = getNextTransactionId();
        int coilByteCount = (values.length + 7) / 8;
        int pduLength = 6 + coilByteCount; // FC + Addr(2) + Qty(2) + ByteCount + Data
        byte[] frame = new byte[MBAP_HEADER_SIZE + 1 + pduLength];

        int length = pduLength + 1;
        frame[0] = (byte) ((txId >> 8) & 0xFF);
        frame[1] = (byte) (txId & 0xFF);
        frame[2] = 0x00; frame[3] = 0x00;
        frame[4] = (byte) ((length >> 8) & 0xFF);
        frame[5] = (byte) (length & 0xFF);
        frame[6] = (byte) slaveId;
        frame[7] = 0x0F;
        frame[8] = (byte) ((address >> 8) & 0xFF);
        frame[9] = (byte) (address & 0xFF);
        frame[10] = (byte) ((values.length >> 8) & 0xFF);
        frame[11] = (byte) (values.length & 0xFF);
        frame[12] = (byte) coilByteCount;
        for (int i = 0; i < values.length; i++) {
            if (values[i]) {
                frame[13 + (i / 8)] |= (1 << (i % 8));
            }
        }

        return frame;
    }

    @Override
    public int getExpectedResponseLength(int functionCode, int quantity) {
        switch (functionCode) {
            case 0x01: case 0x02:
                return MBAP_HEADER_SIZE + 1 + 2 + (quantity + 7) / 8; // MBAP + UnitID + FC+Count + data
            case 0x03: case 0x04:
                return MBAP_HEADER_SIZE + 1 + 2 + quantity * 2;
            case 0x05: case 0x06: case 0x0F: case 0x10:
                return 12; // MBAP + UnitID + FC + Addr(2) + Value(2)
            default:
                return MBAP_HEADER_SIZE + 3;
        }
    }

    /** Extract transaction ID from a TCP frame */
    public static int getTransactionId(byte[] frame) {
        if (frame == null || frame.length < 2) return -1;
        return ((frame[0] & 0xFF) << 8) | (frame[1] & 0xFF);
    }
}
