package com.itclink.modbuslib.connection;

/**
 * USB device information model with chip type detection.
 */
public class UsbDeviceInfo {
    private final int vendorId;
    private final int productId;
    private final String deviceName;
    private final String chipType;

    public UsbDeviceInfo(int vendorId, int productId, String deviceName) {
        this.vendorId = vendorId;
        this.productId = productId;
        this.deviceName = deviceName;
        this.chipType = detectChipType(vendorId, productId);
    }

    public int getVendorId() { return vendorId; }
    public int getProductId() { return productId; }
    public String getDeviceName() { return deviceName; }
    public String getChipType() { return chipType; }

    public static boolean isKnownUsbToSerial(int vendorId, int productId) {
        return detectChipType(vendorId, productId) != null;
    }

    public static String detectChipType(int vendorId, int productId) {
        // FTDI FT232
        if (vendorId == 0x0403 && productId == 0x6001) return "FTDI";
        // CP210x
        if (vendorId == 0x10C4 && productId == 0xEA60) return "CP210x";
        // PL2303
        if (vendorId == 0x067B && productId == 0x2303) return "PL2303";
        // CH340
        if (vendorId == 0x1A86 && productId == 0x7523) return "CH340";
        // Built-in serial
        if (vendorId == 0x1A86 && productId == 0x55D2) return "CH34x_BuiltIn";
        return null;
    }

    @Override
    public String toString() {
        return String.format("USB[%s VID=0x%04X PID=0x%04X %s]",
                chipType != null ? chipType : "Unknown", vendorId, productId, deviceName);
    }
}
