package com.itclink.modbuslib.transport;

import com.itclink.modbuslib.connection.ConnectionState;

public interface TransportListener {
    void onDataReceived(byte[] data);
    void onStateChanged(ConnectionState newState, String message);
    void onError(String error, Throwable cause);
}
