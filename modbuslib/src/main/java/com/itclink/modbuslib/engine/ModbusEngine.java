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
                    synchronized (rtuBufferLock) { rtuBufferPosition = 0; }
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

    /** RTU: wait/notify with buffer size checking */
    private byte[] waitForRtuResponse(ModbusCommand command) {
        int expectedSize = frameBuilder.getExpectedResponseLength(command.functionCode, command.quantity);
        long deadline = System.currentTimeMillis() + timing.getResponseTimeoutMs();

        synchronized (rtuBufferLock) {
            while (System.currentTimeMillis() < deadline) {
                if (rtuBufferPosition >= expectedSize) {
                    byte[] response = new byte[rtuBufferPosition];
                    System.arraycopy(rtuResponseBuffer, 0, response, 0, rtuBufferPosition);
                    return response;
                }
                try {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining > 0) rtuBufferLock.wait(remaining);
                    else break;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            // Timeout - return whatever we have if it's a valid minimum
            if (rtuBufferPosition >= 5) {
                byte[] response = new byte[rtuBufferPosition];
                System.arraycopy(rtuResponseBuffer, 0, response, 0, rtuBufferPosition);
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
