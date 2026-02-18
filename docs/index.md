# ModbusLib

An Android library for Modbus communication over TCP and RTU (USB Serial RS-485).

Provides a unified API for both protocols with built-in support for **Master** and **Slave** modes, priority queuing, batch reads, synchronous operations, and automatic reconnection.

## Features

### Master Mode (Client)

- Modbus TCP (socket-based) and Modbus RTU (USB-to-Serial RS-485)
- Unified API across both protocols
- Function codes: FC01, FC02, FC03, FC04, FC05, FC06, FC0F, FC10
- Priority command queue (high and normal)
- Batch read operations
- Synchronous (blocking) API for background threads
- Auto-reconnect with exponential backoff (TCP)

### Slave Mode (Server)

- Act as a Modbus slave device on TCP or RTU
- Thread-safe RegisterMap for Coils, Discrete Inputs, Holding Registers, Input Registers
- Multi-client support (TCP: multiple masters can connect simultaneously)
- Custom request handler for dynamic value updates
- Register change notifications

### Common

- USB adapter auto-detection (FTDI, CP210x, PL2303, CH340)
- CRC-16 validation (RTU) and MBAP frame assembly (TCP)
- Thread-safe design

## Requirements

- Android SDK 24+ (Android 7.0)
- Java 11
- USB Host support (for RTU mode)

## Architecture

```
com.itclink.modbuslib
|-- ModbusClient              Master mode public API
|-- ModbusClientBuilder       Builder for master client
|-- ModbusSlave               Slave mode public API
|-- ModbusSlaveBuilder        Builder for slave server
|-- callback/                 Async callback interfaces
|-- command/                  Command and batch request models
|-- connection/               Connection state and configuration
|-- engine/                   Core master engine with queue and retry logic
|-- exception/                Typed exception hierarchy
|-- protocol/                 Frame builders and response parsers (RTU/TCP)
|-- slave/                    Slave engine, RegisterMap, transports
|-- transport/                Master transport layer (TCP socket / USB serial)
|-- util/                     Logging, byte utils, cache, validation
```

## License

This project is licensed under the MIT License.
