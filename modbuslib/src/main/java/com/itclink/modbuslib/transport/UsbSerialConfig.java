package com.itclink.modbuslib.transport;

public class UsbSerialConfig {
    public static final int PARITY_NONE = 0;
    public static final int PARITY_ODD = 1;
    public static final int PARITY_EVEN = 2;

    public static final int FLOW_CONTROL_OFF = 0;
    public static final int FLOW_CONTROL_RTS_CTS = 1;
    public static final int FLOW_CONTROL_DSR_DTR = 2;
    public static final int FLOW_CONTROL_XON_XOFF = 3;

    private final int baudRate;
    private final int dataBits;
    private final int stopBits;
    private final int parity;
    private final int flowControl;

    private UsbSerialConfig(Builder builder) {
        this.baudRate = builder.baudRate;
        this.dataBits = builder.dataBits;
        this.stopBits = builder.stopBits;
        this.parity = builder.parity;
        this.flowControl = builder.flowControl;
    }

    public int getBaudRate() { return baudRate; }
    public int getDataBits() { return dataBits; }
    public int getStopBits() { return stopBits; }
    public int getParity() { return parity; }
    public int getFlowControl() { return flowControl; }

    public static UsbSerialConfig defaults() {
        return new Builder().build();
    }

    public static class Builder {
        private int baudRate = 9600;
        private int dataBits = 8;
        private int stopBits = 1;
        private int parity = PARITY_EVEN;
        private int flowControl = FLOW_CONTROL_OFF;

        public Builder baudRate(int baudRate) { this.baudRate = baudRate; return this; }
        public Builder dataBits(int dataBits) { this.dataBits = dataBits; return this; }
        public Builder stopBits(int stopBits) { this.stopBits = stopBits; return this; }
        public Builder parity(int parity) { this.parity = parity; return this; }
        public Builder flowControl(int flowControl) { this.flowControl = flowControl; return this; }

        public UsbSerialConfig build() { return new UsbSerialConfig(this); }
    }
}
