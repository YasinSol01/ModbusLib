package com.itclink.modbuslib.slave;

import com.itclink.modbuslib.util.ModbusLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * TCP server transport for Modbus Slave mode.
 * Listens on a port and accepts multiple master connections.
 */
public class TcpSlaveTransport {

    private static final String TAG = "TcpSlaveTcp";
    private static final int MBAP_HEADER_SIZE = 6;

    private final int port;
    private final SlaveEngine engine;
    private volatile SlaveRequestHandler handler;

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private volatile boolean running = false;

    public TcpSlaveTransport(int port, SlaveEngine engine) {
        this.port = port;
        this.engine = engine;
    }

    public void setHandler(SlaveRequestHandler handler) {
        this.handler = handler;
    }

    public void start() throws IOException {
        if (running) return;

        serverSocket = new ServerSocket(port);
        serverSocket.setReuseAddress(true);
        running = true;

        acceptThread = new Thread(() -> {
            ModbusLog.i(TAG, "Slave listening on port " + port);
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setTcpNoDelay(true);
                    clientSocket.setKeepAlive(true);

                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    clients.add(clientHandler);
                    clientHandler.start();

                    String addr = clientSocket.getInetAddress().getHostAddress();
                    ModbusLog.i(TAG, "Master connected from " + addr);

                    SlaveRequestHandler h = handler;
                    if (h != null) {
                        h.onSlaveEvent(SlaveRequestHandler.SlaveEvent.CLIENT_CONNECTED, addr);
                    }
                } catch (IOException e) {
                    if (running) {
                        ModbusLog.e(TAG, "Accept error: " + e.getMessage());
                    }
                }
            }
        }, "ModbusSlave-Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public void stop() {
        running = false;

        for (ClientHandler client : clients) {
            client.close();
        }
        clients.clear();

        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
            serverSocket = null;
        }

        if (acceptThread != null) {
            acceptThread.interrupt();
            acceptThread = null;
        }

        ModbusLog.i(TAG, "Slave stopped");
    }

    public boolean isRunning() { return running; }
    public int getClientCount() { return clients.size(); }

    /**
     * Handles a single master connection.
     */
    private class ClientHandler {
        private final Socket socket;
        private Thread readThread;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        void start() {
            readThread = new Thread(() -> {
                byte[] headerBuffer = new byte[MBAP_HEADER_SIZE];
                try {
                    InputStream in = socket.getInputStream();
                    OutputStream out = socket.getOutputStream();

                    while (running && !Thread.currentThread().isInterrupted()) {
                        // Read MBAP header
                        int read = readFully(in, headerBuffer, 0, MBAP_HEADER_SIZE);
                        if (read < MBAP_HEADER_SIZE) break;

                        // Parse length
                        int dataLength = ((headerBuffer[4] & 0xFF) << 8) | (headerBuffer[5] & 0xFF);
                        if (dataLength <= 0 || dataLength > 260) {
                            ModbusLog.w(TAG, "Invalid MBAP length: " + dataLength);
                            continue;
                        }

                        // Read full frame
                        byte[] frame = new byte[MBAP_HEADER_SIZE + dataLength];
                        System.arraycopy(headerBuffer, 0, frame, 0, MBAP_HEADER_SIZE);
                        read = readFully(in, frame, MBAP_HEADER_SIZE, dataLength);
                        if (read < dataLength) break;

                        SlaveRequestHandler h = handler;
                        if (h != null) {
                            h.onSlaveEvent(SlaveRequestHandler.SlaveEvent.REQUEST_RECEIVED, null);
                        }

                        // Process request
                        byte[] response = engine.processRequest(frame);
                        if (response != null) {
                            synchronized (out) {
                                out.write(response);
                                out.flush();
                            }
                            if (h != null) {
                                h.onSlaveEvent(SlaveRequestHandler.SlaveEvent.RESPONSE_SENT, null);
                            }
                        }
                    }
                } catch (IOException e) {
                    if (running) {
                        ModbusLog.d(TAG, "Client disconnected: " + e.getMessage());
                    }
                } finally {
                    close();
                    clients.remove(ClientHandler.this);
                    String addr = "";
                    try { addr = socket.getInetAddress().getHostAddress(); } catch (Exception ignored) {}
                    ModbusLog.i(TAG, "Master disconnected: " + addr);

                    SlaveRequestHandler h = handler;
                    if (h != null) {
                        h.onSlaveEvent(SlaveRequestHandler.SlaveEvent.CLIENT_DISCONNECTED, addr);
                    }
                }
            }, "ModbusSlave-Client-" + socket.getPort());
            readThread.setDaemon(true);
            readThread.start();
        }

        void close() {
            try { if (!socket.isClosed()) socket.close(); } catch (IOException ignored) {}
            if (readThread != null) readThread.interrupt();
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
    }
}
