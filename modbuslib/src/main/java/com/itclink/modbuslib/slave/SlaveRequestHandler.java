package com.itclink.modbuslib.slave;

/**
 * Optional callback interface for custom slave request handling.
 * Implement this to intercept requests before they are processed by the default handler.
 * Return true to indicate the request was handled, false to use default RegisterMap behavior.
 */
public interface SlaveRequestHandler {

    /**
     * Called before a read request is processed.
     * Use this to update register values dynamically (e.g., read from a sensor).
     *
     * @param functionCode FC01/FC02/FC03/FC04
     * @param address      start address
     * @param quantity     number of registers/coils
     * @return true if handled (response will be built from RegisterMap), false to reject
     */
    default boolean onBeforeRead(int functionCode, int address, int quantity) {
        return true;
    }

    /**
     * Called after a write request modifies the RegisterMap.
     *
     * @param functionCode FC05/FC06/FC0F/FC10
     * @param address      start address
     * @param quantity     number of registers/coils written
     */
    default void onAfterWrite(int functionCode, int address, int quantity) {
        // Default: do nothing
    }

    /**
     * Called when a slave event occurs (connect, disconnect, error).
     */
    default void onSlaveEvent(SlaveEvent event, String message) {
        // Default: do nothing
    }

    enum SlaveEvent {
        CLIENT_CONNECTED,
        CLIENT_DISCONNECTED,
        REQUEST_RECEIVED,
        RESPONSE_SENT,
        ERROR
    }
}
