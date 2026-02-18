package com.itclink.modbuslib.util;

/**
 * Utility methods for byte array operations.
 */
public class ByteUtils {

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        String clean = hex.replaceAll("\\s+", "");
        if (clean.length() % 2 != 0) return new byte[0];
        byte[] result = new byte[clean.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(clean.charAt(i * 2), 16);
            int low = Character.digit(clean.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) return new byte[0];
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }

    /** Convert unsigned 16-bit register value to signed */
    public static int toSigned16(int value) {
        return (value >= 32768) ? value - 65536 : value;
    }

    /** Convert signed value to unsigned 16-bit register value */
    public static int toUnsigned16(int value) {
        return (value < 0) ? value + 65536 : value;
    }
}
