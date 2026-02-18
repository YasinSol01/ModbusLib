package com.itclink.modbuslib.engine;

public class BatchResult {
    public final int index;
    public final int[] data;
    public final String error;
    public final boolean success;

    public BatchResult(int index, int[] data) {
        this.index = index;
        this.data = data;
        this.error = null;
        this.success = true;
    }

    public BatchResult(int index, String error) {
        this.index = index;
        this.data = null;
        this.error = error;
        this.success = false;
    }
}
