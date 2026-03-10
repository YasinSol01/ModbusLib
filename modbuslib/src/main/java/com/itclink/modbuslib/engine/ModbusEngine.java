package com.itclink.modbuslib.engine;

import com.itclink.modbuslib.callback.ModbusBatchCallback;
import com.itclink.modbuslib.callback.ModbusCallback;
import com.itclink.modbuslib.command.BatchReadRequest;
import com.itclink.modbuslib.command.ModbusCommand;
import com.itclink.modbuslib.exception.ModbusTransportException;
import com.itclink.modbuslib.protocol.ModbusError;
import com.itclink.modbuslib.protocol.ModbusFrameBuilder;
import com.itclink.modbuslib.protocol.ModbusProtocol;
import com.itclink.modbuslib.protocol.ModbusResponseParser;
import com.itclink.modbuslib.protocol.ModbusTcpFrameBuilder;
import com.itclink.modbuslib.protocol.TcpResponseParser;
import com.itclink.modbuslib.transport.ModbusTransport;
import com.itclink.modbuslib.transport.TransportListener;
import com.itclink.modbuslib.connection.ConnectionState;
import com.itclink.modbuslib.util.ModbusLog;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core Modbus command engine with priority queue, retry logic, and response handling.
 * Merged from both RTU and TCP ModbusManager implementations.
 * Uses wait/notify for CPU-efficient response waiting.
 */
public class ModbusEngine implements TransportListener {

    private static final String TAG = "Engine";
    private static final int MAX_RTU_RESPONSE_SIZE = 256;
    // Modbus RTU inter-frame gap: 3.5 char times. At 9600 baud (11 bits/char) = ~4ms.
    // Use 20ms to be safe across baud rates. Once minimum data bytes arrive, wait this long
    // for CRC bytes before accepting the frame as-is.
    private static final long RTU_INTER_FRAME_GAP_MS = 20;

    private final ModbusProtocol protocol;
    private final ModbusFrameBuilder frameBuilder;
    private final ModbusResponseParser responseParser;
    private final ModbusTimingConfig timing;
    private ModbusTransport transport;

    // Priority queues
    private final BlockingQueue<ModbusCommand> highPriorityQueue;
    private final BlockingQueue<ModbusCommand> normalPriorityQueue;
    private Thread queueProcessorThread;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);

    // Response handling (wait/notify based)
    private volatile ModbusCommand currentCommand;
    private volatile byte[] pendingResponse = null;
    private volatile int expectedTransactionId = -1;
    private final Object responseLock = new Object();

    // RTU response buffer (for byte-stream assembly)
    private final byte[] rtuResponseBuffer = new byte[MAX_RTU_RESPONSE_SIZE];
    private volatile int rtuBufferPosition = 0;
    private final Object rtuBufferLock = new Object();

    // RS485 echo suppression: strip echo of sent request from start of receive buffer
    private volatile byte[] lastSentFrame  = null;
    private volatile boolean echoStripped  = false;

    // Statistics
    private final AtomicInteger totalCommands = new AtomicInteger(0);
    private final AtomicInteger successfulCommands = new AtomicInteger(0);
    private final AtomicInteger consecutiveErrors = new AtomicInteger(0);

    public ModbusEngine(ModbusProtocol protocol, ModbusFrameBuilder frameBuilder,
                        ModbusResponseParser responseParser, ModbusTimingConfig timing) {
        this.protocol = protocol;
        this.frameBuilder = frameBuilder;
        this.responseParser = responseParser;
        this.timing = timing;
        this.highPriorityQueue = new LinkedBlockingQueue<>(timing.getMaxQueueSize());
        this.normalPriorityQueue = new LinkedBlockingQueue<>(timing.getMaxQueueSize());
    }

    public void setTransport(ModbusTransport transport) {
        this.transport = transport;
        transport.setTransportListener(this);
    }

    public void start() {
        if (isRunning.get()) return;
        isRunning.set(true);
        queueProcessorThread = new Thread(this::processCommands, "ModbusEngine");
        queueProcessorThread.setDaemon(true);
        queueProcessorThread.start();
    }

    public void stop() {
        isRunning.set(false);
        synchronized (responseLock) { responseLock.notifyAll(); }
        if (queueProcessorThread != null) queueProcessorThread.interrupt();
        highPriorityQueue.clear();
        normalPriorityQueue.clear();
    }

    public void pause() { isPaused.set(true); }
    public void resume() { isPaused.set(false); }

    // ===== QUEUE PROCESSOR =====

    private void processCommands() {
        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
            try {
                if (isPaused.get()) { Thread.sleep(10); continue; }

                ModbusCommand command = highPriorityQueue.poll();
                if (command == null) {
                    command = normalPriorityQueue.poll(100, TimeUnit.MILLISECONDS);
                }

                if (command != null) {
                    executeCommand(command);
                    Thread.sleep(timing.getInterFrameDelayMs());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                ModbusLog.e(TAG, "Queue processor error: " + e.getMessage());
                consecutiveErrors.incrementAndGet();
                try { Thread.sleep(20); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); break;
                }
            }
        }
    }

    private void executeCommand(ModbusCommand command) {
        if (transport == null || !transport.isConnected()) {
            if (command.callback != null) command.callback.onError("Not connected");
            return;
        }

        currentCommand = command;
        totalCommands.incrementAndGet();

        byte[] frame = buildFrame(command);
        if (frame == null) {
            if (command.callback != null) command.callback.onError("Failed to build frame");
            currentCommand = null;
            return;
        }

        // Extract transaction ID for TCP
        int txId = -1;
        if (protocol == ModbusProtocol.TCP && frameBuilder instanceof ModbusTcpFrameBuilder) {
            txId = ModbusTcpFrameBuilder.getTransactionId(frame);
        }

        int attempt = 0;
        while (attempt <= timing.getMaxRetries()) {
            try {
                // Clear response state
                synchronized (responseLock) {
                    pendingResponse = null;
                    expectedTransactionId = txId;
                }
                if (protocol == ModbusProtocol.RTU) {
                    synchronized (rtuBufferLock) {
                        rtuBufferPosition = 0;
                        lastSentFrame = frame;
                        echoStripped  = false;
                    }
                }

                transport.send(frame);

                // Wait for response
                byte[] response = waitForResponse(command);

                if (response != null) {
                    // For TCP, verify transaction ID
                    if (protocol == ModbusProtocol.TCP && responseParser instanceof TcpResponseParser) {
                        if (txId >= 0 && !((TcpResponseParser) responseParser).matchesTransactionId(response, txId)) {
                            attempt++;
                            continue;
                        }
                    }

                    if (responseParser.isErrorResponse(response)) {
                        int errorCode = responseParser.getErrorCode(response);
                        if (command.callback != null) {
                            command.callback.onError("Modbus error: " + ModbusError.getDescription(errorCode));
                        }
                        currentCommand = null;
                        return;
                    }

                    if (responseParser.matchesCommand(response, command)) {
                        int[] data = responseParser.extractData(response, command);
                        successfulCommands.incrementAndGet();
                        consecutiveErrors.set(0);
                        if (command.callback != null) command.callback.onSuccess(data);
                        currentCommand = null;
                        return;
                    }
                }

                attempt++;
                if (attempt <= timing.getMaxRetries()) {
                    Thread.sleep(timing.getRetryDelayMs());
                }
            } catch (ModbusTransportException e) {
                ModbusLog.e(TAG, "Transport error: " + e.getMessage());
                attempt++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // All retries failed
        consecutiveErrors.incrementAndGet();
        if (command.callback != null) {
            command.callback.onError("Command failed after " + timing.getMaxRetries() + " retries");
        }
        currentCommand = null;
    }

    private byte[] buildFrame(ModbusCommand command) {
        switch (command.functionCode) {
            case 0x01: case 0x02: case 0x03: case 0x04:
                return frameBuilder.buildReadFrame(command.slaveId, command.functionCode, command.address, command.quantity);
            case 0x05:
                return frameBuilder.buildWriteSingleCoilFrame(command.slaveId, command.address, command.values != null && command.values[0] != 0);
            case 0x06:
                return frameBuilder.buildWriteSingleRegisterFrame(command.slaveId, command.address, command.values != null ? command.values[0] : 0);
            case 0x0F:
                if (command.values == null) return null;
                boolean[] coils = new boolean[command.values.length];
                for (int i = 0; i < command.values.length; i++) coils[i] = command.values[i] != 0;
                return frameBuilder.buildWriteMultipleCoilsFrame(command.slaveId, command.address, coils);
            case 0x10:
                return frameBuilder.buildWriteMultipleRegistersFrame(command.slaveId, command.address, command.values);
            default:
                return null;
        }
    }

    private byte[] waitForResponse(ModbusCommand command) {
        if (protocol == ModbusProtocol.TCP) {
            return waitForTcpResponse();
        } else {
            return waitForRtuResponse(command);
        }
    }

    /** TCP: wait/notify based - very CPU efficient */
    private byte[] waitForTcpResponse() {
        synchronized (responseLock) {
            if (pendingResponse != null) {
                byte[] resp = pendingResponse;
                pendingResponse = null;
                return resp;
            }
            try {
                responseLock.wait(timing.getResponseTimeoutMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            byte[] resp = pendingResponse;
            pendingResponse = null;
            return resp;
        }
    }

    /** RTU: wait/notify with dynamic frame size based on actual byteCount in response */
    private byte[] waitForRtuResponse(ModbusCommand command) {
        int fc = command.functionCode;
        boolean isReadCommand = (fc >= 0x01 && fc <= 0x04);
        long deadline = System.currentTimeMillis() + timing.getResponseTimeoutMs();

        synchronized (rtuBufferLock) {
            int expectedSize = 0; // determined dynamically; used to trim trailing RS485 echo bytes

            // === RS485 Echo Suppression ===
            // On RS485 half-duplex, master receives its own TX as echo.
            // Stale bytes from the previous command may also arrive BEFORE the current echo
            // (e.g., a buggy/slow slave sends extra bytes that arrive just as master sends the next request).
            // Solution: wait briefly for echo bytes then search the ENTIRE buffer for the echo frame,
            // discarding everything up to and including it.
            if (!echoStripped) {
                byte[] sent = lastSentFrame;
                if (sent != null) {
                    // Wait until buffer has enough bytes to search for echo (or brief timeout)
                    long echoDeadline = System.currentTimeMillis() + RTU_INTER_FRAME_GAP_MS * 2;
                    while (System.currentTimeMillis() < echoDeadline && rtuBufferPosition < sent.length) {
                        try {
                            long wait = echoDeadline - System.currentTimeMillis();
                            if (wait > 0) rtuBufferLock.wait(wait);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return null;
                        }
                    }
                    // Search for sent frame anywhere in buffer (not just at position 0)
                    int echoStart = -1;
                    outer:
                    for (int i = 0; i <= rtuBufferPosition - sent.length; i++) {
                        for (int j = 0; j < sent.length; j++) {
                            if (rtuResponseBuffer[i + j] != sent[j]) continue outer;
                        }
                        echoStart = i;
                        break;
                    }
                    if (echoStart >= 0) {
                        int afterEcho = echoStart + sent.length;
                        int remaining = rtuBufferPosition - afterEcho;
                        System.arraycopy(rtuResponseBuffer, afterEcho, rtuResponseBuffer, 0, remaining);
                        rtuBufferPosition = remaining;
                    }
                }
                echoStripped = true;
            }

            // === Stale Frame Discard ===
            // After echo strip, buffer may still contain late responses from a PREVIOUS slave
            // (e.g., a slow/buggy slave whose response arrived after we already started the next
            // poll cycle). These must be discarded before reading the actual response.
            // Detection: first byte must match command.slaveId; if not, skip the stale frame.
            byte expId = (byte) command.slaveId;
            staleLoop:
            while (System.currentTimeMillis() < deadline) {
                // Wait until at least 1 byte is available
                while (rtuBufferPosition == 0) {
                    long w = deadline - System.currentTimeMillis();
                    if (w <= 0) break staleLoop;
                    try { rtuBufferLock.wait(w); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
                }
                if (rtuResponseBuffer[0] == expId) break; // correct slave — proceed

                // Wrong slave ID: need at least 3 bytes to determine stale frame size
                while (rtuBufferPosition < 3) {
                    long w = deadline - System.currentTimeMillis();
                    if (w <= 0) { rtuBufferPosition = 0; break staleLoop; }
                    try { rtuBufferLock.wait(w); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
                }
                int sfc = rtuResponseBuffer[1] & 0xFF;
                int staleSize = ((sfc & 0x80) != 0) ? 5                   // exception frame
                              : (sfc <= 4)           ? (5 + (rtuResponseBuffer[2] & 0xFF))  // read response
                              :                        8;                  // write echo response
                // Wait for full stale frame before discarding
                while (rtuBufferPosition < staleSize) {
                    long w = deadline - System.currentTimeMillis();
                    if (w <= 0) break;
                    try { rtuBufferLock.wait(Math.min(RTU_INTER_FRAME_GAP_MS, w)); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
                }
                staleSize = Math.min(staleSize, rtuBufferPosition);
                ModbusLog.w(TAG, "RS485 stale frame: slaveId=" + (rtuResponseBuffer[0] & 0xFF)
                        + " expected=" + command.slaveId + " skip=" + staleSize + " bytes");
                int rem = rtuBufferPosition - staleSize;
                if (rem > 0) System.arraycopy(rtuResponseBuffer, staleSize, rtuResponseBuffer, 0, rem);
                rtuBufferPosition = rem;
            }

            if (isReadCommand) {
                // Phase 1: wait for at least 3 bytes (slaveId + fc + byteCount)
                while (System.currentTimeMillis() < deadline && rtuBufferPosition < 3) {
                    try {
                        long remaining = deadline - System.currentTimeMillis();
                        if (remaining > 0) rtuBufferLock.wait(remaining);
                        else break;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }

                if (rtuBufferPosition >= 3) {
                    // Determine actual expected frame size from response header
                    boolean isError = (rtuResponseBuffer[1] & 0x80) != 0;
                    int minDataSize; // minimum bytes to have all data (slaveId + fc + byteCount + data)
                    if (isError) {
                        expectedSize = 5; // slaveId + fc(error) + errorCode + CRC(2)
                        minDataSize = 3;  // slaveId + fc(error) + errorCode
                    } else {
                        int byteCount = rtuResponseBuffer[2] & 0xFF;
                        expectedSize = 5 + byteCount; // slaveId + fc + byteCount + data + CRC(2)
                        minDataSize = 3 + byteCount;  // slaveId + fc + byteCount + data (no CRC)
                        if (expectedSize > MAX_RTU_RESPONSE_SIZE) expectedSize = MAX_RTU_RESPONSE_SIZE;
                    }

                    // Phase 2: wait for full frame.
                    // Once we have at least the data bytes (minDataSize), switch to a short
                    // inter-frame gap timeout so we don't wait 500ms if CRC bytes are missing.
                    while (System.currentTimeMillis() < deadline && rtuBufferPosition < expectedSize) {
                        try {
                            long remaining = deadline - System.currentTimeMillis();
                            if (remaining <= 0) break;
                            long waitTime = (rtuBufferPosition >= minDataSize)
                                    ? Math.min(RTU_INTER_FRAME_GAP_MS, remaining)
                                    : remaining;
                            int posBefore = rtuBufferPosition;
                            rtuBufferLock.wait(waitTime);
                            // If we have data bytes and no new bytes arrived (wait timed out), accept frame
                            if (rtuBufferPosition >= minDataSize && rtuBufferPosition == posBefore) break;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return null;
                        }
                    }
                }
            } else {
                // Write commands (FC05/06/0F/10): byte[2] is not byteCount, use pre-computed size
                expectedSize = frameBuilder.getExpectedResponseLength(fc, command.quantity);
                while (System.currentTimeMillis() < deadline && rtuBufferPosition < expectedSize) {
                    try {
                        long remaining = deadline - System.currentTimeMillis();
                        if (remaining > 0) rtuBufferLock.wait(remaining);
                        else break;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }

            // Return EXACTLY expectedSize bytes to avoid trailing RS485 echo bytes corrupting CRC.
            // (RS485 half-duplex: slave response + next request's echo may arrive concatenated.)
            int returnSize = (expectedSize > 0)
                    ? Math.min(expectedSize, rtuBufferPosition)
                    : rtuBufferPosition;
            if (returnSize >= 5) {
                byte[] response = new byte[returnSize];
                System.arraycopy(rtuResponseBuffer, 0, response, 0, returnSize);
                return response;
            }
            return null;
        }
    }

    // ===== TransportListener callbacks =====

    @Override
    public void onDataReceived(byte[] data) {
        if (data == null || data.length == 0) return;

        if (protocol == ModbusProtocol.TCP) {
            // TCP: complete frames arrive from TcpTransport (MBAP assembly done there)
            synchronized (responseLock) {
                pendingResponse = data;
                responseLock.notifyAll();
            }
        } else {
            // RTU: accumulate bytes in buffer
            synchronized (rtuBufferLock) {
                if (rtuBufferPosition + data.length <= MAX_RTU_RESPONSE_SIZE) {
                    System.arraycopy(data, 0, rtuResponseBuffer, rtuBufferPosition, data.length);
                    rtuBufferPosition += data.length;
                    rtuBufferLock.notifyAll();
                } else {
                    ModbusLog.w(TAG, "RTU buffer overflow, resetting");
                    rtuBufferPosition = 0;
                }
            }
        }
    }

    @Override
    public void onStateChanged(ConnectionState newState, String message) {
        ModbusLog.i(TAG, "Transport state: " + message);
        if (!newState.isConnected()) {
            // Clear queues on disconnect
            synchronized (responseLock) { responseLock.notifyAll(); }
        }
    }

    @Override
    public void onError(String error, Throwable cause) {
        ModbusLog.e(TAG, "Transport error: " + error);
    }

    // ===== PUBLIC COMMAND METHODS =====

    public void addCommand(int slaveId, int functionCode, int address, int quantity,
                           int[] values, ModbusCallback callback, boolean highPriority) {
        ModbusCommand cmd = new ModbusCommand(slaveId, functionCode, address, quantity, values, callback, highPriority);
        BlockingQueue<ModbusCommand> queue = highPriority ? highPriorityQueue : normalPriorityQueue;
        if (!queue.offer(cmd)) {
            if (highPriority && callback != null) callback.onError("Queue full");
        }
    }

    // ===== BATCH READ =====

    public void batchRead(BatchReadRequest request, ModbusBatchCallback callback) {
        if (request.slaveIds.length != request.addresses.length ||
            request.addresses.length != request.quantities.length) {
            callback.onError("Batch parameter arrays must have same length");
            return;
        }

        int total = request.slaveIds.length;
        BatchResult[] results = new BatchResult[total];
        AtomicInteger completed = new AtomicInteger(0);

        for (int i = 0; i < total; i++) {
            final int idx = i;
            addCommand(request.slaveIds[i], 0x03, request.addresses[i], request.quantities[i], null,
                new ModbusCallback() {
                    @Override
                    public void onSuccess(int[] data) {
                        results[idx] = new BatchResult(idx, data);
                        if (completed.incrementAndGet() >= total) callback.onBatchSuccess(results);
                    }
                    @Override
                    public void onError(String error) {
                        results[idx] = new BatchResult(idx, error);
                        if (completed.incrementAndGet() >= total) callback.onBatchSuccess(results);
                    }
                }, false);
        }
    }

    // ===== SYNC API =====

    public int[] readRegistersSync(int slaveId, int functionCode, int address, int quantity) {
        final CountDownLatch latch = new CountDownLatch(1);
        final int[][] result = new int[1][];
        final boolean[] success = new boolean[1];

        addCommand(slaveId, functionCode, address, quantity, null, new ModbusCallback() {
            @Override
            public void onSuccess(int[] data) { result[0] = data; success[0] = true; latch.countDown(); }
            @Override
            public void onError(String error) { success[0] = false; latch.countDown(); }
        }, true);

        try {
            latch.await(timing.getResponseTimeoutMs() + 500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return success[0] ? result[0] : null;
    }

    public boolean writeRegisterSync(int slaveId, int functionCode, int address, int value) {
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] success = new boolean[1];

        int fc = (functionCode == 0x10) ? 0x10 : 0x06;
        addCommand(slaveId, fc, address, 1, new int[]{value}, new ModbusCallback() {
            @Override
            public void onSuccess(int[] data) { success[0] = true; latch.countDown(); }
            @Override
            public void onError(String error) { success[0] = false; latch.countDown(); }
        }, true);

        try {
            latch.await(timing.getResponseTimeoutMs() + 500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return success[0];
    }

    // ===== STATISTICS =====

    public String getStats() {
        return String.format("Commands: %d, Success: %d (%.1f%%), Errors: %d",
                totalCommands.get(), successfulCommands.get(),
                totalCommands.get() > 0 ? (100.0 * successfulCommands.get() / totalCommands.get()) : 0,
                consecutiveErrors.get());
    }

    public int getConsecutiveErrors() { return consecutiveErrors.get(); }
}
