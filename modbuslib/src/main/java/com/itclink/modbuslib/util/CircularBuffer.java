package com.itclink.modbuslib.util;

import java.util.Arrays;

/**
 * High-performance circular buffer for data storage.
 * Thread-safe implementation with overwrite-on-full behavior.
 */
public class CircularBuffer<T> {
    private final Object[] buffer;
    private final int capacity;
    private volatile int writeIndex = 0;
    private volatile int size = 0;
    private final Object lock = new Object();

    public CircularBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new Object[capacity];
    }

    public void add(T item) {
        synchronized (lock) {
            buffer[writeIndex] = item;
            writeIndex = (writeIndex + 1) % capacity;
            if (size < capacity) size++;
        }
    }

    @SuppressWarnings("unchecked")
    public T getLatest() {
        synchronized (lock) {
            int currentSize = this.size;
            int currentWriteIndex = this.writeIndex;
            if (currentSize == 0) return null;
            int latestIndex = (currentWriteIndex - 1 + capacity) % capacity;
            return (T) buffer[latestIndex];
        }
    }

    @SuppressWarnings("unchecked")
    public T get(int index) {
        synchronized (lock) {
            int currentSize = this.size;
            int currentWriteIndex = this.writeIndex;
            if (index < 0 || index >= currentSize) return null;
            int actualIndex = (currentWriteIndex - currentSize + index + capacity) % capacity;
            return (T) buffer[actualIndex];
        }
    }

    @SuppressWarnings("unchecked")
    public T[] toArray(T[] array) {
        synchronized (lock) {
            int currentSize = this.size;
            int currentWriteIndex = this.writeIndex;
            if (currentSize == 0) return Arrays.copyOf(array, 0);
            T[] result = Arrays.copyOf(array, currentSize);
            for (int i = 0; i < currentSize; i++) {
                int actualIndex = (currentWriteIndex - currentSize + i + capacity) % capacity;
                result[i] = (T) buffer[actualIndex];
            }
            return result;
        }
    }

    public int size() { return size; }
    public int capacity() { return capacity; }
    public boolean isEmpty() { return size == 0; }
    public boolean isFull() { return size == capacity; }

    public void clear() {
        synchronized (lock) {
            Arrays.fill(buffer, null);
            writeIndex = 0;
            size = 0;
        }
    }
}
