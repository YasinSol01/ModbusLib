package com.itclink.modbuslib.transport;

import com.itclink.modbuslib.connection.ConnectionConfig;
import com.itclink.modbuslib.connection.ConnectionState;
import com.itclink.modbuslib.exception.ModbusTransportException;
import com.itclink.modbuslib.util.ModbusLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * TCP socket transport with MBAP frame assembly and auto-reconnect.
 * Extracted from MODBUS_PULL_TEST_siammeal ConnectionManager.
 */
public class TcpTransport implements ModbusTransport {

    private static final String TAG = "TcpTransport";
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int MBAP_HEADER_SIZE = 6;

    private final ConnectionConfig config;
    private volatile TransportListener listener;
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;

    private Socket socket;
    private OutputStream outputStream;
    private Thread readThread;
    private volatile boolean running = false;

    // Auto-reconnect
    private Thread reconnectThread;
    private volatile boolean reconnecting = false;
    private static final long RECONNECT_BASE_DELAY = 2000;
    private static final long RECONNECT_MAX_DELAY = 30000;

    public TcpTransport(ConnectionConfig config) {
        this.config = config;
    }

    @Override
    public void connect() throws ModbusTransportException {
        if (config.getHost() == null || config.getHost().isEmpty()) {
            throw new ModbusTransportException("Host not configured");
        }

        setState(ConnectionState.CONNECTING);
        try {
            socket = new Socket();
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            socket.setReuseAddress(true);
            socket.setSoTimeout(0); // Keep alive
            socket.setPerformancePreferences(1, 2, 0);
            socket.connect(new InetSocketAddress(config.getHost(), config.getPort()), CONNECT_TIMEOUT);

            outputStream = socket.getOutputStream();
            running = true;
            startReadThread();
            setState(ConnectionState.CONNECTED);
            ModbusLog.i(TAG, "Connected to " + config.getHost() + ":" + config.getPort());

        } catch (IOException e) {
            setState(ConnectionState.ERROR);
            if (config.isAutoReconnect()) {
                scheduleReconnect();
            }
            throw new ModbusTransportException("TCP connection failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() {
        running = false;
        reconnecting = false;

        if (reconnectThread != null) {
            reconnectThread.interrupt();
            reconnectThread = null;
        }

        if (readThread != null) {
            readThread.interrupt();
            readThread = null;
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            ModbusLog.w(TAG, "Error closing socket: " + e.getMessage());
        }
        socket = null;
        outputStream = null;
        setState(ConnectionState.DISCONNECTED);
    }

    @Override
    public boolean isConnected() {
        return state == ConnectionState.CONNECTED && socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public synchronized void send(byte[] data) throws ModbusTransportException {
        if (!isConnected() || outputStream == null) {
            throw new ModbusTransportException("Not connected");
        }
        try {
            outputStream.write(data);
            outputStream.flush();
        } catch (IOException e) {
            setState(ConnectionState.ERROR);
            if (config.isAutoReconnect()) scheduleReconnect();
            throw new ModbusTransportException("TCP send failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void setTransportListener(TransportListener listener) { this.listener = listener; }

    @Override
    public ConnectionState getState() { return state; }

    @Override
    public String getTransportType() { return "TCP"; }

    private void setState(ConnectionState newState) {
        ConnectionState old = this.state;
        this.state = newState;
        if (old != newState && listener != null) {
            listener.onStateChanged(newState, newState.getDisplayName());
        }
    }

    /**
     * Read thread with MBAP-based frame assembly.
     * Uses MBAP length field to determine complete frame size.
     */
    private void startReadThread() {
        readThread = new Thread(() -> {
            byte[] headerBuffer = new byte[MBAP_HEADER_SIZE];
            try {
                InputStream inputStream = socket.getInputStream();
                while (running && !Thread.currentThread().isInterrupted()) {
                    // Read MBAP header (6 bytes)
                    int read = readFully(inputStream, headerBuffer, 0, MBAP_HEADER_SIZE);
                    if (read < MBAP_HEADER_SIZE) {
                        if (running) {
                            ModbusLog.w(TAG, "Incomplete MBAP header, connection lost");
                            break;
                        }
                        return;
                    }

                    // Parse length from MBAP header (bytes 4-5)
                    int dataLength = ((headerBuffer[4] & 0xFF) << 8) | (headerBuffer[5] & 0xFF);
                    if (dataLength <= 0 || dataLength > 260) {
                        ModbusLog.w(TAG, "Invalid MBAP length: " + dataLength);
                        continue;
                    }

                    // Read remaining data (Unit ID + PDU)
                    byte[] completeFrame = new byte[MBAP_HEADER_SIZE + dataLength];
                    System.arraycopy(headerBuffer, 0, completeFrame, 0, MBAP_HEADER_SIZE);
                    read = readFully(inputStream, completeFrame, MBAP_HEADER_SIZE, dataLength);
                    if (read < dataLength) {
                        ModbusLog.w(TAG, "Incomplete frame data");
                        break;
                    }

                    // Deliver complete frame
                    if (listener != null) {
                        listener.onDataReceived(completeFrame);
                    }
                }
            } catch (IOException e) {
                if (running) {
                    ModbusLog.e(TAG, "Read thread error: " + e.getMessage());
                    setState(ConnectionState.ERROR);
                    if (config.isAutoReconnect()) scheduleReconnect();
                }
            }
        }, "ModbusTCP-Reader");
        readThread.setDaemon(true);
        readThread.start();
    }

    private int readFully(InputStream is, byte[] buffer, int offset, int length) throws IOException {
        int totalRead = 0;
        while (totalRead < length) {
            int read = is.read(buffer, offset + totalRead, length - totalRead);
            if (read < 0) return totalRead;
            totalRead += read;
        }
        return totalRead;
    }

    private void scheduleReconnect() {
        if (reconnecting) return;
        reconnecting = true;
        setState(ConnectionState.RECONNECTING);

        reconnectThread = new Thread(() -> {
            long delay = RECONNECT_BASE_DELAY;
            while (reconnecting && running) {
                try {
                    ModbusLog.i(TAG, "Reconnecting in " + delay + "ms...");
                    Thread.sleep(delay);
                    if (!reconnecting) break;

                    // Close old socket
                    try { if (socket != null) socket.close(); } catch (IOException ignored) {}

                    // Try reconnect
                    socket = new Socket();
                    socket.setTcpNoDelay(true);
                    socket.setKeepAlive(true);
                    socket.connect(new InetSocketAddress(config.getHost(), config.getPort()), CONNECT_TIMEOUT);
                    outputStream = socket.getOutputStream();
                    running = true;
                    reconnecting = false;
                    startReadThread();
                    setState(ConnectionState.CONNECTED);
                    ModbusLog.i(TAG, "Reconnected successfully");
                    return;

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (IOException e) {
                    ModbusLog.w(TAG, "Reconnect failed: " + e.getMessage());
                    delay = Math.min(delay * 2, RECONNECT_MAX_DELAY);
                }
            }
        }, "ModbusTCP-Reconnect");
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }
}
