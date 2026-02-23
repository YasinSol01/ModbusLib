package com.itclink.modbuslib.transport;

import android.util.Log;

import com.itclink.modbuslib.connection.ConnectionState;
import com.itclink.modbuslib.exception.ModbusTransportException;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * NativeSerialTransport - ใช้ /dev/ttyS* สำหรับ Modbus RTU บนอุปกรณ์ที่มี built-in UART
 * (เช่น IXHUB H1 10" ที่ COM5-6 เป็น RS485 ผ่าน native UART ไม่ใช่ USB)
 *
 * ต้องการ root เพื่อ chmod และ stty
 * ใช้งาน:
 *   NativeSerialTransport t = new NativeSerialTransport.Builder("/dev/ttyS4")
 *       .baudRate(9600).parity(NativeSerialTransport.PARITY_EVEN).build();
 *   ModbusClient client = new ModbusClientBuilder(ctx).transport(t).build();
 */
public class NativeSerialTransport implements ModbusTransport {

    private static final String TAG = "NativeSerial";

    // Parity constants (ตรงกับ UsbSerialConfig)
    public static final int PARITY_NONE = 0;
    public static final int PARITY_ODD  = 1;
    public static final int PARITY_EVEN = 2;

    private final String portPath;
    private final int    baudRate;
    private final int    dataBits;
    private final int    stopBits;
    private final int    parity;

    private volatile TransportListener listener;
    private volatile ConnectionState   state = ConnectionState.DISCONNECTED;

    private FileInputStream  inputStream;
    private FileOutputStream outputStream;
    private Thread           readThread;
    private volatile boolean running = false;

    // ===== BUILDER =====

    public static class Builder {
        private final String portPath;
        private int baudRate = 9600;
        private int dataBits = 8;
        private int stopBits = 1;
        private int parity   = PARITY_NONE;

        public Builder(String portPath) { this.portPath = portPath; }
        public Builder baudRate(int v) { this.baudRate = v; return this; }
        public Builder dataBits(int v) { this.dataBits = v; return this; }
        public Builder stopBits(int v) { this.stopBits = v; return this; }
        public Builder parity(int v)   { this.parity   = v; return this; }
        public NativeSerialTransport build() {
            return new NativeSerialTransport(portPath, baudRate, dataBits, stopBits, parity);
        }
    }

    private NativeSerialTransport(String portPath, int baudRate, int dataBits, int stopBits, int parity) {
        this.portPath = portPath;
        this.baudRate = baudRate;
        this.dataBits = dataBits;
        this.stopBits = stopBits;
        this.parity   = parity;
    }

    // ===== ModbusTransport INTERFACE =====

    @Override
    public void connect() throws ModbusTransportException {
        setState(ConnectionState.CONNECTING);

        // 1. chmod 666 ให้ app อ่าน/เขียนได้ (ต้องการ root)
        execRoot("chmod 666 " + portPath);

        // 2. stty ตั้งค่า baud rate, parity, data bits
        execRoot(buildSttyCommand());

        // 3. เปิด file stream
        File port = new File(portPath);
        if (!port.exists()) {
            setState(ConnectionState.ERROR);
            throw new ModbusTransportException("Port not found: " + portPath);
        }
        try {
            inputStream  = new FileInputStream(port);
            outputStream = new FileOutputStream(port);
        } catch (IOException e) {
            setState(ConnectionState.ERROR);
            throw new ModbusTransportException("Cannot open " + portPath + ": " + e.getMessage());
        }

        // 4. start read thread
        running    = true;
        readThread = new Thread(this::readLoop, "NativeSerial-Read");
        readThread.setDaemon(true);
        readThread.start();

        setState(ConnectionState.CONNECTED);
        Log.i(TAG, "Connected to " + portPath + " @ " + baudRate + " baud");
    }

    @Override
    public void disconnect() {
        running = false;
        // ปิด stream เพื่อ interrupt blocking read
        try { if (inputStream  != null) inputStream.close();  } catch (Exception ignored) {}
        try { if (outputStream != null) outputStream.close(); } catch (Exception ignored) {}
        inputStream  = null;
        outputStream = null;
        if (readThread != null) {
            readThread.interrupt();
            readThread = null;
        }
        setState(ConnectionState.DISCONNECTED);
        Log.i(TAG, "Disconnected from " + portPath);
    }

    @Override
    public boolean isConnected() {
        return state == ConnectionState.CONNECTED && running && inputStream != null;
    }

    @Override
    public void send(byte[] data) throws ModbusTransportException {
        if (!isConnected()) throw new ModbusTransportException("Not connected: " + portPath);
        try {
            outputStream.write(data);
            outputStream.flush();
        } catch (IOException e) {
            throw new ModbusTransportException("Write error: " + e.getMessage());
        }
    }

    @Override
    public void setTransportListener(TransportListener listener) { this.listener = listener; }

    @Override
    public ConnectionState getState() { return state; }

    @Override
    public String getTransportType() { return "NATIVE_RTU:" + portPath; }

    // ===== READ LOOP =====

    private void readLoop() {
        byte[] buffer = new byte[512];
        while (running) {
            try {
                int n = inputStream.read(buffer); // blocking
                if (n > 0 && listener != null) {
                    listener.onDataReceived(Arrays.copyOf(buffer, n));
                }
            } catch (IOException e) {
                if (running) {
                    Log.w(TAG, "Read error: " + e.getMessage());
                    if (listener != null) listener.onError("Read error", e);
                }
                break;
            }
        }
        Log.d(TAG, "Read thread exited");
    }

    // ===== PRIVATE HELPERS =====

    private void setState(ConnectionState newState) {
        ConnectionState old = this.state;
        this.state = newState;
        if (old != newState && listener != null) {
            listener.onStateChanged(newState, newState.getDisplayName());
        }
    }

    /**
     * สร้าง stty command สำหรับตั้งค่า serial port
     * ตัวอย่าง: stty -F /dev/ttyS4 9600 cs8 -cstopb -parenb raw -echo
     */
    private String buildSttyCommand() {
        StringBuilder sb = new StringBuilder();
        sb.append("stty -F ").append(portPath)
          .append(" ").append(baudRate)
          .append(" cs").append(dataBits);

        // Stop bits
        sb.append(stopBits == 2 ? " cstopb" : " -cstopb");

        // Parity
        switch (parity) {
            case PARITY_EVEN: sb.append(" parenb -parodd"); break;
            case PARITY_ODD:  sb.append(" parenb parodd");  break;
            default:          sb.append(" -parenb");         break;
        }

        // Raw mode: ไม่ process newlines, echo off
        sb.append(" raw -echo -echoe -echok -echonl -ixon -ixoff -crtscts");
        return sb.toString();
    }

    /**
     * รัน shell command ผ่าน su (root)
     */
    private void execRoot(String command) {
        try {
            Process su = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(su.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            int exit = su.waitFor();
            if (exit != 0) Log.w(TAG, "execRoot exit=" + exit + " cmd=" + command);
            else Log.d(TAG, "execRoot OK: " + command);
        } catch (Exception e) {
            Log.w(TAG, "execRoot failed: " + e.getMessage() + " cmd=" + command);
        }
    }

    // ===== STATIC UTILITY =====

    /**
     * ค้นหา native serial ports ที่มีอยู่ใน /dev/ttyS*, /dev/ttyAS*, /dev/ttyHS*
     * ใช้สำหรับ debug หรือ auto-detect port
     * @return รายการ path ที่พบ เช่น ["/dev/ttyS0", "/dev/ttyS4"]
     */
    public static java.util.List<String> findAvailablePorts() {
        java.util.List<String> found = new java.util.ArrayList<>();
        String[] patterns = {
            "/dev/ttyS%d",   // standard UART
            "/dev/ttyAS%d",  // Rockchip async serial
            "/dev/ttyHS%d",  // high-speed UART
        };
        for (String pattern : patterns) {
            for (int i = 0; i <= 7; i++) {
                String path = String.format(pattern, i);
                if (new File(path).exists()) {
                    found.add(path);
                }
            }
        }
        Log.d(TAG, "Native serial ports found: " + found);
        return found;
    }
}
