package com.isroot.lmbridge

import android.util.Log
import com.google.ai.edge.litertlm.Backend as LiteRtBackend

actual object Logger {
    actual fun v(tag: String, message: String) {
        if (LMBridge.isVerboseEnabled()) Log.v(tag, message)
    }
    actual fun d(tag: String, message: String) {
        if (LMBridge.isDebugEnabled()) Log.d(tag, message)
    }
    actual fun w(tag: String, message: String) {
        if (LMBridge.isWarnEnabled()) Log.w(tag, message)
    }
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (LMBridge.isErrorEnabled()) {
            if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
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
