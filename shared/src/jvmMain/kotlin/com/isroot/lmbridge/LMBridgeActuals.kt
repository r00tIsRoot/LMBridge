package com.isroot.lmbridge

import com.google.ai.edge.litertlm.Backend as LiteRtBackend

actual object Logger {
    actual fun v(tag: String, message: String) {
        if (LMBridge.isVerboseEnabled()) println("[$tag] VERBOSE: $message")
    }
    actual fun d(tag: String, message: String) {
        if (LMBridge.isDebugEnabled()) println("[$tag] DEBUG: $message")
    }
    actual fun w(tag: String, message: String) {
        if (LMBridge.isWarnEnabled()) println("[$tag] WARN: $message")
    }
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (LMBridge.isErrorEnabled()) {
            println("[$tag] ERROR: $message")
            throwable?.printStackTrace()
        }
    }
}

fun convertToLiteRtBackend(backend: LMBridge.Backend): LiteRtBackend {
    return when (backend) {
        LMBridge.Backend.CPU -> LiteRtBackend.CPU()
        LMBridge.Backend.GPU -> LiteRtBackend.GPU()
        LMBridge.Backend.NPU -> LiteRtBackend.NPU()
    }
}
