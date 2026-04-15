package com.isroot.lmbridge.models

import com.isroot.lmbridge.download.ModelDownloadManager

object ModelCatalog {
    val GEMMA_4_E2B_IT = ModelDownloadManager.ModelInfo(
        modelId = "litert-community/gemma-4-E2B-it-litert-lm",
        modelFile = "gemma-4-E2B-it.litertlm",
        commitHash = "7fa1d78473894f7e736a21d920c3aa80f950c0db",
        sizeInBytes = 2583085056,
    )

    val GEMMA_4_E4B_IT = ModelDownloadManager.ModelInfo(
        modelId = "litert-community/gemma-4-E4B-it-litert-lm",
        modelFile = "gemma-4-E4B-it.litertlm",
        commitHash = "9695417f248178c63a9f318c6e0c56cb917cb837",
        sizeInBytes = 3654467584,
    )

    val GEMMA_3N_E2B_IT = ModelDownloadManager.ModelInfo(
        modelId = "google/gemma-3n-E2B-it-litert-lm",
        modelFile = "gemma-3n-E2B-it-int4.litertlm",
        commitHash = "ba9ca88da013b537b6ed38108be609b8db1c3a16",
        sizeInBytes = 3655827456,
    )

    val GEMMA_3N_E4B_IT = ModelDownloadManager.ModelInfo(
        modelId = "google/gemma-3n-E4B-it-litert-lm",
        modelFile = "gemma-3n-E4B-it-int4.litertlm",
        commitHash = "297ed75955702dec3503e00c2c2ecbbf475300bc",
        sizeInBytes = 4919541760,
    )

    val GEMMA3_1B_IT = ModelDownloadManager.ModelInfo(
        modelId = "litert-community/Gemma3-1B-IT",
        modelFile = "gemma3-1b-it-int4.litertlm",
        commitHash = "42d538a932e8d5b12e6b3b455f5572560bd60b2c",
        sizeInBytes = 584417280,
    )

    val QWEN2_5_1_5B_INSTRUCT = ModelDownloadManager.ModelInfo(
        modelId = "litert-community/Qwen2.5-1.5B-Instruct",
        modelFile = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        commitHash = "19edb84c69a0212f29a6ef17ba0d6f278b6a1614",
        sizeInBytes = 1597931520,
    )

    val DEEPSEEK_R1_DISTILL_QWEN_1_5B = ModelDownloadManager.ModelInfo(
        modelId = "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
        modelFile = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
        commitHash = "e34bb88632342d1f9640bad579a45134eb1cf988",
        sizeInBytes = 1833451520,
    )
}
