package com.isroot.lmbridge

object LMBridge {
    enum class Backend {
        CPU,
        GPU,
        NPU,
    }

    enum class LogLevel {
        OFF,
        ERROR,
        WARN,
        DEBUG,
        VERBOSE,
    }

    var logLevel: LogLevel = LogLevel.ERROR

    fun isVerboseEnabled(): Boolean = logLevel >= LogLevel.VERBOSE
    fun isDebugEnabled(): Boolean = logLevel >= LogLevel.DEBUG
    fun isWarnEnabled(): Boolean = logLevel >= LogLevel.WARN
    fun isErrorEnabled(): Boolean = logLevel >= LogLevel.ERROR
}

expect object Logger {
    fun v(tag: String, message: String)
    fun d(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
