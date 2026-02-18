# ModbusLib [![](https://jitpack.io/v/YasinSol01/ModbusLib.svg)](https://jitpack.io/#YasinSol01/ModbusLib)

An Android library for Modbus communication over TCP and RTU (USB Serial RS-485). Provides a unified API for both protocols with built-in support for priority queuing, batch reads, synchronous operations, and automatic reconnection.

## Features

- Modbus TCP (socket-based) and Modbus RTU (USB-to-Serial RS-485)
- Unified API across both protocols
- Function codes: FC01, FC02, FC03, FC04, FC05, FC06, FC0F, FC10
- Priority command queue (high and normal)
- Batch read operations
- Synchronous (blocking) API for background threads
- Auto-reconnect with exponential backoff (TCP)
- USB adapter auto-detection (FTDI, CP210x, PL2303, CH340)
- CRC-16 validation (RTU) and MBAP frame assembly (TCP)
- Thread-safe design

## Requirements

- Android SDK 24+ (Android 7.0)
- Java 11
- USB Host support (for RTU mode)

## Installation

### Step 1. Add the JitPack repository

In your project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Step 2. Add the dependency

In your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.YasinSol01:ModbusLib:v1.0.2")
}
```

## Usage

### TCP Connection

```java
import com.itclink.modbuslib.ModbusClient;
import com.itclink.modbuslib.ModbusClientBuilder;
import com.itclink.modbuslib.protocol.ModbusProtocol;
import com.itclink.modbuslib.callback.ModbusCallback;

ModbusClient client = new ModbusClientBuilder(context)
    .protocol(ModbusProtocol.TCP)
    .host("192.168.1.100")
    .port(502)
    .autoReconnect(true)
    .build();

client.connect();
```

### RTU Connection (USB Serial)

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
```

### Reading Holding Registers (Async)

```java
// FC03 - Read Holding Registers
// Parameters: slaveId, startAddress, quantity, callback, highPriority
client.readHoldingRegisters(1, 0, 10, new ModbusCallback() {
    @Override
    public void onSuccess(int[] data) {
        // data contains 10 register values
        for (int i = 0; i < data.length; i++) {
            Log.d("Modbus", "Register " + i + " = " + data[i]);
        }
    }

    @Override
    public void onError(String error) {
        Log.e("Modbus", "Read failed: " + error);
    }
}, false);
```

### Reading Input Registers

```java
// FC04 - Read Input Registers
client.readInputRegisters(1, 0, 5, new ModbusCallback() {
    @Override
    public void onSuccess(int[] data) {
        // Process input register values
    }

    @Override
    public void onError(String error) {
        Log.e("Modbus", error);
    }
}, false);
```

### Reading Coils

```java
// FC01 - Read Coils
client.readCoils(1, 0, 16, new ModbusCallback() {
    @Override
    public void onSuccess(int[] data) {
        // data[i] is 0 or 1 for each coil
        boolean coil0 = data[0] == 1;
        boolean coil1 = data[1] == 1;
    }

    @Override
    public void onError(String error) {
        Log.e("Modbus", error);
    }
}, false);
```

### Writing a Single Register

```java
// FC06 - Write Single Register
client.writeSingleRegister(1, 100, 1234, new ModbusCallback() {
    @Override
    public void onSuccess(int[] data) {
        Log.d("Modbus", "Write successful");
    }

    @Override
    public void onError(String error) {
        Log.e("Modbus", "Write failed: " + error);
    }
}, false);
```

### Writing a Single Coil

```java
// FC05 - Write Single Coil
client.writeSingleCoil(1, 0, true, new ModbusCallback() {
    @Override
    public void onSuccess(int[] data) {
        Log.d("Modbus", "Coil set to ON");
    }

    @Override
    public void onError(String error) {
        Log.e("Modbus", error);
    }
}, false);
```

### Writing Multiple Registers

```java
// FC10 - Write Multiple Registers
int[] values = {100, 200, 300, 400};
client.writeMultipleRegisters(1, 0, values, new ModbusCallback() {
    @Override
    public void onSuccess(int[] data) {
        Log.d("Modbus", "Multiple registers written");
    }

    @Override
    public void onError(String error) {
        Log.e("Modbus", error);
    }
}, false);
```

### Writing Multiple Coils

```java
// FC0F - Write Multiple Coils
boolean[] coils = {true, false, true, true, false};
client.writeMultipleCoils(1, 0, coils, new ModbusCallback() {
    @Override
    public void onSuccess(int[] data) {
        Log.d("Modbus", "Multiple coils written");
    }

    @Override
    public void onError(String error) {
        Log.e("Modbus", error);
    }
}, false);
```

### Batch Read

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
            if (result.isSuccess()) {
                int[] data = result.getData();
                // Process each batch result
            } else {
                Log.e("Modbus", "Batch item failed: " + result.getError());
            }
        }
    }

    @Override
    public void onError(String error) {
        Log.e("Modbus", "Batch read failed: " + error);
    }
});
```

### Synchronous Read (Blocking)

Must be called from a background thread.

```java
new Thread(() -> {
    // FC03 - Read 10 holding registers from slave 1, address 0
    int[] data = client.readRegistersSync(1, 0x03, 0, 10);
    if (data != null) {
        // Process register values
    } else {
        // Read failed
    }
}).start();
```

### Synchronous Write (Blocking)

```java
new Thread(() -> {
    boolean success = client.writeRegisterSync(1, 100, 5000);
    if (success) {
        // Write confirmed
    }
}).start();
```

### Priority Commands

Pass `true` as the last parameter to send a command with high priority. High-priority commands are processed before normal-priority commands in the queue.

```java
// High-priority read
client.readHoldingRegisters(1, 0, 10, callback, true);

// Normal-priority read
client.readHoldingRegisters(1, 100, 10, callback, false);
```

### Pause and Resume

```java
// Pause command processing (queued commands are preserved)
client.pause();

// Resume command processing
client.resume();
```

### Connection State

```java
import com.itclink.modbuslib.connection.ConnectionState;

boolean connected = client.isConnected();
ConnectionState state = client.getConnectionState();
// States: DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR
```

### Custom Timing Configuration

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

### Disconnect

```java
client.disconnect();
```

## Permissions

The library declares the following permissions in its manifest:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-feature android:name="android.hardware.usb.host" android:required="false" />
```

For RTU mode, USB host permission must be granted by the user when a device is connected.

## Architecture

```
com.itclink.modbuslib
|-- ModbusClient              Public API facade
|-- ModbusClientBuilder       Builder for client construction
|-- callback/                 Async callback interfaces
|-- command/                  Command and batch request models
|-- connection/               Connection state and configuration
|-- engine/                   Core engine with queue and retry logic
|-- exception/                Typed exception hierarchy
|-- protocol/                 Frame builders and response parsers (RTU/TCP)
|-- transport/                Transport layer (TCP socket / USB serial)
|-- util/                     Logging, byte utils, cache, validation
```

## License

This project is licensed under the MIT License.
