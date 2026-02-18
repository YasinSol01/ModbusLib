package com.itclink.modbuslib.transport;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.itclink.modbuslib.connection.ConnectionConfig;
import com.itclink.modbuslib.connection.ConnectionState;
import com.itclink.modbuslib.connection.UsbDeviceInfo;
import com.itclink.modbuslib.exception.ModbusTransportException;
import com.itclink.modbuslib.util.ModbusLog;

import java.util.Map;

/**
 * USB serial transport for Modbus RTU.
 * Supports FTDI, CP210x, PL2303, CH340 adapters.
 * Extracted from MODBUS_PULL_TEST ConnectionManager.
 */
public class UsbRtuTransport implements ModbusTransport {

    private static final String TAG = "UsbRtuTransport";

    private final Context context;
    private final ConnectionConfig config;
    private volatile TransportListener listener;
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;

    private UsbManager usbManager;
    private UsbDevice usbDevice;
    private UsbDeviceConnection usbConnection;
    private UsbSerialDevice serialPort;

    public UsbRtuTransport(Context context, ConnectionConfig config) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }

    @Override
    public void connect() throws ModbusTransportException {
        if (usbManager == null) {
            throw new ModbusTransportException("USB Manager not available");
        }

        setState(ConnectionState.CONNECTING);

        // Find compatible USB device
        usbDevice = findCompatibleDevice();
        if (usbDevice == null) {
            setState(ConnectionState.ERROR);
            throw new ModbusTransportException("No compatible USB-to-Serial device found");
        }

        // Open connection
        usbConnection = usbManager.openDevice(usbDevice);
        if (usbConnection == null) {
            setState(ConnectionState.ERROR);
            throw new ModbusTransportException("USB permission denied or device unavailable");
        }

        // Create serial port
        serialPort = UsbSerialDevice.createUsbSerialDevice(usbDevice, usbConnection);
        if (serialPort == null) {
            usbConnection.close();
            setState(ConnectionState.ERROR);
            throw new ModbusTransportException("Failed to create USB serial device");
        }

        if (!serialPort.open()) {
            usbConnection.close();
            setState(ConnectionState.ERROR);
            throw new ModbusTransportException("Failed to open USB serial port");
        }

        // Configure serial parameters
        UsbSerialConfig sc = config.getSerialConfig() != null ? config.getSerialConfig() : UsbSerialConfig.defaults();
        serialPort.setBaudRate(sc.getBaudRate());
        serialPort.setDataBits(sc.getDataBits());
        serialPort.setStopBits(sc.getStopBits());
        serialPort.setParity(sc.getParity());
        serialPort.setFlowControl(sc.getFlowControl());

        // Set up read callback
        serialPort.read(data -> {
            if (data != null && data.length > 0 && listener != null) {
                listener.onDataReceived(data);
            }
        });

        setState(ConnectionState.CONNECTED);
        UsbDeviceInfo info = new UsbDeviceInfo(usbDevice.getVendorId(), usbDevice.getProductId(), usbDevice.getDeviceName());
        ModbusLog.i(TAG, "Connected to " + info);
    }

    @Override
    public void disconnect() {
        if (serialPort != null) {
            try { serialPort.close(); } catch (Exception e) { /* ignore */ }
            serialPort = null;
        }
        if (usbConnection != null) {
            try { usbConnection.close(); } catch (Exception e) { /* ignore */ }
            usbConnection = null;
        }
        usbDevice = null;
        setState(ConnectionState.DISCONNECTED);
    }

    @Override
    public boolean isConnected() {
        return state == ConnectionState.CONNECTED && serialPort != null && serialPort.isOpen();
    }

    @Override
    public void send(byte[] data) throws ModbusTransportException {
        if (!isConnected()) {
            throw new ModbusTransportException("USB serial not connected");
        }
        serialPort.write(data);
    }

    @Override
    public void setTransportListener(TransportListener listener) { this.listener = listener; }

    @Override
    public ConnectionState getState() { return state; }

    @Override
    public String getTransportType() { return "USB_RTU"; }

    private void setState(ConnectionState newState) {
        ConnectionState old = this.state;
        this.state = newState;
        if (old != newState && listener != null) {
            listener.onStateChanged(newState, newState.getDisplayName());
        }
    }

    private UsbDevice findCompatibleDevice() {
        if (usbManager == null) return null;
        Map<String, UsbDevice> deviceList = usbManager.getDeviceList();
        if (deviceList == null || deviceList.isEmpty()) return null;

        for (UsbDevice device : deviceList.values()) {
            if (UsbDeviceInfo.isKnownUsbToSerial(device.getVendorId(), device.getProductId())) {
                if (usbManager.hasPermission(device)) {
                    ModbusLog.d(TAG, "Found compatible device: " + device.getDeviceName());
                    return device;
                }
            }
        }
        return null;
    }

    /** Get the underlying UsbSerialDevice for advanced use */
    public UsbSerialDevice getSerialPort() { return serialPort; }
}
