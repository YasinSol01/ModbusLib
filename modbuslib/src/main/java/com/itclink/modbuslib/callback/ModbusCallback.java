package com.itclink.modbuslib.callback;

public interface ModbusCallback {
    void onSuccess(int[] data);
    void onError(String error);
}
