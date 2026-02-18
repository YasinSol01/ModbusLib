package com.itclink.modbuslib.connection;

public enum ConnectionState {
    DISCONNECTED("Disconnected", false),
    CONNECTING("Connecting...", false),
    CONNECTED("Connected", true),
    RECONNECTING("Reconnecting...", false),
    ERROR("Connection Error", false);

    private final String displayName;
    private final boolean connected;

    ConnectionState(String displayName, boolean connected) {
        this.displayName = displayName;
        this.connected = connected;
    }

    public String getDisplayName() { return displayName; }
    public boolean isConnected() { return connected; }
}
