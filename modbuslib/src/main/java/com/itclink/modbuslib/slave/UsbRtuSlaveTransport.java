package com.itclink.modbuslib.slave;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

import com.felhr.usbserial.UsbSerialDevice;
import com.itclink.modbuslib.connection.UsbDeviceInfo;
import com.itclink.modbuslib.transport.UsbSerialConfig;
import com.itclink.modbuslib.util.CircularBuffer;
import com.itclink.modbuslib.util.ModbusLog;

import java.util.Map;

/**
 * USB serial transport for Modbus RTU Slave mode.
 * Listens for incoming requests on USB-to-Serial adapter.
 * Uses a circular buffer to accumulate bytes and detect complete RTU frames.
 */
public class UsbRtuSlaveTransport {

    private static final String TAG = "UsbRtuSlave";
    private static final int RESPONSE_BUFFER_SIZE = 512;
    private static final long INTER_FRAME_TIMEOUT_MS = 20; // 3.5 char time at 9600

    private final Context context;
    private final UsbSerialConfig serialConfig;
    private final SlaveEngine engine;
    private volatile SlaveRequestHandler handler;

    private UsbManager usbManager;
    private UsbSerialDevice serialPort;
    private UsbDeviceConnection usbConnection;

    private final CircularBuffer<Byte> rxBuffer = new CircularBuffer<>(RESPONSE_BUFFER_SIZE);
    private volatile boolean running = false;
    private Thread processThread;

    // Frame detection
    private final byte[] frameBuffer = new byte[256];
    private int framePos = 0;
    private long lastByteTime = 0;

    public UsbRtuSlaveTransport(Context context, UsbSerialConfig serialConfig, SlaveEngine engine) {
        this.context = context.getApplicationContext();
        this.serialConfig = serialConfig != null ? serialConfig : UsbSerialConfig.defaults();
        this.engine = engine;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }

    public void setHandler(SlaveRequestHandler handler) {
        this.handler = handler;
    }

    public void start() throws Exception {
        if (running) return;

        if (usbManager == null) throw new Exception("USB Manager not available");

        UsbDevice device = findCompatibleDevice();
        if (device == null) throw new Exception("No compatible USB-to-Serial device found");

        usbConnection = usbManager.openDevice(device);
        if (usbConnection == null) throw new Exception("USB permission denied");

        serialPort = UsbSerialDevice.createUsbSerialDevice(device, usbConnection);
        if (serialPort == null || !serialPort.open()) {
            if (usbConnection != null) usbConnection.close();
            throw new Exception("Failed to open USB serial port");
        }

        // Configure serial
        serialPort.setBaudRate(serialConfig.getBaudRate());
        serialPort.setDataBits(serialConfig.getDataBits());
        serialPort.setStopBits(serialConfig.getStopBits());
        serialPort.setParity(serialConfig.getParity());
        serialPort.setFlowControl(serialConfig.getFlowControl());

        running = true;

        // Set up read callback - accumulate bytes
        serialPort.read(data -> {
            if (data != null && data.length > 0 && running) {
                synchronized (frameBuffer) {
                    long now = System.currentTimeMillis();

                    // If gap between bytes exceeds inter-frame timeout, start new frame
                    if (framePos > 0 && (now - lastByteTime) > INTER_FRAME_TIMEOUT_MS) {
                        processFrame(framePos);
                        framePos = 0;
                    }

                    // Append bytes to frame buffer
                    for (byte b : data) {
                        if (framePos < frameBuffer.length) {
                            frameBuffer[framePos++] = b;
                        }
                    }
                    lastByteTime = now;
                }
            }
        });

        // Process thread - detect end of frame by silence gap
        processThread = new Thread(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(INTER_FRAME_TIMEOUT_MS);

                    synchronized (frameBuffer) {
                        if (framePos > 0) {
                            long elapsed = System.currentTimeMillis() - lastByteTime;
                            if (elapsed >= INTER_FRAME_TIMEOUT_MS) {
                                processFrame(framePos);
                                framePos = 0;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "ModbusRtuSlave-Process");
        processThread.setDaemon(true);
        processThread.start();

        UsbDeviceInfo info = new UsbDeviceInfo(device.getVendorId(), device.getProductId(), device.getDeviceName());
        ModbusLog.i(TAG, "RTU Slave started on " + info);
    }

    private void processFrame(int length) {
        if (length < 4) return; // Minimum RTU frame: SlaveID(1) + FC(1) + CRC(2)

        byte[] frame = new byte[length];
        System.arraycopy(frameBuffer, 0, frame, 0, length);

        SlaveRequestHandler h = handler;
        if (h != null) {
            h.onSlaveEvent(SlaveRequestHandler.SlaveEvent.REQUEST_RECEIVED, null);
        }

        byte[] response = engine.processRequest(frame);
        if (response != null && serialPort != null && running) {
            serialPort.write(response);

            if (h != null) {
                h.onSlaveEvent(SlaveRequestHandler.SlaveEvent.RESPONSE_SENT, null);
            }
        }
    }

    public void stop() {
        running = false;

        if (processThread != null) {
            processThread.interrupt();
            processThread = null;
        }

        if (serialPort != null) {
            try { serialPort.close(); } catch (Exception ignored) {}
            serialPort = null;
        }

        if (usbConnection != null) {
            try { usbConnection.close(); } catch (Exception ignored) {}
            usbConnection = null;
        }

        ModbusLog.i(TAG, "RTU Slave stopped");
    }

    public boolean isRunning() { return running; }

    private UsbDevice findCompatibleDevice() {
        if (usbManager == null) return null;
        Map<String, UsbDevice> deviceList = usbManager.getDeviceList();
        if (deviceList == null || deviceList.isEmpty()) return null;

        for (UsbDevice device : deviceList.values()) {
            if (UsbDeviceInfo.isKnownUsbToSerial(device.getVendorId(), device.getProductId())) {
                if (usbManager.hasPermission(device)) {
                    return device;
                }
            }
        }
        return null;
    }
}
