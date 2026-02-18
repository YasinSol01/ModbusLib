# Slave Mode

Slave mode allows Android to act as a Modbus slave (server), responding to read/write requests from master devices such as SCADA systems, HMIs, or other PLCs.

## Connection

### TCP Slave

Listens on a port and accepts multiple master connections simultaneously.

```java
import com.itclink.modbuslib.ModbusSlave;
import com.itclink.modbuslib.ModbusSlaveBuilder;
import com.itclink.modbuslib.protocol.ModbusProtocol;

ModbusSlave slave = new ModbusSlaveBuilder(context)
    .protocol(ModbusProtocol.TCP)
    .slaveId(1)
    .port(502)
    .build();

slave.start();
```

### RTU Slave (USB Serial)

Listens for requests on a USB-to-Serial RS-485 adapter.

```java
import com.itclink.modbuslib.transport.UsbSerialConfig;

ModbusSlave slave = new ModbusSlaveBuilder(context)
    .protocol(ModbusProtocol.RTU)
    .slaveId(1)
    .serialConfig(new UsbSerialConfig.Builder()
        .baudRate(9600)
        .parity(UsbSerialConfig.PARITY_EVEN)
        .build())
    .build();

slave.start();
```

## Setting Register Values

Set values before or after starting the slave. Masters will receive the latest values on their next read request.

```java
// Holding Registers (FC03 read / FC06, FC10 write)
slave.setHoldingRegister(0, 1234);
slave.setHoldingRegister(1, 5678);

// Input Registers (FC04 read only)
slave.setInputRegister(0, 9999);

// Coils (FC01 read / FC05, FC0F write)
slave.setCoil(0, true);

// Discrete Inputs (FC02 read only)
slave.setDiscreteInput(0, true);
```

## Updating Values at Runtime

```java
// Update values while slave is running
// Masters will get the new values on next read
slave.setHoldingRegister(0, getTemperatureValue());
slave.setInputRegister(0, getPressureValue());
slave.setCoil(0, isMotorRunning());
```

## Custom RegisterMap Size

By default, the RegisterMap has 10,000 entries for each type. You can customize this:

```java
import com.itclink.modbuslib.slave.RegisterMap;

RegisterMap registerMap = new RegisterMap(100, 100, 500, 500);
// Parameters: coils, discreteInputs, holdingRegisters, inputRegisters

ModbusSlave slave = new ModbusSlaveBuilder(context)
    .protocol(ModbusProtocol.TCP)
    .slaveId(1)
    .port(502)
    .registerMap(registerMap)
    .build();
```

## Custom Request Handler

Intercept read/write requests for dynamic value updates or access control.

```java
import com.itclink.modbuslib.slave.SlaveRequestHandler;

ModbusSlave slave = new ModbusSlaveBuilder(context)
    .protocol(ModbusProtocol.TCP)
    .slaveId(1)
    .port(502)
    .requestHandler(new SlaveRequestHandler() {
        @Override
        public boolean onBeforeRead(int functionCode, int address, int quantity) {
            // Update values dynamically before they are read
            if (functionCode == 0x04 && address == 0) {
                slave.setInputRegister(0, readSensorValue());
            }
            return true; // Return false to reject the read
        }

        @Override
        public void onAfterWrite(int functionCode, int address, int quantity) {
            // React to write commands from master
            Log.d("Slave", "Master wrote to address " + address);
        }

        @Override
        public void onSlaveEvent(SlaveEvent event, String message) {
            switch (event) {
                case CLIENT_CONNECTED:
                    Log.d("Slave", "Master connected: " + message);
                    break;
                case CLIENT_DISCONNECTED:
                    Log.d("Slave", "Master disconnected: " + message);
                    break;
                case REQUEST_RECEIVED:
                    Log.d("Slave", "Request received");
                    break;
                case RESPONSE_SENT:
                    Log.d("Slave", "Response sent");
                    break;
                case ERROR:
                    Log.e("Slave", "Error: " + message);
                    break;
            }
        }
    })
    .build();
```

## Register Change Listener

Get notified when register values change (from master write commands or local updates).

```java
slave.getRegisterMap().setChangeListener((type, address, quantity) -> {
    Log.d("Slave", type + " changed at address " + address + " (qty=" + quantity + ")");
});
```

## Slave Status

```java
boolean running = slave.isRunning();
int clients = slave.getClientCount();   // TCP only
long requests = slave.getRequestCount();
long errors = slave.getErrorCount();
int id = slave.getSlaveId();
```

## Stop

```java
slave.stop();
```

## Supported Function Codes

| Function Code | Operation | Slave Behavior |
|---|---|---|
| FC01 | Read Coils | Returns coil values from RegisterMap |
| FC02 | Read Discrete Inputs | Returns discrete input values from RegisterMap |
| FC03 | Read Holding Registers | Returns holding register values from RegisterMap |
| FC04 | Read Input Registers | Returns input register values from RegisterMap |
| FC05 | Write Single Coil | Updates coil in RegisterMap |
| FC06 | Write Single Register | Updates holding register in RegisterMap |
| FC0F | Write Multiple Coils | Updates multiple coils in RegisterMap |
| FC10 | Write Multiple Registers | Updates multiple holding registers in RegisterMap |

## Error Handling

The slave automatically responds with Modbus exception codes:

| Exception Code | Meaning | When |
|---|---|---|
| 0x01 | Illegal Function | Unsupported function code |
| 0x02 | Illegal Data Address | Address out of RegisterMap range |
| 0x03 | Illegal Data Value | Invalid quantity or value format |
