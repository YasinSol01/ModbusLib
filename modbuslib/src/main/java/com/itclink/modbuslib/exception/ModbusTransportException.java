package com.itclink.modbuslib.exception;

public class ModbusTransportException extends ModbusException {
    public ModbusTransportException(String message) {
        super(message);
    }

    public ModbusTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
