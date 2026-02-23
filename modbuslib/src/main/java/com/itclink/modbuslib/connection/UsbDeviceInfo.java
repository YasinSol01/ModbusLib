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
        // FTDI
        if (vendorId == 0x0403 && productId == 0x6001) return "FTDI";
        if (vendorId == 0x0403 && productId == 0x6015) return "FTDI_FT231X";
        // Silicon Labs CP210x
        if (vendorId == 0x10C4 && productId == 0xEA60) return "CP210x";
        if (vendorId == 0x10C4 && productId == 0xEA70) return "CP2102N";
        // Prolific
        if (vendorId == 0x067B && productId == 0x2303) return "PL2303";
        // WCH single-port
        if (vendorId == 0x1A86 && productId == 0x7523) return "CH340";
        if (vendorId == 0x1A86 && productId == 0x7522) return "CH340K";
        if (vendorId == 0x1A86 && productId == 0x5523) return "CH341";
        // WCH built-in (IXHUB 7" = 0x55D2)
        if (vendorId == 0x1A86 && productId == 0x55D2) return "CH34x_BuiltIn";
        // WCH multi-port built-in variants (IXHUB 10" candidates)
        if (vendorId == 0x1A86 && productId == 0x55D3) return "CH343_BuiltIn";
        if (vendorId == 0x1A86 && productId == 0x55D4) return "CH9102_BuiltIn";
        if (vendorId == 0x1A86 && productId == 0x55D5) return "CH9143_BuiltIn";
        if (vendorId == 0x1A86 && productId == 0x55D7) return "CH9101_BuiltIn";
        if (vendorId == 0x1A86 && productId == 0x55D9) return "CH9344_BuiltIn";
        if (vendorId == 0x1A86 && productId == 0x7530) return "CH348_BuiltIn";
        return null;
    }

    @Override
    public String toString() {
        return String.format("USB[%s VID=0x%04X PID=0x%04X %s]",
                chipType != null ? chipType : "Unknown", vendorId, productId, deviceName);
    }
}
