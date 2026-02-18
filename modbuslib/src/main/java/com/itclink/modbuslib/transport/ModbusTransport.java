package com.itclink.modbuslib.transport;

import com.itclink.modbuslib.connection.ConnectionState;
import com.itclink.modbuslib.exception.ModbusTransportException;

public interface ModbusTransport {
    void connect() throws ModbusTransportException;
    void disconnect();
    boolean isConnected();
    void send(byte[] data) throws ModbusTransportException;
    void setTransportListener(TransportListener listener);
    ConnectionState getState();
    String getTransportType();
}
