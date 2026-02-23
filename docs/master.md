# Master Mode

Master mode allows Android to act as a Modbus master (client), sending requests to slave devices such as PLCs, sensors, and I/O modules.

## Connection

### TCP

```java
ModbusClient client = new ModbusClientBuilder(context)
    .protocol(ModbusProtocol.TCP)
    .host("192.168.1.100")
    .port(502)
    .autoReconnect(true)
    .build();

client.connect();
```

### RTU (USB Serial)

```java
ModbusClient client = new ModbusClientBuilder(context)
    .protocol(ModbusProtocol.RTU)
    .serialConfig(new UsbSerialConfig.Builder()
        .baudRate(9600)
        .parity(UsbSerialConfig.PARITY_EVEN)
        .build())
    .build();

client.connect();
```

### RTU (Native UART)

For devices with built-in RS-485 serial ports (`/dev/ttyS*`) that do not expose a USB interface. Requires root for `chmod` and `stty` configuration.

```java
import com.itclink.modbuslib.transport.NativeSerialTransport;

// Optional: discover available ports first
List<String> ports = NativeSerialTransport.findAvailablePorts();
// e.g. ["/dev/ttyS0", "/dev/ttyS4", "/dev/ttyS5"]

NativeSerialTransport transport = new NativeSerialTransport.Builder("/dev/ttyS4")
    .baudRate(9600)
    .dataBits(8)                             // Default: 8
    .stopBits(1)                             // Default: 1
    .parity(NativeSerialTransport.PARITY_EVEN)
    .build();

ModbusClient client = new ModbusClientBuilder(context)
    .transport(transport)
    .build();

client.connect();
```

**Parity constants:** `PARITY_NONE`, `PARITY_ODD`, `PARITY_EVEN`

### Custom Timing

```java
import com.itclink.modbuslib.engine.ModbusTimingConfig;

ModbusClient client = new ModbusClientBuilder(context)
    .protocol(ModbusProtocol.TCP)
    .host("192.168.1.100")
    .port(502)
    .timingConfig(ModbusTimingConfig.custom(2000, 50, 3))
    // Parameters: timeoutMs, interFrameDelayMs, maxRetries
    .build();
```

## Read Operations

All read operations are asynchronous. Pass `true` as the last parameter for high-priority commands.

### FC01 - Read Coils

```java
client.readCoils(1, 0, 16, new ModbusCallback() {
    @Override
    public void onSuccess(int[] data) {
        // data[i] is 0 or 1 for each coil
        boolean coil0 = data[0] == 1;
    }

    @Override
    public void onError(String error) {
        Log.e("Modbus", error);
    }
}, false);
```

### FC02 - Read Discrete Inputs

```java
client.readDiscreteInputs(1, 0, 16, callback, false);
```

### FC03 - Read Holding Registers

```java
client.readHoldingRegisters(1, 0, 10, new ModbusCallback() {
    @Override
    public void onSuccess(int[] data) {
        // data contains 10 register values (16-bit unsigned each)
    }

    @Override
    public void onError(String error) {
        Log.e("Modbus", error);
    }
}, false);
```

### FC04 - Read Input Registers

```java
client.readInputRegisters(1, 0, 5, callback, false);
```

## Write Operations

### FC05 - Write Single Coil

```java
client.writeSingleCoil(1, 0, true, callback, false);
```

### FC06 - Write Single Register

```java
client.writeSingleRegister(1, 100, 1234, callback, false);
```

### FC0F - Write Multiple Coils

```java
boolean[] coils = {true, false, true, true, false};
client.writeMultipleCoils(1, 0, coils, callback, false);
```

### FC10 - Write Multiple Registers

```java
int[] values = {100, 200, 300, 400};
client.writeMultipleRegisters(1, 0, values, callback, false);
```

## Batch Read

Execute multiple reads as a single batch. Each item can target different slave IDs and addresses.

```java
import com.itclink.modbuslib.command.BatchReadRequest;
import com.itclink.modbuslib.callback.ModbusBatchCallback;
import com.itclink.modbuslib.engine.BatchResult;

BatchReadRequest request = new BatchReadRequest(
    new int[]{1, 1, 2},       // slaveIds
    new int[]{0, 100, 0},     // startAddresses
    new int[]{10, 5, 20}      // quantities
);

client.batchRead(request, new ModbusBatchCallback() {
    @Override
    public void onBatchSuccess(BatchResult[] results) {
        for (BatchResult result : results) {
            if (result.success) {
                Log.d("Batch", "Item " + result.index + ": " + Arrays.toString(result.data));
            } else {
                Log.e("Batch", "Item " + result.index + " failed: " + result.error);
            }
        }
    }

    @Override
    public void onError(String error) {
        Log.e("Batch", "Batch failed: " + error);
    }
});
```

## Synchronous API

Blocking operations for use on background threads. Do not call from the main thread.

### Sync Read

```java
new Thread(() -> {
    int[] data = client.readRegistersSync(1, 0x03, 0, 10);
    if (data != null) {
        // Process register values
    }
}).start();
```

### Sync Write

```java
new Thread(() -> {
    boolean success = client.writeRegisterSync(1, 100, 5000);
}).start();
```

## Priority Commands

High-priority commands are processed before normal-priority commands in the queue.

```java
// High priority - processed first
client.readHoldingRegisters(1, 0, 10, callback, true);

// Normal priority
client.readHoldingRegisters(1, 100, 10, callback, false);
```

## Pause and Resume

```java
// Pause command processing (queued commands are preserved)
client.pause();

// Resume command processing
client.resume();
```

## Connection State

```java
import com.itclink.modbuslib.connection.ConnectionState;

boolean connected = client.isConnected();
ConnectionState state = client.getConnectionState();
// States: DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR
```

## Statistics

```java
String stats = client.getStats();
int errors = client.getConsecutiveErrors();
```

## Disconnect

```java
client.disconnect();
```
