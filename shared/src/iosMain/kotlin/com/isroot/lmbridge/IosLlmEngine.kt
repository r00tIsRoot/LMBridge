package com.isroot.lmbridge

import com.isroot.lmbridge.litert.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.cinterop.*

class IosLlmEngine(
    modelPath: String? = null,
    backend: LMBridge.Backend = LMBridge.Backend.NPU,
    maxNumTokens: Int = 8192
) : LlmEngine(modelPath, backend, maxNumTokens) {
    private var nativeEngine: CPointer<litert_lm_engine_t>? = null
    private var nativeConversation: CPointer<litert_lm_conversation_t>? = null

    override suspend fun performInitialize() {
        val path = modelPath ?: "gemma-4-E2B-it.litertlm"
        
        nativeEngine = litert_lm_create_engine(
            path,
            convertToNativeBackend(backend),
            maxNumTokens
        ) ?: throw IllegalStateException("Failed to create LiteRT-LM engine on iOS")
        
        litert_lm_initialize(nativeEngine)
    }

    override fun performSendMessage(content: String, systemInstruction: String): Flow<GenerationResult> = callbackFlow {
        val engine = nativeEngine ?: throw IllegalStateException("Engine not initialized")
        
        nativeConversation = litert_lm_create_conversation(engine, systemInstruction)
            ?: throw IllegalStateException("Failed to create conversation on iOS")

        val stableRef = StableRef.create(this)
        
        litert_lm_send_message_async(
            nativeConversation,
            content,
            staticCFunction { message, status, user_data ->
                val scope = user_data?.asStableRef<ProducerScope<GenerationResult>>()?.get()
                when (status) {
                    0 -> scope?.trySend(GenerationResult.Token(message?.toKString() ?: ""))
                    1 -> {
                        scope?.trySend(GenerationResult.Done)
                        scope?.close()
                    }
                    else -> {
                        scope?.trySend(GenerationResult.Error("iOS Native Error: $status"))
                        scope?.close()
                    }
                }
            },
            stableRef.asCPointer()
        )

        awaitClose {
            stableRef.dispose()
            litert_lm_cancel_process(nativeConversation)
        }
    }

    override suspend fun performShutdown() {
        nativeConversation?.let { litert_lm_destroy_conversation(it) }
        nativeEngine?.let { litert_lm_destroy_engine(it) }
        nativeConversation = null
        nativeEngine = null
    }

    private fun convertToNativeBackend(backend: LMBridge.Backend): Int {
        return when (backend) {
            LMBridge.Backend.CPU -> 0
            LMBridge.Backend.GPU -> 1
            LMBridge.Backend.NPU -> 2
        }
    }
}
