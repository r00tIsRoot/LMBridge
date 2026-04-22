package com.isroot.lmbridge

import kotlinx.serialization.Serializable

@Serializable
data class EngineConfig(
    val modelPath: String,
    val params: Map<String, String> = emptyMap()
)
