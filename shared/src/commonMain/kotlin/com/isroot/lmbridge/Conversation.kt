package com.isroot.lmbridge

import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    val id: String,
    val messages: List<Message>
)
