package com.isroot.lmbridge

import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidLlmEngine(
    private val context: Context,
    modelPath: String? = null,
    backend: LMBridge.Backend = LMBridge.Backend.NPU,
    maxNumTokens: Int = 8192
) : LlmEngine(modelPath, backend, maxNumTokens) {
    private var nativeEnginePtr: Long = 0

    override suspend fun performInitialize() {
        val finalPath = resolveModelPath()
        nativeEnginePtr = nativeInitialize(finalPath, convertToNativeBackend(backend), maxNumTokens)
        
        if (nativeEnginePtr == 0L) {
            throw IllegalStateException("Failed to initialize LiteRT-LM native engine on Android")
        }
    }

    override fun performSendMessage(content: String, systemInstruction: String): Flow<GenerationResult> = callbackFlow {
        if (nativeEnginePtr == 0L) throw IllegalStateException("Engine not initialized")

        val callback = object : NativeCallback {
            override fun onMessage(message: String) {
                trySend(GenerationResult.Token(message))
            }
            override fun onDone() {
                trySend(GenerationResult.Done)
                close()
            }
            override fun onError(error: String) {
                trySend(GenerationResult.Error(error))
                close()
            }
        }

        nativeSendMessage(nativeEnginePtr, content, systemInstruction, callback)

        awaitClose {
            nativeCancelProcess(nativeEnginePtr)
        }
    }

    override suspend fun performShutdown() {
        if (nativeEnginePtr != 0L) {
            nativeShutdown(nativeEnginePtr)
            nativeEnginePtr = 0
        }
    }

    private fun resolveModelPath(): String {
        return if (modelPath.isNullOrEmpty()) {
            "gemma-4-E2B-it.litertlm" 
        } else {
            modelPath
        }
    }

    private fun convertToNativeBackend(backend: LMBridge.Backend): Int {
        return when (backend) {
            LMBridge.Backend.CPU -> 0
            LMBridge.Backend.GPU -> 1
            LMBridge.Backend.NPU -> 2
        }
    }

    private external fun nativeInitialize(path: String, backend: Int, maxTokens: Int): Long
    private external fun nativeSendMessage(enginePtr: Long, content: String, system: String, callback: NativeCallback)
    private external fun nativeCancelProcess(enginePtr: Long)
    private external fun nativeShutdown(enginePtr: Long)

    interface NativeCallback {
        fun onMessage(message: String)
        fun onDone()
        fun onError(error: String)
    }

    companion object {
        init {
            System.loadLibrary("lmbridge_core")
        }
    }
}
