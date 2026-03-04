# ModbusLib [![](https://jitpack.io/v/YasinSol01/ModbusLib.svg)](https://jitpack.io/#YasinSol01/ModbusLib)

An Android library for Modbus communication over TCP and RTU (USB Serial or Native UART) with Master and Slave support.

**[Full Documentation](https://yasinsol01.github.io/ModbusLib/)**

## Features

- **Master Mode** - Read/write registers, coils from PLCs and devices (FC01-FC10)
- **Slave Mode** - Act as a Modbus device, serve data to SCADA/HMI
- **Gateway Mode** - Master + Slave simultaneously for protocol bridging
- TCP (socket), RTU via USB-to-Serial, and RTU via Native UART (`/dev/ttyS*`)
- Priority queue, batch reads, sync API, auto-reconnect
- USB adapter auto-detection (FTDI, CP210x, PL2303, CH340)
- **Native UART** support for devices with built-in RS-485 serial ports (rooted)
- CRC-16 table-lookup (RTU) and MBAP frame assembly (TCP)

## Installation

Add JitPack to `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add dependency to `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.YasinSol01:ModbusLib:v1.0.2")
}
```

## Quick Start

### Master - TCP

```java
ModbusClient client = new ModbusClientBuilder(context)
    .protocol(ModbusProtocol.TCP)
    .host("192.168.1.100").port(502)
    .build();

client.connect();
client.readHoldingRegisters(1, 0, 10, callback, false);
```

### Master - Native UART (rooted device, built-in RS-485)

```java
NativeSerialTransport transport = new NativeSerialTransport.Builder("/dev/ttyS4")
    .baudRate(9600)
    .parity(NativeSerialTransport.PARITY_EVEN)
    .build();

ModbusClient client = new ModbusClientBuilder(context)
    .transport(transport)
    .build();

client.connect();
client.readHoldingRegisters(1, 0, 10, callback, false);
```

### Slave (serve data)

```java
ModbusSlave slave = new ModbusSlaveBuilder(context)
    .protocol(ModbusProtocol.TCP)
    .slaveId(1).port(502)
    .build();

slave.setHoldingRegister(0, 1234);
slave.start();
```

## Documentation

For full usage guide, API reference, and examples:

**[https://yasinsol01.github.io/ModbusLib/](https://yasinsol01.github.io/ModbusLib/)**

## Requirements

- Android SDK 24+ (Android 7.0)
- Java 11

## License

MIT License
