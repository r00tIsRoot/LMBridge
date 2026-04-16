package com.isroot.lmbridge

import android.util.Log

/**
 * LMBridge SDK configuration.
 *
 * This class provides global configuration for the SDK including logging levels.
 */
object LMBridge {
    /**
     * Log level for SDK debugging.
     *
     * - OFF: No logs
     * - ERROR: Error messages only
     * - WARN: Warnings and errors
     * - DEBUG: Debug, warnings, and errors
     * - VERBOSE: All logs including detailed execution info
     */
    enum class LogLevel {
        OFF,
        ERROR,
        WARN,
        DEBUG,
        VERBOSE,
    }

    private var _logLevel: LogLevel = LogLevel.ERROR
    private val lock = Any()

    /**
     * Current log level.
     */
    var logLevel: LogLevel
        get() = synchronized(lock) { _logLevel }
        set(value) = synchronized(lock) { _logLevel = value }

    /**
     * Set log level by numeric value (for easier integration).
     *
     * @param level 0 = OFF, 1 = ERROR, 2 = WARN, 3 = DEBUG, 4 = VERBOSE
     */
    fun setLogLevel(level: Int) {
        logLevel = when (level) {
            0 -> LogLevel.OFF
            1 -> LogLevel.ERROR
            2 -> LogLevel.WARN
            3 -> LogLevel.DEBUG
            4 -> LogLevel.VERBOSE
            else -> LogLevel.ERROR
        }
    }

    /**
     * Check if log level allows verbose logging.
     */
    fun isVerboseEnabled(): Boolean = logLevel >= LogLevel.VERBOSE

    /**
     * Check if log level allows debug logging.
     */
    fun isDebugEnabled(): Boolean = logLevel >= LogLevel.DEBUG

    /**
     * Check if log level allows warnings.
     */
    fun isWarnEnabled(): Boolean = logLevel >= LogLevel.WARN

    /**
     * Check if log level allows errors.
     */
    fun isErrorEnabled(): Boolean = logLevel >= LogLevel.ERROR

    /**
     * Internal log tag.
     */
    const val TAG = "LMBridge"
}

/**
 * Logger utility for SDK internal logging.
 */
object Logger {
    fun v(tag: String, message: String) {
        if (LMBridge.isVerboseEnabled()) {
            Log.v(tag, message)
        }
    }

    fun d(tag: String, message: String) {
        if (LMBridge.isDebugEnabled()) {
            Log.d(tag, message)
        }
    }

    fun w(tag: String, message: String) {
        if (LMBridge.isWarnEnabled()) {
            Log.w(tag, message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (LMBridge.isErrorEnabled()) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }
}
