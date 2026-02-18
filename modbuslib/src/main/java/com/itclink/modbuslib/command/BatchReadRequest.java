package com.itclink.modbuslib.command;

public class BatchReadRequest {
    public final int[] slaveIds;
    public final int[] addresses;
    public final int[] quantities;

    public BatchReadRequest(int[] slaveIds, int[] addresses, int[] quantities) {
        this.slaveIds = slaveIds;
        this.addresses = addresses;
        this.quantities = quantities;
    }
}
