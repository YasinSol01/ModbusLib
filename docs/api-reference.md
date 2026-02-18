# API Reference

## ModbusClient

Public facade for Master mode communication.

### Construction

```java
ModbusClient client = new ModbusClientBuilder(context)
    .protocol(ModbusProtocol.TCP)       // Required: TCP or RTU
    .host("192.168.1.100")              // TCP only
    .port(502)                          // TCP only, default: 502
    .serialConfig(UsbSerialConfig)      // RTU only
    .timingConfig(ModbusTimingConfig)   // Optional
    .autoReconnect(true)                // Optional, default: true
    .build();
```

### Methods

| Method | Description |
|---|---|
| `connect()` | Connect to the slave device |
| `disconnect()` | Disconnect and release resources |
| `isConnected()` | Returns `true` if transport is connected |
| `getConnectionState()` | Returns `ConnectionState` enum |
| `readCoils(slaveId, address, quantity, callback, highPriority)` | FC01 |
| `readDiscreteInputs(slaveId, address, quantity, callback, highPriority)` | FC02 |
| `readHoldingRegisters(slaveId, address, quantity, callback, highPriority)` | FC03 |
| `readInputRegisters(slaveId, address, quantity, callback, highPriority)` | FC04 |
| `writeSingleCoil(slaveId, address, value, callback, highPriority)` | FC05 |
| `writeSingleRegister(slaveId, address, value, callback, highPriority)` | FC06 |
| `writeMultipleCoils(slaveId, address, values, callback, highPriority)` | FC0F |
| `writeMultipleRegisters(slaveId, address, values, callback, highPriority)` | FC10 |
| `batchRead(request, callback)` | Execute multiple reads as a batch |
| `readRegistersSync(slaveId, functionCode, address, quantity)` | Blocking read (background thread only) |
| `writeRegisterSync(slaveId, address, value)` | Blocking write (background thread only) |
| `pause()` | Pause command processing |
| `resume()` | Resume command processing |
| `getStats()` | Returns statistics string |
| `getConsecutiveErrors()` | Returns consecutive error count |
| `getTransport()` | Returns underlying `ModbusTransport` |

---

## ModbusSlave

Public facade for Slave mode.

### Construction

```java
ModbusSlave slave = new ModbusSlaveBuilder(context)
    .protocol(ModbusProtocol.TCP)           // Required: TCP or RTU
    .slaveId(1)                             // Optional, default: 1
    .port(502)                              // TCP only, default: 502
    .serialConfig(UsbSerialConfig)          // RTU only
    .registerMap(RegisterMap)               // Optional, default: 10000 each
    .requestHandler(SlaveRequestHandler)    // Optional
    .build();
```

### Methods

| Method | Description |
|---|---|
| `start()` | Start listening for master connections |
| `stop()` | Stop and close all connections |
| `isRunning()` | Returns `true` if slave is active |
| `getRegisterMap()` | Returns the `RegisterMap` instance |
| `setHoldingRegister(address, value)` | Set holding register value |
| `setInputRegister(address, value)` | Set input register value |
| `setCoil(address, value)` | Set coil value |
| `setDiscreteInput(address, value)` | Set discrete input value |
| `getClientCount()` | TCP only: number of connected masters |
| `getSlaveId()` | Returns configured slave ID |
| `getRequestCount()` | Total requests processed |
| `getErrorCount()` | Total errors |

---

## RegisterMap

Thread-safe in-memory storage for Modbus registers.

### Constructor

```java
RegisterMap()                                          // Default: 10000 each
RegisterMap(coilCount, discreteCount, holdingCount, inputCount)  // Custom sizes
```

### Methods

| Method | Description |
|---|---|
| `getCoil(address)` | Get coil value |
| `getCoils(address, quantity)` | Get multiple coils |
| `setCoil(address, value)` | Set coil value |
| `setCoils(address, values)` | Set multiple coils |
| `getDiscreteInput(address)` | Get discrete input |
| `getDiscreteInputs(address, quantity)` | Get multiple discrete inputs |
| `setDiscreteInput(address, value)` | Set discrete input |
| `getHoldingRegister(address)` | Get holding register |
| `getHoldingRegisters(address, quantity)` | Get multiple holding registers |
| `setHoldingRegister(address, value)` | Set holding register |
| `setHoldingRegisters(address, values)` | Set multiple holding registers |
| `getInputRegister(address)` | Get input register |
| `getInputRegisters(address, quantity)` | Get multiple input registers |
| `setInputRegister(address, value)` | Set input register |
| `setInputRegisters(address, values)` | Set multiple input registers |
| `setChangeListener(listener)` | Set register change callback |

---

## ModbusCallback

```java
public interface ModbusCallback {
    void onSuccess(int[] data);
    void onError(String error);
}
```

## ModbusBatchCallback

```java
public interface ModbusBatchCallback {
    void onBatchSuccess(BatchResult[] results);
    void onError(String error);
}
```

## BatchResult

| Field | Type | Description |
|---|---|---|
| `index` | `int` | Index in the batch request |
| `data` | `int[]` | Register values (null on error) |
| `error` | `String` | Error message (null on success) |
| `success` | `boolean` | Whether the read succeeded |

## BatchReadRequest

```java
new BatchReadRequest(
    int[] slaveIds,     // Slave ID for each read
    int[] addresses,    // Start address for each read
    int[] quantities    // Quantity for each read
);
```

---

## ConnectionState

```java
enum ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}
```

## ModbusProtocol

```java
enum ModbusProtocol {
    RTU,    // slaveId: 1-247, timeout: 500ms, retries: 3
    TCP     // slaveId: 0-255, timeout: 1500ms, retries: 2
}
```

## UsbSerialConfig

```java
new UsbSerialConfig.Builder()
    .baudRate(9600)                         // Default: 9600
    .dataBits(UsbSerialConfig.DATA_BITS_8)  // Default: 8
    .stopBits(UsbSerialConfig.STOP_BITS_1)  // Default: 1
    .parity(UsbSerialConfig.PARITY_EVEN)    // Default: Even
    .flowControl(UsbSerialConfig.FLOW_CONTROL_OFF)
    .build();
```

## ModbusTimingConfig

```java
ModbusTimingConfig.forRTU()     // timeout: 500ms, delay: 10ms, retries: 3
ModbusTimingConfig.forTCP()     // timeout: 1500ms, delay: 30ms, retries: 2
ModbusTimingConfig.custom(timeoutMs, interFrameDelayMs, maxRetries)
```

## SlaveRequestHandler

```java
public interface SlaveRequestHandler {
    default boolean onBeforeRead(int functionCode, int address, int quantity);
    default void onAfterWrite(int functionCode, int address, int quantity);
    default void onSlaveEvent(SlaveEvent event, String message);

    enum SlaveEvent {
        CLIENT_CONNECTED,
        CLIENT_DISCONNECTED,
        REQUEST_RECEIVED,
        RESPONSE_SENT,
        ERROR
    }
}
```

## Exception Hierarchy

```
ModbusException (base)
|-- ModbusTimeoutException      Response timeout
|-- ModbusProtocolException     Invalid frame / error response
|-- ModbusTransportException    Connection / I/O error
```
