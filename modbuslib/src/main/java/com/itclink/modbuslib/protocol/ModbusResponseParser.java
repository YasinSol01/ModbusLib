package com.itclink.modbuslib.protocol;

import com.itclink.modbuslib.command.ModbusCommand;

public interface ModbusResponseParser {
    boolean isValidFrame(byte[] frame);
    boolean isErrorResponse(byte[] frame);
    int getErrorCode(byte[] frame);
    boolean matchesCommand(byte[] response, ModbusCommand command);
    int[] extractRegisters(byte[] response);
    int[] extractCoils(byte[] response, int quantity);
    int[] extractData(byte[] response, ModbusCommand command);
}
