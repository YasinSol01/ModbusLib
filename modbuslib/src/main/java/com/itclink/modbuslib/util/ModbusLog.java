package com.itclink.modbuslib.util;

import android.util.Log;

/**
 * Library-level logger with pluggable handler and level-based filtering.
 * No SharedPreferences or BuildConfig dependency.
 */
public class ModbusLog {

    public enum Level {
        VERBOSE(0), DEBUG(1), INFO(2), WARN(3), ERROR(4), NONE(5);

        private final int priority;

        Level(int priority) { this.priority = priority; }

        public int getPriority() { return priority; }
    }

    public interface LogHandler {
        void log(int level, String tag, String message, Throwable throwable);
    }

    private static final String PREFIX = "Modbus_";
    private static volatile Level currentLevel = Level.DEBUG;
    private static volatile LogHandler customHandler = null;

    public static void setLevel(Level level) { currentLevel = level; }
    public static Level getLevel() { return currentLevel; }

    public static void setHandler(LogHandler handler) { customHandler = handler; }

    private static boolean shouldLog(Level level) {
        return level.getPriority() >= currentLevel.getPriority();
    }

    private static String tag(String tag) { return PREFIX + tag; }

    // Verbose
    public static void v(String tag, String message) {
        if (!shouldLog(Level.VERBOSE)) return;
        if (customHandler != null) { customHandler.log(Log.VERBOSE, tag(tag), message, null); return; }
        Log.v(tag(tag), message);
    }

    // Debug
    public static void d(String tag, String message) {
        if (!shouldLog(Level.DEBUG)) return;
        if (customHandler != null) { customHandler.log(Log.DEBUG, tag(tag), message, null); return; }
        Log.d(tag(tag), message);
    }

    // Info
    public static void i(String tag, String message) {
        if (!shouldLog(Level.INFO)) return;
        if (customHandler != null) { customHandler.log(Log.INFO, tag(tag), message, null); return; }
        Log.i(tag(tag), message);
    }

    // Warning
    public static void w(String tag, String message) {
        if (!shouldLog(Level.WARN)) return;
        if (customHandler != null) { customHandler.log(Log.WARN, tag(tag), message, null); return; }
        Log.w(tag(tag), message);
    }

    public static void w(String tag, String message, Throwable t) {
        if (!shouldLog(Level.WARN)) return;
        if (customHandler != null) { customHandler.log(Log.WARN, tag(tag), message, t); return; }
        Log.w(tag(tag), message, t);
    }

    // Error
    public static void e(String tag, String message) {
        if (!shouldLog(Level.ERROR)) return;
        if (customHandler != null) { customHandler.log(Log.ERROR, tag(tag), message, null); return; }
        Log.e(tag(tag), message);
    }

    public static void e(String tag, String message, Throwable t) {
        if (!shouldLog(Level.ERROR)) return;
        if (customHandler != null) { customHandler.log(Log.ERROR, tag(tag), message, t); return; }
        Log.e(tag(tag), message, t);
    }
}
