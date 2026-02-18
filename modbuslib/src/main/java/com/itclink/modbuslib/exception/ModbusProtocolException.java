package com.itclink.modbuslib.exception;

public class ModbusProtocolException extends ModbusException {
    public ModbusProtocolException(String message) {
        super(message);
    }

    public ModbusProtocolException(String message, int errorCode) {
        super(message, errorCode);
    }
}
