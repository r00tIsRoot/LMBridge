package com.isroot.lmbridge

import kotlin.js.Promise

@JsModule("@litertjs/core")
@JsNonModule
external object LiteRtJs {
    fun loadLiteRt(): Promise<LiteRtEngine>
}

external interface LiteRtEngine {
    fun loadAndCompile(modelPath: String): Promise<LiteRtModel>
}

external interface LiteRtModel {
    fun createConversation(config: LiteRtConversationConfig): Promise<LiteRtConversation>
}

external interface LiteRtConversationConfig {
    var systemInstruction: String
}

external interface LiteRtConversation {
    fun sendMessage(content: String): Promise<LiteRtResponse>
    fun cancelProcess()
}

external interface LiteRtResponse {
    // In a real scenario, this would be a stream or a callback.
    // For this PoC, we'll assume a simplified response structure.
    val text: String
}

// Helper to create config objects in JS
fun createConversationConfig(instruction: String): LiteRtConversationConfig {
    val config = js("{}")
    config.systemInstruction = instruction
    return config.unsafeCast<LiteRtConversationConfig>()
}
