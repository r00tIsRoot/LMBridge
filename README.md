# LMBridge

Google LiteRT-LM (on-device LLM inference) Android SDK

## Overview

LMBridge is an Android library that provides on-device LLM inference using Google's LiteRT-LM engine. It supports text generation, multimodal (image + text) inference, and tool calling.

## Requirements

- Android API 26+ (Android 8.0 Oreo)
- Kotlin 2.2.0
- Gradle 8.10.2+

## Installation

### 1. Add JitPack Repository

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Add Dependency

```kotlin
// build.gradle.kts (app)
dependencies {
    implementation("com.isroot:lmbridge:1.0.0")
}
```

Or reference the AAR directly from `lmbridge/build/outputs/aar/lmbridge-release.aar`.

## Quick Start

```kotlin
// 1. Create client
val client = LMBridgeClient.Builder(context)
    .setModelPath("/path/to/model.litertlm")  // optional, uses asset if not set
    .build()

// 2. Initialize (async)
lifecycleScope.launch {
    client.initialize()
    
    // 3. Generate text (streaming)
    client.generate("안녕하세요").collect { result ->
        when (result) {
            is GenerationResult.Token -> print(result.text)
            is GenerationResult.Done -> println("\n[Done]")
            is GenerationResult.Error -> println("[Error: ${result.message}]")
        }
    }
    
    // 4. Cleanup
    client.release()
}
```

## API Reference

### LMBridgeClient

| Method | Description |
|--------|-------------|
| `initialize()` | Initialize the LLM engine |
| `generate(prompt)` | Text-only generation |
| `generateWithImages(prompt, images)` | Multimodal (text + images) |
| `generateWithTools(prompt, tools)` | Tool calling |
| `generateWithInput(input)` | Using MultimodalInput |
| `stopGeneration()` | Cancel ongoing generation |
| `release()` | Release resources |

### GenerationResult

```kotlin
sealed class GenerationResult {
    data class Token(val text: String) : GenerationResult()  // streaming token
    data object Done : GenerationResult()                    // generation complete
    data class Error(val message: String) : GenerationResult()  // error occurred
}
```

### MultimodalInput

```kotlin
// Text only
val input = MultimodalInput.text("안녕하세요")

// Text + Images
val input = MultimodalInput.textAndImages("이 이미지를 설명해줘", listOf(bitmap1, bitmap2))
```

## Examples

### Text Generation

```kotlin
lifecycleScope.launch {
    client.generate("Write a haiku about spring:").collect { result ->
        when (result) {
            is GenerationResult.Token -> appendToTextView(result.text)
            is GenerationResult.Done -> showCompletionMessage()
            is GenerationResult.Error -> showError(result.message)
        }
    }
}
```

### Multimodal (Image + Text)

```kotlin
lifecycleScope.launch {
    val images = listOf(bitmapFromCamera, bitmapFromGallery)
    client.generateWithImages("Describe this image:", images).collect { result ->
        when (result) {
            is GenerationResult.Token -> updateResponse(result.text)
            is GenerationResult.Done -> finish()
            is GenerationResult.Error -> error(result.message)
        }
    }
}
```

### Tool Calling

```kotlin
val weatherTool = ToolProvider.builder()
    .addTools(
        Tool.create(
            name = "get_weather",
            description = "Get current weather for a location",
            params = listOf(
                Tool.Param(name = "location", type = "string", required = true)
            )
        )
    )
    .build()

lifecycleScope.launch {
    client.generateWithTools("What's the weather in Seoul?", listOf(weatherTool))
        .collect { result ->
            // handle response
        }
}
```

### Stop Generation

```kotlin
// User tapped cancel button
client.stopGeneration()
```

## Backend Configuration

The SDK uses NPU backend by default. You can modify this in `ModelInferenceManager`:

```kotlin
val engineConfig = EngineConfig(
    modelPath = finalModelPath,
    backend = Backend.NPU(),  // Options: NPU(), CPU(), GPU()
)
```

For NPU, the native library directory is automatically detected. For other backends, no special configuration is required.

## Model File

Place your `.litertlm` model file in `app/src/main/assets/`. The default filename is `gemma-4-E2B-it.litertlm`.

To use a custom model path:

```kotlin
val client = LMBridgeClient.Builder(context)
    .setModelPath("/sdcard/models/my-model.litertlm")
    .build()
```

## Error Handling

```kotlin
lifecycleScope.launch {
    try {
        client.initialize()
    } catch (e: Exception) {
        Log.e("LMBridge", "Initialization failed: ${e.message}")
    }
}
```

## Dependencies

- `androidx.core:core-ktx:1.15.0`
- `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0`
- `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0`
- `com.google.ai.edge.litertlm:litertlm-android:0.10.0`

## License

Apache License 2.0