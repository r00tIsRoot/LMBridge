package com.isroot.lmbridge

actual object Logger {
    actual fun v(tag: String, message: String) {
        if (LMBridge.isVerboseEnabled()) console.log("[$tag] VERBOSE: $message")
    }
    actual fun d(tag: String, message: String) {
        if (LMBridge.isDebugEnabled()) console.log("[$tag] DEBUG: $message")
    }
    actual fun w(tag: String, message: String) {
        if (LMBridge.isWarnEnabled()) console.warn("[$tag] WARN: $message")
    }
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (LMBridge.isErrorEnabled()) {
            console.error("[$tag] ERROR: $message")
            throwable?.let { console.error(it) }
        }
    }
}

fun convertToLiteRtBackend(backend: LMBridge.Backend): String {
    return backend.name
}
