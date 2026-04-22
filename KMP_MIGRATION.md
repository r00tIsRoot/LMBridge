# LMBridge KMP Migration Guide

LMBridge has been migrated to Kotlin Multiplatform (KMP) to provide a unified SDK across Android, JVM, Web, and iOS.

## 🚀 What's New?

The SDK now resides in the `:shared` module. You can now use LMBridge in:
- **Android**: Via the shared KMP module.
- **JVM**: For desktop or server-side applications.
- **Web**: Using Kotlin/JS and LiteRT.js.
- **iOS**: Via Kotlin/Native and the LiteRT-LM C API.

## 🛠 How to Migrate

### 1. Update Dependencies
Instead of depending on the old `:lmbridge` module, depend on the new `:shared` module.

### 2. Replace `LMBridgeClient` with `LlmEngine`
The old `LMBridgeClient` has been replaced by a more flexible `LlmEngine` interface.

**Before (Android Only):**
```kotlin
val client = LMBridgeClient.Builder(context).build()
client.initialize()
client.generate("Hello").collect { ... }
```

**After (Multiplatform):**
```kotlin
val config = EngineConfig(modelPath = "/path/to/model.litertlm")
val engine = LlmEngineFactory.create(config)
engine.initialize()
engine.sendMessage("conv_1", "Hello").collect { ... }
```

### 3. Platform-Specific Initialization
- **Android**: Ensure you initialize `AndroidSdkConfig.context` at app startup.
- **JVM/Web/iOS**: Initialize using the common `LlmEngineFactory`.

## ⚠️ Deprecation Plan

- **Version 0.1.0 (KMP)**: Introduction of the `:shared` module. Both `:lmbridge` and `:shared` are available.
- **Version 0.2.0**: `:lmbridge` module marked as `@Deprecated`.
- **Version 1.0.0**: `:lmbridge` module will be removed. All users must migrate to `:shared`.

## 📚 API Mapping

| Old API | New API | Note |
|---|---|---|
| `LMBridgeClient` | `LlmEngine` | Now an interface for multiplatform support |
| `LMBridgeClient.Builder` | `LlmEngineFactory` | Simplified factory pattern |
| `GenerationResult` | `GenerationResult` | Maintained for compatibility |
| `ModelDownloadManager` | (To be migrated) | Planned for next phase |
