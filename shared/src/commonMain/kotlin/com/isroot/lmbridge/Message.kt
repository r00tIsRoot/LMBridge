package com.isroot.lmbridge

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long
)
