package com.itclink.modbuslib.callback;

import com.itclink.modbuslib.engine.BatchResult;

public interface ModbusBatchCallback {
    void onBatchSuccess(BatchResult[] results);
    void onError(String error);
}
