package com.itclink.modbuslib.exception;

public class ModbusException extends Exception {
    private final int errorCode;

    public ModbusException(String message) {
        super(message);
        this.errorCode = -1;
    }

    public ModbusException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ModbusException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = -1;
    }

    public int getErrorCode() { return errorCode; }
}
