package com.isroot.lmbridge

import android.content.Context

object AndroidSdkConfig {
    var context: Context? = null
        set(value) {
            field = value
        }

    fun requireContext(): Context {
        return context ?: throw IllegalStateException("AndroidSdkConfig.context must be initialized before using the SDK")
    }
}
