package com.itclink.modbuslib.slave;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe in-memory storage for Modbus registers.
 * Stores Coils, Discrete Inputs, Holding Registers, and Input Registers.
 */
public class RegisterMap {

    private final boolean[] coils;              // FC01 read, FC05/FC0F write
    private final boolean[] discreteInputs;     // FC02 read only
    private final int[] holdingRegisters;       // FC03 read, FC06/FC10 write
    private final int[] inputRegisters;         // FC04 read only

    private final ReadWriteLock coilLock = new ReentrantReadWriteLock();
    private final ReadWriteLock discreteLock = new ReentrantReadWriteLock();
    private final ReadWriteLock holdingLock = new ReentrantReadWriteLock();
    private final ReadWriteLock inputLock = new ReentrantReadWriteLock();

    private volatile RegisterChangeListener changeListener;

    public RegisterMap(int coilCount, int discreteCount, int holdingCount, int inputCount) {
        this.coils = new boolean[coilCount];
        this.discreteInputs = new boolean[discreteCount];
        this.holdingRegisters = new int[holdingCount];
        this.inputRegisters = new int[inputCount];
    }

    /**
     * Default: 10000 coils, 10000 discrete inputs, 10000 holding registers, 10000 input registers.
     */
    public RegisterMap() {
        this(10000, 10000, 10000, 10000);
    }

    // ===== COILS (FC01 / FC05 / FC0F) =====

    public boolean getCoil(int address) {
        coilLock.readLock().lock();
        try {
            validateAddress(address, coils.length, "Coil");
            return coils[address];
        } finally {
            coilLock.readLock().unlock();
        }
    }

    public boolean[] getCoils(int address, int quantity) {
        coilLock.readLock().lock();
        try {
            validateRange(address, quantity, coils.length, "Coil");
            boolean[] result = new boolean[quantity];
            System.arraycopy(coils, address, result, 0, quantity);
            return result;
        } finally {
            coilLock.readLock().unlock();
        }
    }

    public void setCoil(int address, boolean value) {
        coilLock.writeLock().lock();
        try {
            validateAddress(address, coils.length, "Coil");
            coils[address] = value;
        } finally {
            coilLock.writeLock().unlock();
        }
        notifyChange(RegisterType.COIL, address, 1);
    }

    public void setCoils(int address, boolean[] values) {
        coilLock.writeLock().lock();
        try {
            validateRange(address, values.length, coils.length, "Coil");
            System.arraycopy(values, 0, coils, address, values.length);
        } finally {
            coilLock.writeLock().unlock();
        }
        notifyChange(RegisterType.COIL, address, values.length);
    }

    // ===== DISCRETE INPUTS (FC02) =====

    public boolean getDiscreteInput(int address) {
        discreteLock.readLock().lock();
        try {
            validateAddress(address, discreteInputs.length, "Discrete Input");
            return discreteInputs[address];
        } finally {
            discreteLock.readLock().unlock();
        }
    }

    public boolean[] getDiscreteInputs(int address, int quantity) {
        discreteLock.readLock().lock();
        try {
            validateRange(address, quantity, discreteInputs.length, "Discrete Input");
            boolean[] result = new boolean[quantity];
            System.arraycopy(discreteInputs, address, result, 0, quantity);
            return result;
        } finally {
            discreteLock.readLock().unlock();
        }
    }

    public void setDiscreteInput(int address, boolean value) {
        discreteLock.writeLock().lock();
        try {
            validateAddress(address, discreteInputs.length, "Discrete Input");
            discreteInputs[address] = value;
        } finally {
            discreteLock.writeLock().unlock();
        }
        notifyChange(RegisterType.DISCRETE_INPUT, address, 1);
    }

    // ===== HOLDING REGISTERS (FC03 / FC06 / FC10) =====

    public int getHoldingRegister(int address) {
        holdingLock.readLock().lock();
        try {
            validateAddress(address, holdingRegisters.length, "Holding Register");
            return holdingRegisters[address];
        } finally {
            holdingLock.readLock().unlock();
        }
    }

    public int[] getHoldingRegisters(int address, int quantity) {
        holdingLock.readLock().lock();
        try {
            validateRange(address, quantity, holdingRegisters.length, "Holding Register");
            int[] result = new int[quantity];
            System.arraycopy(holdingRegisters, address, result, 0, quantity);
            return result;
        } finally {
            holdingLock.readLock().unlock();
        }
    }

    public void setHoldingRegister(int address, int value) {
        holdingLock.writeLock().lock();
        try {
            validateAddress(address, holdingRegisters.length, "Holding Register");
            holdingRegisters[address] = value & 0xFFFF;
        } finally {
            holdingLock.writeLock().unlock();
        }
        notifyChange(RegisterType.HOLDING_REGISTER, address, 1);
    }

    public void setHoldingRegisters(int address, int[] values) {
        holdingLock.writeLock().lock();
        try {
            validateRange(address, values.length, holdingRegisters.length, "Holding Register");
            for (int i = 0; i < values.length; i++) {
                holdingRegisters[address + i] = values[i] & 0xFFFF;
            }
        } finally {
            holdingLock.writeLock().unlock();
        }
        notifyChange(RegisterType.HOLDING_REGISTER, address, values.length);
    }

    // ===== INPUT REGISTERS (FC04) =====

    public int getInputRegister(int address) {
        inputLock.readLock().lock();
        try {
            validateAddress(address, inputRegisters.length, "Input Register");
            return inputRegisters[address];
        } finally {
            inputLock.readLock().unlock();
        }
    }

    public int[] getInputRegisters(int address, int quantity) {
        inputLock.readLock().lock();
        try {
            validateRange(address, quantity, inputRegisters.length, "Input Register");
            int[] result = new int[quantity];
            System.arraycopy(inputRegisters, address, result, 0, quantity);
            return result;
        } finally {
            inputLock.readLock().unlock();
        }
    }

    public void setInputRegister(int address, int value) {
        inputLock.writeLock().lock();
        try {
            validateAddress(address, inputRegisters.length, "Input Register");
            inputRegisters[address] = value & 0xFFFF;
        } finally {
            inputLock.writeLock().unlock();
        }
        notifyChange(RegisterType.INPUT_REGISTER, address, 1);
    }

    public void setInputRegisters(int address, int[] values) {
        inputLock.writeLock().lock();
        try {
            validateRange(address, values.length, inputRegisters.length, "Input Register");
            for (int i = 0; i < values.length; i++) {
                inputRegisters[address + i] = values[i] & 0xFFFF;
            }
        } finally {
            inputLock.writeLock().unlock();
        }
        notifyChange(RegisterType.INPUT_REGISTER, address, values.length);
    }

    // ===== LISTENER =====

    public void setChangeListener(RegisterChangeListener listener) {
        this.changeListener = listener;
    }

    private void notifyChange(RegisterType type, int address, int quantity) {
        RegisterChangeListener l = changeListener;
        if (l != null) {
            l.onRegisterChanged(type, address, quantity);
        }
    }

    // ===== VALIDATION =====

    private void validateAddress(int address, int max, String name) {
        if (address < 0 || address >= max) {
            throw new IllegalArgumentException(name + " address " + address + " out of range (0-" + (max - 1) + ")");
        }
    }

    private void validateRange(int address, int quantity, int max, String name) {
        if (address < 0 || quantity <= 0 || (address + quantity) > max) {
            throw new IllegalArgumentException(
                    name + " range " + address + "+" + quantity + " out of range (0-" + (max - 1) + ")");
        }
    }

    // ===== TYPES =====

    public enum RegisterType {
        COIL, DISCRETE_INPUT, HOLDING_REGISTER, INPUT_REGISTER
    }

    public interface RegisterChangeListener {
        void onRegisterChanged(RegisterType type, int address, int quantity);
    }
}
