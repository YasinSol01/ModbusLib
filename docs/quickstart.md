# Quick Start

## TCP Master - Read Registers

```java
import com.itclink.modbuslib.ModbusClient;
import com.itclink.modbuslib.ModbusClientBuilder;
import com.itclink.modbuslib.protocol.ModbusProtocol;
import com.itclink.modbuslib.callback.ModbusCallback;

// Create client
ModbusClient client = new ModbusClientBuilder(context)
    .protocol(ModbusProtocol.TCP)
    .host("192.168.1.100")
    .port(502)
    .build();

// Connect
client.connect();

// Read 10 holding registers from slave 1, address 0
client.readHoldingRegisters(1, 0, 10, new ModbusCallback() {
    @Override
    public void onSuccess(int[] data) {
        for (int i = 0; i < data.length; i++) {
            Log.d("Modbus", "Register " + i + " = " + data[i]);
        }
    }

    @Override
    public void onError(String error) {
        Log.e("Modbus", "Read failed: " + error);
    }
}, false);

// Disconnect when done
client.disconnect();
```

## RTU Master - USB Serial

```java
import com.itclink.modbuslib.transport.UsbSerialConfig;

ModbusClient client = new ModbusClientBuilder(context)
    .protocol(ModbusProtocol.RTU)
    .serialConfig(new UsbSerialConfig.Builder()
        .baudRate(9600)
        .dataBits(UsbSerialConfig.DATA_BITS_8)
        .stopBits(UsbSerialConfig.STOP_BITS_1)
        .parity(UsbSerialConfig.PARITY_EVEN)
        .build())
    .build();

client.connect();
client.readHoldingRegisters(1, 0, 10, callback, false);
```

## TCP Slave - Serve Registers

```java
import com.itclink.modbuslib.ModbusSlave;
import com.itclink.modbuslib.ModbusSlaveBuilder;

ModbusSlave slave = new ModbusSlaveBuilder(context)
    .protocol(ModbusProtocol.TCP)
    .slaveId(1)
    .port(502)
    .build();

// Set values
slave.setHoldingRegister(0, 1234);
slave.setHoldingRegister(1, 5678);

// Start listening
slave.start();

// Update values at any time
slave.setHoldingRegister(0, 9999);

// Stop
slave.stop();
```
