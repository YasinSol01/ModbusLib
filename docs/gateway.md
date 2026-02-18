# Gateway Mode (Master + Slave)

ModbusLib supports running Master and Slave simultaneously in the same application, enabling your Android device to act as a **Modbus Gateway** or **Protocol Bridge**.

## Use Case

```
PLC / Sensor               Android Device              SCADA / HMI
  [Slave]  <--- Master pulls data ---  [ModbusClient]
                                        [ModbusSlave]  --- serves data ---> [Master]
```

The Android device:

1. **Pulls** data from a PLC or sensor using `ModbusClient` (Master)
2. **Stores** the data in the Slave's `RegisterMap`
3. **Serves** the data to SCADA/HMI systems using `ModbusSlave` (Slave)

## Basic Example

```java
// 1. Create Slave (let other devices read from us)
ModbusSlave slave = new ModbusSlaveBuilder(context)
    .protocol(ModbusProtocol.TCP)
    .slaveId(1)
    .port(502)
    .build();
slave.start();

// 2. Create Master (pull data from PLC)
ModbusClient client = new ModbusClientBuilder(context)
    .protocol(ModbusProtocol.TCP)
    .host("192.168.1.100")
    .port(502)
    .build();
client.connect();

// 3. Pull data from PLC and store in Slave
client.readHoldingRegisters(1, 0, 10, new ModbusCallback() {
    @Override
    public void onSuccess(int[] data) {
        for (int i = 0; i < data.length; i++) {
            slave.setHoldingRegister(i, data[i]);
        }
    }

    @Override
    public void onError(String error) {
        Log.e("Gateway", error);
    }
}, false);
```

## Continuous Polling

Use a `ScheduledExecutorService` to poll the PLC at regular intervals.

```java
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

scheduler.scheduleAtFixedRate(() -> {
    int[] data = client.readRegistersSync(1, 0x03, 0, 10);
    if (data != null) {
        for (int i = 0; i < data.length; i++) {
            slave.setHoldingRegister(i, data[i]);
        }
    }
}, 0, 500, TimeUnit.MILLISECONDS);  // Poll every 500ms
```

## Protocol Bridge (RTU to TCP)

Bridge data from an RTU device to a TCP network.

```java
// RTU Master -> read from RS-485 device
ModbusClient rtuClient = new ModbusClientBuilder(context)
    .protocol(ModbusProtocol.RTU)
    .serialConfig(new UsbSerialConfig.Builder()
        .baudRate(9600)
        .parity(UsbSerialConfig.PARITY_EVEN)
        .build())
    .build();
rtuClient.connect();

// TCP Slave -> serve data over network
ModbusSlave tcpSlave = new ModbusSlaveBuilder(context)
    .protocol(ModbusProtocol.TCP)
    .slaveId(1)
    .port(502)
    .build();
tcpSlave.start();

// Bridge: RTU -> TCP
scheduler.scheduleAtFixedRate(() -> {
    int[] data = rtuClient.readRegistersSync(1, 0x03, 0, 20);
    if (data != null) {
        for (int i = 0; i < data.length; i++) {
            tcpSlave.setHoldingRegister(i, data[i]);
        }
    }
}, 0, 500, TimeUnit.MILLISECONDS);
```

## Bidirectional Bridge

Allow the SCADA to write values that get forwarded to the PLC.

```java
ModbusSlave slave = new ModbusSlaveBuilder(context)
    .protocol(ModbusProtocol.TCP)
    .slaveId(1)
    .port(502)
    .requestHandler(new SlaveRequestHandler() {
        @Override
        public void onAfterWrite(int functionCode, int address, int quantity) {
            // Forward writes from SCADA to PLC
            if (functionCode == 0x06) {
                int value = slave.getRegisterMap().getHoldingRegister(address);
                client.writeSingleRegister(1, address, value, new ModbusCallback() {
                    @Override
                    public void onSuccess(int[] data) {
                        Log.d("Gateway", "Forwarded write to PLC: addr=" + address);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e("Gateway", "Forward failed: " + error);
                    }
                }, true); // High priority
            }
        }
    })
    .build();
```

## Cleanup

```java
scheduler.shutdown();
client.disconnect();
slave.stop();
```
