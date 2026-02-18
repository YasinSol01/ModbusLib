package com.itclink.modbuslib.util;

import com.itclink.modbuslib.protocol.ModbusProtocol;

/**
 * Modbus-specific validation utilities.
 */
public class ModbusValidation {

    public static boolean isValidSlaveId(int slaveId, ModbusProtocol protocol) {
        if (protocol == ModbusProtocol.RTU) {
            return slaveId >= 1 && slaveId <= 247;
        } else {
            return slaveId >= 0 && slaveId <= 255;
        }
    }

    public static boolean isValidAddress(int address) {
        return address >= 0 && address <= 65535;
    }

    public static boolean isValidQuantity(int quantity, int functionCode) {
        switch (functionCode) {
            case 0x01: case 0x02:
                return quantity >= 1 && quantity <= 2000;
            case 0x03: case 0x04:
                return quantity >= 1 && quantity <= 125;
            default:
                return quantity >= 1;
        }
    }

    public static boolean isValidRegisterValue(int value) {
        return value >= 0 && value <= 65535;
    }

    public static boolean isValidFunctionCode(int functionCode) {
        int base = functionCode & 0x7F;
        switch (base) {
            case 0x01: case 0x02: case 0x03: case 0x04:
            case 0x05: case 0x06: case 0x0F: case 0x10:
                return true;
            default:
                return false;
        }
    }

    public static boolean isValidIPAddress(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isValidPort(int port) {
        return port >= 1 && port <= 65535;
    }
}
