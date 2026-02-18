package com.itclink.modbuslib.slave;

import com.itclink.modbuslib.protocol.ModbusError;
import com.itclink.modbuslib.protocol.ModbusProtocol;
import com.itclink.modbuslib.protocol.ModbusRtuFrameBuilder;
import com.itclink.modbuslib.util.ModbusLog;

/**
 * Core engine for processing incoming Modbus requests and building responses.
 * Works with both TCP and RTU protocols.
 */
public class SlaveEngine {

    private static final String TAG = "SlaveEngine";

    private final int slaveId;
    private final RegisterMap registerMap;
    private final ModbusProtocol protocol;
    private volatile SlaveRequestHandler handler;

    // RTU CRC helper
    private final ModbusRtuFrameBuilder rtuFrameBuilder;

    // Stats
    private volatile long requestCount = 0;
    private volatile long errorCount = 0;

    public SlaveEngine(int slaveId, RegisterMap registerMap, ModbusProtocol protocol) {
        this.slaveId = slaveId;
        this.registerMap = registerMap;
        this.protocol = protocol;
        this.rtuFrameBuilder = (protocol == ModbusProtocol.RTU) ? new ModbusRtuFrameBuilder() : null;
    }

    public void setHandler(SlaveRequestHandler handler) {
        this.handler = handler;
    }

    /**
     * Process an incoming request frame and return the response frame.
     * For TCP: frame includes MBAP header (6 bytes) + Unit ID + PDU.
     * For RTU: frame includes Slave ID + PDU + CRC (2 bytes).
     *
     * @return response frame, or null if the request is not for this slave
     */
    public byte[] processRequest(byte[] frame) {
        if (frame == null || frame.length < 4) return null;

        try {
            if (protocol == ModbusProtocol.TCP) {
                return processTcpRequest(frame);
            } else {
                return processRtuRequest(frame);
            }
        } catch (Exception e) {
            ModbusLog.e(TAG, "Error processing request: " + e.getMessage());
            errorCount++;
            return null;
        }
    }

    // ===== TCP =====

    private byte[] processTcpRequest(byte[] frame) {
        if (frame.length < 8) return null; // MBAP(6) + UnitID(1) + FC(1) minimum

        // Parse MBAP header
        int transactionId = ((frame[0] & 0xFF) << 8) | (frame[1] & 0xFF);
        int protocolId = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
        if (protocolId != 0) return null; // Not Modbus

        int unitId = frame[6] & 0xFF;
        if (unitId != slaveId && unitId != 0) return null; // Not for us (0 = broadcast)

        // Extract PDU (after Unit ID)
        int functionCode = frame[7] & 0xFF;
        byte[] pdu = new byte[frame.length - 7];
        System.arraycopy(frame, 7, pdu, 0, pdu.length);

        // Process and build response PDU
        byte[] responsePdu = processFunction(functionCode, pdu);
        if (responsePdu == null) return null;

        // Build TCP response: MBAP + Unit ID + response PDU
        int responseLength = 1 + responsePdu.length; // Unit ID + PDU
        byte[] response = new byte[6 + responseLength];
        response[0] = (byte) ((transactionId >> 8) & 0xFF);
        response[1] = (byte) (transactionId & 0xFF);
        response[2] = 0; // Protocol ID
        response[3] = 0;
        response[4] = (byte) ((responseLength >> 8) & 0xFF);
        response[5] = (byte) (responseLength & 0xFF);
        response[6] = (byte) unitId;
        System.arraycopy(responsePdu, 0, response, 7, responsePdu.length);

        requestCount++;
        return response;
    }

    // ===== RTU =====

    private byte[] processRtuRequest(byte[] frame) {
        if (frame.length < 4) return null;

        // Verify CRC
        if (!rtuFrameBuilder.verifyCRC(frame)) {
            ModbusLog.w(TAG, "CRC verification failed");
            errorCount++;
            return null;
        }

        int reqSlaveId = frame[0] & 0xFF;
        if (reqSlaveId != slaveId && reqSlaveId != 0) return null; // Not for us

        int functionCode = frame[1] & 0xFF;
        // PDU = FC + data (without slave ID and CRC)
        byte[] pdu = new byte[frame.length - 3]; // remove slave ID(1) + CRC(2)
        System.arraycopy(frame, 1, pdu, 0, pdu.length);

        byte[] responsePdu = processFunction(functionCode, pdu);
        if (responsePdu == null) return null;

        // Build RTU response: Slave ID + response PDU + CRC
        byte[] responseWithoutCrc = new byte[1 + responsePdu.length];
        responseWithoutCrc[0] = (byte) slaveId;
        System.arraycopy(responsePdu, 0, responseWithoutCrc, 1, responsePdu.length);

        requestCount++;
        return rtuFrameBuilder.buildFrameWithCRC(responseWithoutCrc);
    }

    // ===== FUNCTION PROCESSING =====

    private byte[] processFunction(int functionCode, byte[] pdu) {
        switch (functionCode) {
            case 0x01: return handleReadCoils(pdu);
            case 0x02: return handleReadDiscreteInputs(pdu);
            case 0x03: return handleReadHoldingRegisters(pdu);
            case 0x04: return handleReadInputRegisters(pdu);
            case 0x05: return handleWriteSingleCoil(pdu);
            case 0x06: return handleWriteSingleRegister(pdu);
            case 0x0F: return handleWriteMultipleCoils(pdu);
            case 0x10: return handleWriteMultipleRegisters(pdu);
            default:
                return buildErrorResponse(functionCode, 0x01); // Illegal function
        }
    }

    // FC01 - Read Coils
    private byte[] handleReadCoils(byte[] pdu) {
        if (pdu.length < 5) return buildErrorResponse(0x01, 0x03);
        int address = ((pdu[1] & 0xFF) << 8) | (pdu[2] & 0xFF);
        int quantity = ((pdu[3] & 0xFF) << 8) | (pdu[4] & 0xFF);

        if (quantity < 1 || quantity > 2000) return buildErrorResponse(0x01, 0x03);

        SlaveRequestHandler h = handler;
        if (h != null && !h.onBeforeRead(0x01, address, quantity)) {
            return buildErrorResponse(0x01, 0x02);
        }

        try {
            boolean[] values = registerMap.getCoils(address, quantity);
            return buildReadCoilsResponse(0x01, values);
        } catch (IllegalArgumentException e) {
            return buildErrorResponse(0x01, 0x02); // Illegal data address
        }
    }

    // FC02 - Read Discrete Inputs
    private byte[] handleReadDiscreteInputs(byte[] pdu) {
        if (pdu.length < 5) return buildErrorResponse(0x02, 0x03);
        int address = ((pdu[1] & 0xFF) << 8) | (pdu[2] & 0xFF);
        int quantity = ((pdu[3] & 0xFF) << 8) | (pdu[4] & 0xFF);

        if (quantity < 1 || quantity > 2000) return buildErrorResponse(0x02, 0x03);

        SlaveRequestHandler h = handler;
        if (h != null && !h.onBeforeRead(0x02, address, quantity)) {
            return buildErrorResponse(0x02, 0x02);
        }

        try {
            boolean[] values = registerMap.getDiscreteInputs(address, quantity);
            return buildReadCoilsResponse(0x02, values);
        } catch (IllegalArgumentException e) {
            return buildErrorResponse(0x02, 0x02);
        }
    }

    // FC03 - Read Holding Registers
    private byte[] handleReadHoldingRegisters(byte[] pdu) {
        if (pdu.length < 5) return buildErrorResponse(0x03, 0x03);
        int address = ((pdu[1] & 0xFF) << 8) | (pdu[2] & 0xFF);
        int quantity = ((pdu[3] & 0xFF) << 8) | (pdu[4] & 0xFF);

        if (quantity < 1 || quantity > 125) return buildErrorResponse(0x03, 0x03);

        SlaveRequestHandler h = handler;
        if (h != null && !h.onBeforeRead(0x03, address, quantity)) {
            return buildErrorResponse(0x03, 0x02);
        }

        try {
            int[] values = registerMap.getHoldingRegisters(address, quantity);
            return buildReadRegistersResponse(0x03, values);
        } catch (IllegalArgumentException e) {
            return buildErrorResponse(0x03, 0x02);
        }
    }

    // FC04 - Read Input Registers
    private byte[] handleReadInputRegisters(byte[] pdu) {
        if (pdu.length < 5) return buildErrorResponse(0x04, 0x03);
        int address = ((pdu[1] & 0xFF) << 8) | (pdu[2] & 0xFF);
        int quantity = ((pdu[3] & 0xFF) << 8) | (pdu[4] & 0xFF);

        if (quantity < 1 || quantity > 125) return buildErrorResponse(0x04, 0x03);

        SlaveRequestHandler h = handler;
        if (h != null && !h.onBeforeRead(0x04, address, quantity)) {
            return buildErrorResponse(0x04, 0x02);
        }

        try {
            int[] values = registerMap.getInputRegisters(address, quantity);
            return buildReadRegistersResponse(0x04, values);
        } catch (IllegalArgumentException e) {
            return buildErrorResponse(0x04, 0x02);
        }
    }

    // FC05 - Write Single Coil
    private byte[] handleWriteSingleCoil(byte[] pdu) {
        if (pdu.length < 5) return buildErrorResponse(0x05, 0x03);
        int address = ((pdu[1] & 0xFF) << 8) | (pdu[2] & 0xFF);
        int value = ((pdu[3] & 0xFF) << 8) | (pdu[4] & 0xFF);

        if (value != 0x0000 && value != 0xFF00) return buildErrorResponse(0x05, 0x03);

        try {
            registerMap.setCoil(address, value == 0xFF00);

            SlaveRequestHandler h = handler;
            if (h != null) h.onAfterWrite(0x05, address, 1);

            // Echo request as response
            return new byte[]{(byte) 0x05, pdu[1], pdu[2], pdu[3], pdu[4]};
        } catch (IllegalArgumentException e) {
            return buildErrorResponse(0x05, 0x02);
        }
    }

    // FC06 - Write Single Register
    private byte[] handleWriteSingleRegister(byte[] pdu) {
        if (pdu.length < 5) return buildErrorResponse(0x06, 0x03);
        int address = ((pdu[1] & 0xFF) << 8) | (pdu[2] & 0xFF);
        int value = ((pdu[3] & 0xFF) << 8) | (pdu[4] & 0xFF);

        try {
            registerMap.setHoldingRegister(address, value);

            SlaveRequestHandler h = handler;
            if (h != null) h.onAfterWrite(0x06, address, 1);

            // Echo request as response
            return new byte[]{(byte) 0x06, pdu[1], pdu[2], pdu[3], pdu[4]};
        } catch (IllegalArgumentException e) {
            return buildErrorResponse(0x06, 0x02);
        }
    }

    // FC0F - Write Multiple Coils
    private byte[] handleWriteMultipleCoils(byte[] pdu) {
        if (pdu.length < 6) return buildErrorResponse(0x0F, 0x03);
        int address = ((pdu[1] & 0xFF) << 8) | (pdu[2] & 0xFF);
        int quantity = ((pdu[3] & 0xFF) << 8) | (pdu[4] & 0xFF);
        int byteCount = pdu[5] & 0xFF;

        if (quantity < 1 || quantity > 1968) return buildErrorResponse(0x0F, 0x03);
        if (pdu.length < 6 + byteCount) return buildErrorResponse(0x0F, 0x03);

        try {
            boolean[] values = new boolean[quantity];
            for (int i = 0; i < quantity; i++) {
                int byteIndex = i / 8;
                int bitIndex = i % 8;
                values[i] = ((pdu[6 + byteIndex] >> bitIndex) & 1) == 1;
            }
            registerMap.setCoils(address, values);

            SlaveRequestHandler h = handler;
            if (h != null) h.onAfterWrite(0x0F, address, quantity);

            // Response: FC + address + quantity
            return new byte[]{(byte) 0x0F, pdu[1], pdu[2], pdu[3], pdu[4]};
        } catch (IllegalArgumentException e) {
            return buildErrorResponse(0x0F, 0x02);
        }
    }

    // FC10 - Write Multiple Registers
    private byte[] handleWriteMultipleRegisters(byte[] pdu) {
        if (pdu.length < 6) return buildErrorResponse(0x10, 0x03);
        int address = ((pdu[1] & 0xFF) << 8) | (pdu[2] & 0xFF);
        int quantity = ((pdu[3] & 0xFF) << 8) | (pdu[4] & 0xFF);
        int byteCount = pdu[5] & 0xFF;

        if (quantity < 1 || quantity > 123) return buildErrorResponse(0x10, 0x03);
        if (byteCount != quantity * 2) return buildErrorResponse(0x10, 0x03);
        if (pdu.length < 6 + byteCount) return buildErrorResponse(0x10, 0x03);

        try {
            int[] values = new int[quantity];
            for (int i = 0; i < quantity; i++) {
                int offset = 6 + i * 2;
                values[i] = ((pdu[offset] & 0xFF) << 8) | (pdu[offset + 1] & 0xFF);
            }
            registerMap.setHoldingRegisters(address, values);

            SlaveRequestHandler h = handler;
            if (h != null) h.onAfterWrite(0x10, address, quantity);

            // Response: FC + address + quantity
            return new byte[]{(byte) 0x10, pdu[1], pdu[2], pdu[3], pdu[4]};
        } catch (IllegalArgumentException e) {
            return buildErrorResponse(0x10, 0x02);
        }
    }

    // ===== RESPONSE BUILDERS =====

    private byte[] buildReadCoilsResponse(int functionCode, boolean[] values) {
        int byteCount = (values.length + 7) / 8;
        byte[] response = new byte[2 + byteCount]; // FC + byteCount + data
        response[0] = (byte) functionCode;
        response[1] = (byte) byteCount;

        for (int i = 0; i < values.length; i++) {
            if (values[i]) {
                int byteIndex = i / 8;
                int bitIndex = i % 8;
                response[2 + byteIndex] |= (1 << bitIndex);
            }
        }
        return response;
    }

    private byte[] buildReadRegistersResponse(int functionCode, int[] values) {
        int byteCount = values.length * 2;
        byte[] response = new byte[2 + byteCount]; // FC + byteCount + data
        response[0] = (byte) functionCode;
        response[1] = (byte) byteCount;

        for (int i = 0; i < values.length; i++) {
            response[2 + i * 2] = (byte) ((values[i] >> 8) & 0xFF);
            response[3 + i * 2] = (byte) (values[i] & 0xFF);
        }
        return response;
    }

    private byte[] buildErrorResponse(int functionCode, int exceptionCode) {
        errorCount++;
        return new byte[]{(byte) (functionCode | 0x80), (byte) exceptionCode};
    }

    // ===== STATS =====

    public long getRequestCount() { return requestCount; }
    public long getErrorCount() { return errorCount; }
    public int getSlaveId() { return slaveId; }
}
