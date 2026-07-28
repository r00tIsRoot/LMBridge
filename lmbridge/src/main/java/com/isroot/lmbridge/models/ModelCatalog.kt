package com.isroot.lmbridge.models

import com.isroot.lmbridge.download.ModelDownloadManager.ModelInfo

/**
 * 라이브러리가 기본 제공하는 온디바이스 LLM 카탈로그.
 *
 * 항목은 모두 LiteRT-LM 엔진이 로드하는 `.litertlm` 번들이며(MediaPipe `.task` 는 제외),
 * 대부분 [litert-community](https://huggingface.co/litert-community) 조직이 배포한
 * 텍스트 생성 모델이다. 각 항목의 `commitHash`/`sizeInBytes` 는 재현 가능한(검증 가능한)
 * 다운로드를 위해 특정 리비전에 고정돼 있다.
 *
 * 소비자는 [ALL] 로 전체 목록을 순회하거나, 자주 쓰는 모델은 아래 명명 상수로 바로 참조할 수 있다.
 */
object ModelCatalog {

    // ── 자주 쓰는(명명) 모델 ──────────────────────────────────────────────────

    val GEMMA_4_E2B_IT = ModelInfo(
        displayName = "Gemma-4-E2B-it",
        modelId = "litert-community/gemma-4-E2B-it-litert-lm",
        modelFile = "gemma-4-E2B-it.litertlm",
        commitHash = "7fa1d78473894f7e736a21d920c3aa80f950c0db",
        sizeInBytes = 2583085056,
        gated = true,
    )

    val GEMMA_4_E4B_IT = ModelInfo(
        displayName = "Gemma-4-E4B-it",
        modelId = "litert-community/gemma-4-E4B-it-litert-lm",
        modelFile = "gemma-4-E4B-it.litertlm",
        commitHash = "9695417f248178c63a9f318c6e0c56cb917cb837",
        sizeInBytes = 3654467584,
        gated = true,
    )

    val GEMMA_3N_E2B_IT = ModelInfo(
        displayName = "Gemma-3n-E2B-it",
        modelId = "google/gemma-3n-E2B-it-litert-lm",
        modelFile = "gemma-3n-E2B-it-int4.litertlm",
        commitHash = "ba9ca88da013b537b6ed38108be609b8db1c3a16",
        sizeInBytes = 3655827456,
        gated = true,
    )

    val GEMMA_3N_E4B_IT = ModelInfo(
        displayName = "Gemma-3n-E4B-it",
        modelId = "google/gemma-3n-E4B-it-litert-lm",
        modelFile = "gemma-3n-E4B-it-int4.litertlm",
        commitHash = "297ed75955702dec3503e00c2c2ecbbf475300bc",
        sizeInBytes = 4919541760,
        gated = true,
    )

    val GEMMA3_1B_IT = ModelInfo(
        displayName = "Gemma3-1B-IT",
        modelId = "litert-community/Gemma3-1B-IT",
        modelFile = "gemma3-1b-it-int4.litertlm",
        commitHash = "42d538a932e8d5b12e6b3b455f5572560bd60b2c",
        sizeInBytes = 584417280,
        gated = true,
    )

    val QWEN2_5_1_5B_INSTRUCT = ModelInfo(
        displayName = "Qwen2.5-1.5B-Instruct",
        modelId = "litert-community/Qwen2.5-1.5B-Instruct",
        modelFile = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        commitHash = "19edb84c69a0212f29a6ef17ba0d6f278b6a1614",
        sizeInBytes = 1597931520,
        gated = false,
    )

    val DEEPSEEK_R1_DISTILL_QWEN_1_5B = ModelInfo(
        displayName = "DeepSeek-R1-Distill-Qwen-1.5B",
        modelId = "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
        modelFile = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
        commitHash = "e34bb88632342d1f9640bad579a45134eb1cf988",
        sizeInBytes = 1833451520,
        gated = false,
    )

    // ── 전체 카탈로그 ─────────────────────────────────────────────────────────
    // 아래 인라인 항목은 litert-community 텍스트 생성 모델의 HF 메타데이터에서 생성됐다.
    // (각 repo에서 vendor(NPU)·GPU 전용 빌드를 제외한 가장 작은 .litertlm 을 선택)

    /** 이름(abc) 오름차순으로 정렬된 전체 모델 목록. 샘플/소비자는 이 리스트를 순회한다. */
    val ALL: List<ModelInfo> = listOf(
        GEMMA_4_E2B_IT,
        GEMMA_4_E4B_IT,
        GEMMA_3N_E2B_IT,
        GEMMA_3N_E4B_IT,
        GEMMA3_1B_IT,
        QWEN2_5_1_5B_INSTRUCT,
        DEEPSEEK_R1_DISTILL_QWEN_1_5B,
        ModelInfo(
            displayName = "SmolLM2-135M-Instruct",
            modelId = "litert-community/SmolLM2-135M-Instruct",
            modelFile = "SmolLM2_135M_Instruct.litertlm",
            commitHash = "8111e0a65fda719f0a6855e8e1a8ec8c3f9ccb22",
            sizeInBytes = 142819328L,
            gated = false,
        ),
        ModelInfo(
            displayName = "FunctionGemma-Mobile-Actions",
            modelId = "litert-community/functiongemma-mobile-actions_q8_ekv1024.litertlm",
            modelFile = "mobile-actions_q8_ekv1024.litertlm",
            commitHash = "5683618019f1025f4dd567d26f58dc0ebb6244f2",
            sizeInBytes = 284426240L,
            gated = false,
        ),
        ModelInfo(
            displayName = "functiongemma-270m-ft-mobile-actions",
            modelId = "litert-community/functiongemma-270m-ft-mobile-actions",
            modelFile = "mobile_actions_q8_ekv1024.litertlm",
            commitHash = "f1c7b940a5a2598fb940648fb3cfcc745b18184b",
            sizeInBytes = 288964608L,
            gated = true,
        ),
        ModelInfo(
            displayName = "functiongemma-270m-ft-tiny-garden",
            modelId = "litert-community/functiongemma-270m-ft-tiny-garden",
            modelFile = "tiny_garden_q8_ekv1024.litertlm",
            commitHash = "aca35636dccadc77499c9843d9ff044b7e06566e",
            sizeInBytes = 288964608L,
            gated = true,
        ),
        ModelInfo(
            displayName = "gemma-3-270m-it",
            modelId = "litert-community/gemma-3-270m-it",
            modelFile = "gemma3-270m-it-q8.litertlm",
            commitHash = "9d2093270fb5aa49a986b49b5779d763dde7b630",
            sizeInBytes = 304005120L,
            gated = true,
        ),
        ModelInfo(
            displayName = "Qwen3-0.6B-int4",
            modelId = "litert-community/Qwen3-0.6B-int4",
            modelFile = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            commitHash = "6aa2daf8aba4aa456797fb8040b36a3948bcfda7",
            sizeInBytes = 347251840L,
            gated = false,
        ),
        ModelInfo(
            displayName = "SmolLM2-360M-Instruct",
            modelId = "litert-community/SmolLM2-360M-Instruct",
            modelFile = "SmolLM2_360M_instruct.litertlm",
            commitHash = "507c99cfe6541ba2bcd84818786f7b025935e5e1",
            sizeInBytes = 373719040L,
            gated = false,
        ),
        ModelInfo(
            displayName = "granite-4.0-350m-litert-lm",
            modelId = "litert-community/granite-4.0-350m-litert-lm",
            modelFile = "granite-4.0-350m_q8_ekv1280.litertlm",
            commitHash = "6f1e9bce89b174930a79de82d0dcdede708f8c34",
            sizeInBytes = 468209584L,
            gated = false,
        ),
        ModelInfo(
            displayName = "Qwen3-0.6B",
            modelId = "litert-community/Qwen3-0.6B",
            modelFile = "qwen3_0_6b_mixed_int4.litertlm",
            commitHash = "dd97997951bb15a2a71f539ba17f604707c0b11a",
            sizeInBytes = 497664000L,
            gated = false,
        ),
        ModelInfo(
            displayName = "Qwen2-0.5B-Instruct",
            modelId = "litert-community/Qwen2-0.5B-Instruct",
            modelFile = "Qwen2_0.5B_Instruct.litertlm",
            commitHash = "f2949f79a8154234747a794348d77554ae0e1fb0",
            sizeInBytes = 647377840L,
            gated = false,
        ),
        ModelInfo(
            displayName = "LFM2.5-1.2B-Instruct",
            modelId = "litert-community/LFM2.5-1.2B-Instruct",
            modelFile = "LFM2.5-1.2B-Instruct_int4.litertlm",
            commitHash = "f234443f3ade34583bfb818b7599d99d21670eb5",
            sizeInBytes = 735999360L,
            gated = false,
        ),
        ModelInfo(
            displayName = "LFM2.5-1.2B-JP",
            modelId = "litert-community/LFM2.5-1.2B-JP",
            modelFile = "LFM2.5-1.2B-JP_int4.litertlm",
            commitHash = "e57474e02084ae018ed9f60312d02983bf7897b5",
            sizeInBytes = 735999360L,
            gated = false,
        ),
        ModelInfo(
            displayName = "LFM2.5-1.2B-Thinking",
            modelId = "litert-community/LFM2.5-1.2B-Thinking",
            modelFile = "LFM2.5-1.2B-Thinking_int4.litertlm",
            commitHash = "09e65243315999782b6a1beea09dbf48c910e755",
            sizeInBytes = 735999360L,
            gated = false,
        ),
        ModelInfo(
            displayName = "MiniCPM5-1B",
            modelId = "litert-community/MiniCPM5-1B",
            modelFile = "minicpm_wi4b32_wi8_afp32.litertlm",
            commitHash = "d5c36807a1b643b7cd27919f6dc16f09320db050",
            sizeInBytes = 792625152L,
            gated = false,
        ),
        ModelInfo(
            displayName = "OLMo-2-1B-Instruct",
            modelId = "litert-community/OLMo-2-1B-Instruct",
            modelFile = "OLMo-2-1B-Instruct_q4_block32_ekv4096.litertlm",
            commitHash = "56fd1f4daa48149bef3f2da02567ff3d9398f7b6",
            sizeInBytes = 931241056L,
            gated = false,
        ),
        ModelInfo(
            displayName = "Llama-3.2-1B",
            modelId = "litert-community/Llama-3.2-1B",
            modelFile = "llama3_2_1b_mixed_int4_gpu.litertlm",
            commitHash = "fd0e16d32f23ffc87473750e294fef8d8bf081e1",
            sizeInBytes = 963903488L,
            gated = true,
        ),
        ModelInfo(
            displayName = "FastVLM-0.5B",
            modelId = "litert-community/FastVLM-0.5B",
            modelFile = "FastVLM-0.5B.litertlm",
            commitHash = "460013246392191f8532e45e518576bb6513eace",
            sizeInBytes = 1156342768L,
            gated = false,
        ),
        ModelInfo(
            displayName = "TinySwallow-1.5B-Instruct",
            modelId = "litert-community/TinySwallow-1.5B-Instruct",
            modelFile = "TinySwallow-1.5B-Instruct.litertlm",
            commitHash = "cf67d4d779c4602a7fdf43331c21897bc63ee67f",
            sizeInBytes = 1567604736L,
            gated = false,
        ),
        ModelInfo(
            displayName = "VibeThinker-1.5B",
            modelId = "litert-community/VibeThinker-1.5B",
            modelFile = "VibeThinker-1.5B.litertlm",
            commitHash = "10a5f89bb85a97b58585b0d962205dfda0fdac82",
            sizeInBytes = 1567604736L,
            gated = false,
        ),
        ModelInfo(
            displayName = "Qwen2-1.5B-Instruct",
            modelId = "litert-community/Qwen2-1.5B-Instruct",
            modelFile = "Qwen2_1.5B_Instruct.litertlm",
            commitHash = "0292a162494daa04ab6d67da70a39ef353a281f2",
            sizeInBytes = 1802843056L,
            gated = false,
        ),
        ModelInfo(
            displayName = "SmolLM3-3B",
            modelId = "litert-community/SmolLM3-3B",
            modelFile = "SmolLM3-3B_q4_block32_ekv4096.litertlm",
            commitHash = "a28ab40816fbdde13dc47ba43662b9ae6314ca92",
            sizeInBytes = 2002257840L,
            gated = false,
        ),
        ModelInfo(
            displayName = "Qwen3-1.7B",
            modelId = "litert-community/Qwen3-1.7B",
            modelFile = "Qwen3_1.7B.litertlm",
            commitHash = "5d8fd1f27c771dbbbb185c9f05c3547760dd3cbd",
            sizeInBytes = 2056729520L,
            gated = false,
        ),
        ModelInfo(
            displayName = "VibeThinker-3B",
            modelId = "litert-community/VibeThinker-3B",
            modelFile = "model.litertlm",
            commitHash = "5579e603588baf626bfebe0a613c59fd6e1fd7c3",
            sizeInBytes = 2057106352L,
            gated = false,
        ),
        ModelInfo(
            displayName = "Llama-3.2-3B",
            modelId = "litert-community/Llama-3.2-3B",
            modelFile = "llama3_2_3b_mixed_int4_gpu.litertlm",
            commitHash = "401735124eace78bbaee7bbf8f95581ab2ee63af",
            sizeInBytes = 2207940608L,
            gated = true,
        ),
        ModelInfo(
            displayName = "Ministral-3-3B-Reasoning-2512",
            modelId = "litert-community/Ministral-3-3B-Reasoning-2512",
            modelFile = "model.litertlm",
            commitHash = "a8ea8686dc9fdb1684c37f36316db72dbf30c4f8",
            sizeInBytes = 2340524016L,
            gated = false,
        ),
        ModelInfo(
            displayName = "Ministral-3-3B-Instruct-2512",
            modelId = "litert-community/Ministral-3-3B-Instruct-2512",
            modelFile = "Ministral-3-3B-Instruct-2512_q4_block32_ekv4096.litertlm",
            commitHash = "be9d92f94862c986c40cf31620711426c4d46fcf",
            sizeInBytes = 2340982768L,
            gated = false,
        ),
        ModelInfo(
            displayName = "Nanbeige4.1-3B",
            modelId = "litert-community/Nanbeige4.1-3B",
            modelFile = "model.litertlm",
            commitHash = "79112086153aba18f59481900a624a3ca3480d34",
            sizeInBytes = 2409326576L,
            gated = false,
        ),
        ModelInfo(
            displayName = "Qwen3-4B-Thinking-2507",
            modelId = "litert-community/Qwen3-4B-Thinking-2507",
            modelFile = "model.litertlm",
            commitHash = "24fbe2b27e77e259da53e4bfcc37ca23d8368874",
            sizeInBytes = 2474357680L,
            gated = false,
        ),
        ModelInfo(
            displayName = "FastContext-1.0-4B-SFT",
            modelId = "litert-community/FastContext-1.0-4B-SFT",
            modelFile = "model_block128.litertlm",
            commitHash = "ed50394b3da835735a0357b186f5b72729866cf6",
            sizeInBytes = 2474357680L,
            gated = false,
        ),
        ModelInfo(
            displayName = "Jan-nano",
            modelId = "litert-community/Jan-nano",
            modelFile = "model.litertlm",
            commitHash = "fd14ac75676c953d92504e751cd62d38315e40c9",
            sizeInBytes = 2474357680L,
            gated = false,
        ),
        ModelInfo(
            displayName = "Polaris-4B-Preview",
            modelId = "litert-community/Polaris-4B-Preview",
            modelFile = "model.litertlm",
            commitHash = "4ffb727fc4d9b7e2469bc7548d165b7cca065572",
            sizeInBytes = 2474865584L,
            gated = false,
        ),
        ModelInfo(
            displayName = "MedGemma-1.5-4B-IT",
            modelId = "litert-community/MedGemma-1.5-4B-IT",
            modelFile = "medgemma-1.5-4b-it_q4_block32_ekv2048.litertlm",
            commitHash = "757aee35d31443c3f9cdd9bb20d2556c5412c548",
            sizeInBytes = 2583871056L,
            gated = true,
        ),
        ModelInfo(
            displayName = "Qwen3-4B",
            modelId = "litert-community/Qwen3-4B",
            modelFile = "qwen3_4b_mixed_int4.litertlm",
            commitHash = "84cc5a35c9c65cd18fcd65bb1f3a7d77a4acfe6e",
            sizeInBytes = 2659057664L,
            gated = false,
        ),
        ModelInfo(
            displayName = "Qwen3-4B-Instruct-2507",
            modelId = "litert-community/Qwen3-4B-Instruct-2507",
            modelFile = "qwen3_4b_instruct_2507_mixed_int4.litertlm",
            commitHash = "a7385088ed97778d7cf91a0b541fa1f95735f768",
            sizeInBytes = 2659057664L,
            gated = false,
        ),
        ModelInfo(
            displayName = "Phi-4-mini-reasoning",
            modelId = "litert-community/Phi-4-mini-reasoning",
            modelFile = "model.litertlm",
            commitHash = "88a428819294ecc7e4d31303c9cead1d5350920e",
            sizeInBytes = 2783974384L,
            gated = false,
        ),
        ModelInfo(
            displayName = "Qwen2.5-Coder-3B-Instruct",
            modelId = "litert-community/Qwen2.5-Coder-3B-Instruct",
            modelFile = "Qwen2.5_Coder_3B_It.litertlm",
            commitHash = "a32e9f082c3fee8adcbe71990eae1eaca3eb0eb9",
            sizeInBytes = 3433083824L,
            gated = false,
        ),
        ModelInfo(
            displayName = "Phi-4-mini-instruct",
            modelId = "litert-community/Phi-4-mini-instruct",
            modelFile = "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            commitHash = "8cd368be75fdb94d5a6f6f5b40f1ab22a6c2543e",
            sizeInBytes = 3910090752L,
            gated = false,
        ),
        ModelInfo(
            displayName = "DeepSeek-R1-Distill-Qwen-7B",
            modelId = "litert-community/DeepSeek-R1-Distill-Qwen-7B",
            modelFile = "DeepSeek-R1-Distill-Qwen-7B_q4_block32_ekv4096.litertlm",
            commitHash = "ab1a8fad8088987df0f74fe0b66547d276e90ebd",
            sizeInBytes = 4531978224L,
            gated = false,
        ),
        ModelInfo(
            displayName = "Qwen3-8B",
            modelId = "litert-community/Qwen3-8B",
            modelFile = "qwen3_8b_mixed_int4.litertlm",
            commitHash = "71ff705588319d52d374977eff3da4eee0c0d26e",
            sizeInBytes = 4887412736L,
            gated = false,
        ),
        ModelInfo(
            displayName = "Qwen3-14B",
            modelId = "litert-community/Qwen3-14B",
            modelFile = "qwen3_14b_mixed_int4.litertlm",
            commitHash = "e4122fd370cec85c61467274b180e0954e4f422d",
            sizeInBytes = 8655863808L,
            gated = false,
        ),
    ).sortedBy { it.displayName.lowercase() }

    /** modelId 로 카탈로그 항목을 찾는다. */
    fun byId(modelId: String): ModelInfo? = ALL.firstOrNull { it.modelId == modelId }
}
