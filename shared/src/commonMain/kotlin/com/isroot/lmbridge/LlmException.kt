package com.isroot.lmbridge

sealed class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InitializationException(message: String, cause: Throwable? = null) : LlmException(message, cause)
    class InferenceException(message: String, cause: Throwable? = null) : LlmException(message, cause)
    class ConfigurationException(message: String, cause: Throwable? = null) : LlmException(message, cause)
    class UnknownLlmException(message: String, cause: Throwable? = null) : LlmException(message, cause)
}
