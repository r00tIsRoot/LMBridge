package com.isroot.lmbridge.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatConfigTest {

    @Test
    fun defaults_leaveModelDefaultsUntouched() {
        val config = ChatConfig()
        assertNull(config.sampler)
        assertNull(config.thinking)
        assertNull(config.maxOutputTokens)
        assertTrue(config.history.isEmpty())
        assertTrue(config.automaticToolCalling)
    }

    @Test(expected = IllegalArgumentException::class)
    fun maxOutputTokens_mustBePositive() {
        ChatConfig(maxOutputTokens = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sampler_rejectsTopPOutOfRange() {
        Sampler(topP = 1.5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sampler_rejectsNonPositiveTopK() {
        Sampler(topK = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun thinking_rejectsBudgetBelowUnlimited() {
        Thinking(tokenBudget = -2)
    }

    @Test
    fun thinking_acceptsUnlimitedDisabledAndPositiveBudgets() {
        assertEquals(Thinking.UNLIMITED, Thinking().tokenBudget)
        assertEquals(0, Thinking(tokenBudget = 0).tokenBudget)
        assertEquals(1024, Thinking(tokenBudget = 1024).tokenBudget)
    }

    @Test
    fun chatMessage_factoriesSetRole() {
        assertEquals(ChatMessage.Role.USER, ChatMessage.user("hi").role)
        assertEquals(ChatMessage.Role.MODEL, ChatMessage.model("hello").role)
    }
}
