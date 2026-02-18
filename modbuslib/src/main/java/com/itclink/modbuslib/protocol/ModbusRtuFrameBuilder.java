package com.itclink.modbuslib.protocol;

import com.itclink.modbuslib.util.ModbusLog;
import com.itclink.modbuslib.util.ModbusValidation;

/**
 * Modbus RTU frame builder with CRC-16 (polynomial 0xA001).
 * Extracted from MODBUS_PULL_TEST ModbusRTU.java
 */
public class ModbusRtuFrameBuilder implements ModbusFrameBuilder {

    private static final String TAG = "RtuFrame";

    // CRC-16 lookup table (polynomial 0xA001)
    private static final int[] CRC_TABLE = {
        0x0000, 0xC0C1, 0xC181, 0x0140, 0xC301, 0x03C0, 0x0280, 0xC241,
        0xC601, 0x06C0, 0x0780, 0xC741, 0x0500, 0xC5C1, 0xC481, 0x0440,
        0xCC01, 0x0CC0, 0x0D80, 0xCD41, 0x0F00, 0xCFC1, 0xCE81, 0x0E40,
        0x0A00, 0xCAC1, 0xCB81, 0x0B40, 0xC901, 0x09C0, 0x0880, 0xC841,
        0xD801, 0x18C0, 0x1980, 0xD941, 0x1B00, 0xDBC1, 0xDA81, 0x1A40,
        0x1E00, 0xDEC1, 0xDF81, 0x1F40, 0xDD01, 0x1DC0, 0x1C80, 0xDC41,
        0x1400, 0xD4C1, 0xD581, 0x1540, 0xD701, 0x17C0, 0x1680, 0xD641,
        0xD201, 0x12C0, 0x1380, 0xD341, 0x1100, 0xD1C1, 0xD081, 0x1040,
        0xF001, 0x30C0, 0x3180, 0xF141, 0x3300, 0xF3C1, 0xF281, 0x3240,
        0x3600, 0xF6C1, 0xF781, 0x3740, 0xF501, 0x35C0, 0x3480, 0xF441,
        0x3C00, 0xFCC1, 0xFD81, 0x3D40, 0xFF01, 0x3FC0, 0x3E80, 0xFE41,
        0xFA01, 0x3AC0, 0x3B80, 0xFB41, 0x3900, 0xF9C1, 0xF881, 0x3840,
        0x2800, 0xE8C1, 0xE981, 0x2940, 0xEB01, 0x2BC0, 0x2A80, 0xEA41,
        0xEE01, 0x2EC0, 0x2F80, 0xEF41, 0x2D00, 0xEDC1, 0xEC81, 0x2C40,
        0xE401, 0x24C0, 0x2580, 0xE541, 0x2700, 0xE7C1, 0xE681, 0x2640,
        0x2200, 0xE2C1, 0xE381, 0x2340, 0xE101, 0x21C0, 0x2080, 0xE041,
        0xA001, 0x60C0, 0x6180, 0xA141, 0x6300, 0xA3C1, 0xA281, 0x6240,
        0x6600, 0xA6C1, 0xA781, 0x6740, 0xA501, 0x65C0, 0x6480, 0xA441,
        0x6C00, 0xACC1, 0xAD81, 0x6D40, 0xAF01, 0x6FC0, 0x6E80, 0xAE41,
        0xAA01, 0x6AC0, 0x6B80, 0xAB41, 0x6900, 0xA9C1, 0xA881, 0x6840,
        0x7800, 0xB8C1, 0xB981, 0x7940, 0xBB01, 0x7BC0, 0x7A80, 0xBA41,
        0xBE01, 0x7EC0, 0x7F80, 0xBF41, 0x7D00, 0xBDC1, 0xBC81, 0x7C40,
        0xB401, 0x74C0, 0x7580, 0xB541, 0x7700, 0xB7C1, 0xB681, 0x7640,
        0x7200, 0xB2C1, 0xB381, 0x7340, 0xB101, 0x71C0, 0x7080, 0xB041,
        0x5000, 0x90C1, 0x9181, 0x5140, 0x9301, 0x53C0, 0x5280, 0x9241,
        0x9601, 0x56C0, 0x5780, 0x9741, 0x5500, 0x95C1, 0x9481, 0x5440,
        0x9C01, 0x5CC0, 0x5D80, 0x9D41, 0x5F00, 0x9FC1, 0x9E81, 0x5E40,
        0x5A00, 0x9AC1, 0x9B81, 0x5B40, 0x9901, 0x59C0, 0x5880, 0x9841,
        0x8801, 0x48C0, 0x4980, 0x8941, 0x4B00, 0x8BC1, 0x8A81, 0x4A40,
        0x4E00, 0x8EC1, 0x8F81, 0x4F40, 0x8D01, 0x4DC0, 0x4C80, 0x8C41,
        0x4400, 0x84C1, 0x8581, 0x4540, 0x8701, 0x47C0, 0x4680, 0x8641,
        0x8201, 0x42C0, 0x4380, 0x8341, 0x4100, 0x81C1, 0x8081, 0x4040
    };

    public int calculateCRC(byte[] data, int length) {
        if (data == null || length <= 0 || length > data.length) return 0;
        int crc = 0xFFFF;
        for (int i = 0; i < length; i++) {
            int index = (crc ^ (data[i] & 0xFF)) & 0xFF;
            crc = ((crc >> 8) & 0xFF) ^ CRC_TABLE[index];
        }
        return crc & 0xFFFF;
    }

    public boolean verifyCRC(byte[] frame) {
        if (frame == null || frame.length < 4) return false;
        int received = ((frame[frame.length - 1] & 0xFF) << 8) | (frame[frame.length - 2] & 0xFF);
        int calculated = calculateCRC(frame, frame.length - 2);
        return received == calculated;
    }

    /**
     * Creates a new byte array with CRC appended.
     * Used by SlaveEngine to build RTU response frames.
     */
    public byte[] buildFrameWithCRC(byte[] data) {
        if (data == null || data.length == 0) return data;
        byte[] frame = new byte[data.length + 2];
        System.arraycopy(data, 0, frame, 0, data.length);
        int crc = calculateCRC(frame, data.length);
        frame[data.length] = (byte) (crc & 0xFF);
        frame[data.length + 1] = (byte) ((crc >> 8) & 0xFF);
        return frame;
    }

    private void appendCRC(byte[] frame, int length) {
        if (frame == null || length < 0 || length + 2 > frame.length) return;
        int crc = calculateCRC(frame, length);
        frame[length] = (byte) (crc & 0xFF);           // CRC Low (Little Endian)
        frame[length + 1] = (byte) ((crc >> 8) & 0xFF); // CRC High
    }

    @Override
    public byte[] buildReadFrame(int slaveId, int functionCode, int address, int quantity) {
        if (!ModbusValidation.isValidSlaveId(slaveId, ModbusProtocol.RTU)) return null;
        if (!ModbusValidation.isValidAddress(address)) return null;
        if (!ModbusValidation.isValidQuantity(quantity, functionCode)) return null;

        byte[] frame = new byte[8];
        frame[0] = (byte) slaveId;
        frame[1] = (byte) functionCode;
        frame[2] = (byte) ((address >> 8) & 0xFF);
        frame[3] = (byte) (address & 0xFF);
        frame[4] = (byte) ((quantity >> 8) & 0xFF);
        frame[5] = (byte) (quantity & 0xFF);
        appendCRC(frame, 6);
        return frame;
    }

    @Override
    public byte[] buildWriteSingleCoilFrame(int slaveId, int address, boolean value) {
        if (!ModbusValidation.isValidSlaveId(slaveId, ModbusProtocol.RTU)) return null;
        if (!ModbusValidation.isValidAddress(address)) return null;

        byte[] frame = new byte[8];
        frame[0] = (byte) slaveId;
        frame[1] = 0x05;
        frame[2] = (byte) ((address >> 8) & 0xFF);
        frame[3] = (byte) (address & 0xFF);
        frame[4] = (byte) (value ? 0xFF : 0x00);
        frame[5] = 0x00;
        appendCRC(frame, 6);
        return frame;
    }

    @Override
    public byte[] buildWriteSingleRegisterFrame(int slaveId, int address, int value) {
        if (!ModbusValidation.isValidSlaveId(slaveId, ModbusProtocol.RTU)) return null;
        if (!ModbusValidation.isValidAddress(address)) return null;

        byte[] frame = new byte[8];
        frame[0] = (byte) slaveId;
        frame[1] = 0x06;
        frame[2] = (byte) ((address >> 8) & 0xFF);
        frame[3] = (byte) (address & 0xFF);
        frame[4] = (byte) ((value >> 8) & 0xFF);
        frame[5] = (byte) (value & 0xFF);
        appendCRC(frame, 6);
        return frame;
    }

    @Override
    public byte[] buildWriteMultipleRegistersFrame(int slaveId, int address, int[] values) {
        if (!ModbusValidation.isValidSlaveId(slaveId, ModbusProtocol.RTU)) return null;
        if (!ModbusValidation.isValidAddress(address)) return null;
        if (values == null || values.length == 0 || values.length > 123) return null;

        int byteCount = values.length * 2;
        byte[] frame = new byte[9 + byteCount];
        frame[0] = (byte) slaveId;
        frame[1] = 0x10;
        frame[2] = (byte) ((address >> 8) & 0xFF);
        frame[3] = (byte) (address & 0xFF);
        frame[4] = (byte) ((values.length >> 8) & 0xFF);
        frame[5] = (byte) (values.length & 0xFF);
        frame[6] = (byte) byteCount;
        for (int i = 0; i < values.length; i++) {
            frame[7 + i * 2] = (byte) ((values[i] >> 8) & 0xFF);
            frame[8 + i * 2] = (byte) (values[i] & 0xFF);
        }
        appendCRC(frame, 7 + byteCount);
        return frame;
    }

    @Override
    public byte[] buildWriteMultipleCoilsFrame(int slaveId, int address, boolean[] values) {
        if (!ModbusValidation.isValidSlaveId(slaveId, ModbusProtocol.RTU)) return null;
        if (!ModbusValidation.isValidAddress(address)) return null;
        if (values == null || values.length == 0 || values.length > 1968) return null;

        int byteCount = (values.length + 7) / 8;
        byte[] frame = new byte[9 + byteCount];
        frame[0] = (byte) slaveId;
        frame[1] = 0x0F;
        frame[2] = (byte) ((address >> 8) & 0xFF);
        frame[3] = (byte) (address & 0xFF);
        frame[4] = (byte) ((values.length >> 8) & 0xFF);
        frame[5] = (byte) (values.length & 0xFF);
        frame[6] = (byte) byteCount;
        for (int i = 0; i < values.length; i++) {
            if (values[i]) {
                frame[7 + (i / 8)] |= (1 << (i % 8));
            }
        }
        appendCRC(frame, 7 + byteCount);
        return frame;
    }

    @Override
    public int getExpectedResponseLength(int functionCode, int quantity) {
        switch (functionCode) {
            case 0x01: case 0x02:
                return 5 + (quantity + 7) / 8; // slave + func + count + data + CRC(2)
            case 0x03: case 0x04:
                return 5 + quantity * 2;
            case 0x05: case 0x06: case 0x0F: case 0x10:
                return 8;
            default:
                return 5;
        }
    }
}
