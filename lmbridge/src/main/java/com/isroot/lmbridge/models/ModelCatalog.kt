package com.isroot.lmbridge.models

import com.isroot.lmbridge.download.ModelDownloadManager.ModelInfo

/**
 * 라이브러리가 기본 제공하는 온디바이스 LLM 카탈로그.
 *
 * 항목은 모두 LiteRT-LM 엔진이 로드하는 `.litertlm` 번들이며(MediaPipe `.task` 는 제외),
 * 대부분 [litert-community](https://huggingface.co/litert-community) 조직이 배포한
 * 텍스트 생성 모델이다. 각 항목의 `commitHash`/`sizeInBytes`/`sha256` 은 재현 가능한(검증 가능한)
 * 다운로드를 위해 특정 리비전에 고정돼 있다(2026-10-15 HF main 기준 일괄 갱신).
 *
 * litert-community는 번들을 수시로 재패키징하며, 런타임(litertlm) 요구 버전이 바뀌기도 한다.
 * litertlm을 올릴 때는 고정 리비전이 새 런타임에서 동작하는지 함께 확인해야 한다.
 *
 * 소비자는 [ALL] 로 전체 목록을 순회하거나, 자주 쓰는 모델은 아래 명명 상수로 바로 참조할 수 있다.
 */
object ModelCatalog {

    // ── 자주 쓰는(명명) 모델 ──────────────────────────────────────────────────

    val GEMMA_4_E2B_IT = ModelInfo(
        displayName = "Gemma-4-E2B-it",
        modelId = "litert-community/gemma-4-E2B-it-litert-lm",
        modelFile = "gemma-4-E2B-it.litertlm",
        commitHash = "b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1",
        sizeInBytes = 2588147712,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        gated = true,
    )

    val GEMMA_4_E4B_IT = ModelInfo(
        displayName = "Gemma-4-E4B-it",
        modelId = "litert-community/gemma-4-E4B-it-litert-lm",
        modelFile = "gemma-4-E4B-it.litertlm",
        commitHash = "2eee7ac325f20eb8c9ac1d0e972f7c84663062da",
        sizeInBytes = 3659530240,
        sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
        gated = true,
    )

    val GEMMA_3N_E2B_IT = ModelInfo(
        displayName = "Gemma-3n-E2B-it",
        modelId = "google/gemma-3n-E2B-it-litert-lm",
        modelFile = "gemma-3n-E2B-it-int4.litertlm",
        commitHash = "c03b6f60b8da6c5400b6838a2cf26420f80c0a01",
        sizeInBytes = 3655827456,
        sha256 = "2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6",
        gated = true,
    )

    val GEMMA_3N_E4B_IT = ModelInfo(
        displayName = "Gemma-3n-E4B-it",
        modelId = "google/gemma-3n-E4B-it-litert-lm",
        modelFile = "gemma-3n-E4B-it-int4.litertlm",
        commitHash = "297ed75955702dec3503e00c2c2ecbbf475300bc",
        sizeInBytes = 4919541760,
        sha256 = "2e67a6cd51dfe0f793431e6bd4ed8d029c88e10f52ca0469ad38445e3cd3c1f4",
        gated = true,
    )

    val GEMMA3_1B_IT = ModelInfo(
        displayName = "Gemma3-1B-IT",
        modelId = "litert-community/Gemma3-1B-IT",
        modelFile = "gemma3-1b-it-int4.litertlm",
        commitHash = "d448835d84411a08f577e25f3afebe528402d73b",
        sizeInBytes = 584417280,
        sha256 = "1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be",
        gated = true,
    )

    val QWEN2_5_1_5B_INSTRUCT = ModelInfo(
        // main(2026-10)은 litert-lm 0.18.0용 chat template(content parts)로 갱신돼 0.16.x에서
        // 히스토리/프롬프트가 깨진다. litertlm을 0.18+로 올리기 전까지 이전 리비전에 고정.
        displayName = "Qwen2.5-1.5B-Instruct",
        modelId = "litert-community/Qwen2.5-1.5B-Instruct",
        modelFile = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        commitHash = "19edb84c69a0212f29a6ef17ba0d6f278b6a1614",
        sizeInBytes = 1597931520,
        sha256 = "faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9",
        gated = false,
    )

    val DEEPSEEK_R1_DISTILL_QWEN_1_5B = ModelInfo(
        displayName = "DeepSeek-R1-Distill-Qwen-1.5B",
        modelId = "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
        modelFile = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
        commitHash = "2f8b8ee90d8f93b15305b699e8772b277d074a9a",
        sizeInBytes = 1833451520,
        sha256 = "69b35f01759eed765641ab4af589bbe98131fd2825662a086d9037409b8c1295",
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
            sha256 = "ccdc5c85735743f081b7d44ca309cab569f76c0f2f0e8e163449a63721969c37",
            gated = false,
        ),
        ModelInfo(
            displayName = "FunctionGemma-Mobile-Actions",
            modelId = "litert-community/functiongemma-mobile-actions_q8_ekv1024.litertlm",
            modelFile = "mobile-actions_q8_ekv1024.litertlm",
            commitHash = "5683618019f1025f4dd567d26f58dc0ebb6244f2",
            sizeInBytes = 284426240L,
            sha256 = "92109695f911d1872fa8ae07c1e3ff0ed70f2c3d1690d410ec6db8587c2ab409",
            gated = false,
        ),
        ModelInfo(
            displayName = "functiongemma-270m-ft-mobile-actions",
            modelId = "litert-community/functiongemma-270m-ft-mobile-actions",
            modelFile = "mobile_actions_q8_ekv1024.litertlm",
            commitHash = "6d7eb4d4a1d25be7e049f89b8a5080561cedc41c",
            sizeInBytes = 288964608L,
            sha256 = "33e295cbd996b419bb1de8f3f85c5b6b01ee058a2c89bdb2173cf3e6ff4ce9d0",
            gated = true,
        ),
        ModelInfo(
            displayName = "functiongemma-270m-ft-tiny-garden",
            modelId = "litert-community/functiongemma-270m-ft-tiny-garden",
            modelFile = "tiny_garden_q8_ekv1024.litertlm",
            commitHash = "aca35636dccadc77499c9843d9ff044b7e06566e",
            sizeInBytes = 288964608L,
            sha256 = "38067934eac6845417a456602da8714e1c0a5ae28970140d3b5f6abaf48e3141",
            gated = true,
        ),
        ModelInfo(
            displayName = "gemma-3-270m-it",
            modelId = "litert-community/gemma-3-270m-it",
            modelFile = "gemma3-270m-it-q8.litertlm",
            commitHash = "9d2093270fb5aa49a986b49b5779d763dde7b630",
            sizeInBytes = 304005120L,
            sha256 = "757e9119fa5bd667a2774fb470ac4afcd3190a21c677f8e69a5d6bc908abdd63",
            gated = true,
        ),
        ModelInfo(
            displayName = "Qwen3-0.6B-int4",
            modelId = "litert-community/Qwen3-0.6B-int4",
            modelFile = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            commitHash = "6aa2daf8aba4aa456797fb8040b36a3948bcfda7",
            sizeInBytes = 347251840L,
            sha256 = "2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139",
            gated = false,
        ),
        ModelInfo(
            displayName = "SmolLM2-360M-Instruct",
            modelId = "litert-community/SmolLM2-360M-Instruct",
            modelFile = "SmolLM2_360M_instruct.litertlm",
            commitHash = "507c99cfe6541ba2bcd84818786f7b025935e5e1",
            sizeInBytes = 373719040L,
            sha256 = "8e2834da211b439751af968ed650febdde5a8cb8d88bc6c1a3059f049caa5c2e",
            gated = false,
        ),
        ModelInfo(
            displayName = "granite-4.0-350m-litert-lm",
            modelId = "litert-community/granite-4.0-350m-litert-lm",
            modelFile = "granite-4.0-350m_q8_ekv1280.litertlm",
            commitHash = "6f1e9bce89b174930a79de82d0dcdede708f8c34",
            sizeInBytes = 468209584L,
            sha256 = "c8e9a29493f62b7c44461fb36980987c4c1454c75e95f57ba0539a8edc9dce76",
            gated = false,
        ),
        ModelInfo(
            displayName = "Qwen3-0.6B",
            modelId = "litert-community/Qwen3-0.6B",
            modelFile = "qwen3_0_6b_mixed_int4.litertlm",
            commitHash = "e6687177e00897731e7716267eebd4eba0dde7c2",
            sizeInBytes = 497516544L,
            sha256 = "7900eb4e7362d88c58782c6f9999bb7a129e03544aa98b8f338ea0cc5d8c22c1",
            gated = false,
        ),
        ModelInfo(
            displayName = "Qwen2-0.5B-Instruct",
            modelId = "litert-community/Qwen2-0.5B-Instruct",
            modelFile = "Qwen2_0.5B_Instruct.litertlm",
            commitHash = "13aab3e522828d85fa178d885716ab858a715149",
            sizeInBytes = 647377840L,
            sha256 = "0f01cc004b8eb62b92ba6be85ed05a248ba0d2f78af94c4949b313eccfb4c157",
            gated = false,
        ),
        ModelInfo(
            displayName = "LFM2.5-1.2B-Instruct",
            modelId = "litert-community/LFM2.5-1.2B-Instruct",
            modelFile = "LFM2.5-1.2B-Instruct_int4.litertlm",
            // 2026-10-13 re-pack(llm_model_type lfm2): litertlm >= 0.15 필요. 이전 번들은 0.15+에서
            // "missing some output TensorBuffers"로 생성 실패한다.
            commitHash = "2b81a4be8ceaec7a74614fc6df2efcae5ed55828",
            sizeInBytes = 736015744L,
            sha256 = "e904729a8a8a0160658cb7cd22992e270a0547c05cc45b08c47ece5c0e29425b",
            gated = false,
        ),
        ModelInfo(
            displayName = "LFM2.5-1.2B-JP",
            modelId = "litert-community/LFM2.5-1.2B-JP",
            modelFile = "LFM2.5-1.2B-JP_int4.litertlm",
            // 2026-10-13 re-pack(llm_model_type lfm2): litertlm >= 0.15 필요. 이전 번들은 0.15+에서
            // "missing some output TensorBuffers"로 생성 실패한다.
            commitHash = "6d6b3fd127a6fabd124bdf176421e55e8e9cc2b0",
            sizeInBytes = 736015744L,
            sha256 = "eed10977c555e7a7b94e4d39134338e3f3df83da31dd2dbe6e1f16c87def227d",
            gated = false,
        ),
        ModelInfo(
            displayName = "LFM2.5-1.2B-Thinking",
            modelId = "litert-community/LFM2.5-1.2B-Thinking",
            modelFile = "LFM2.5-1.2B-Thinking_int4.litertlm",
            // 2026-10-13 re-pack(llm_model_type lfm2): litertlm >= 0.15 필요. 이전 번들은 0.15+에서
            // "missing some output TensorBuffers"로 생성 실패한다.
            commitHash = "7bc0383538f1e891472aae58c98dbd4620343da5",
            sizeInBytes = 736015744L,
            sha256 = "da3c0ad154b78f33537b117fbe747cc1d4fd3507479002b159998f4023f32a84",
            gated = false,
        ),
        ModelInfo(
            // main(2026-10)은 litert-lm 0.18.0용 chat template(content parts)로 갱신돼 0.16.x에서
            // 히스토리/프롬프트가 깨진다. litertlm을 0.18+로 올리기 전까지 이전 리비전에 고정.
            displayName = "MiniCPM5-1B",
            modelId = "litert-community/MiniCPM5-1B",
            modelFile = "minicpm_wi4b32_wi8_afp32.litertlm",
            commitHash = "d5c36807a1b643b7cd27919f6dc16f09320db050",
            sizeInBytes = 792625152L,
            sha256 = "43f41a837c58408192d703a7cf7bb27a3f8b4f8cc1d34c7f79e54f058f966ccc",
            gated = false,
        ),
        ModelInfo(
            displayName = "OLMo-2-1B-Instruct",
            modelId = "litert-community/OLMo-2-1B-Instruct",
            modelFile = "OLMo-2-1B-Instruct_q4_block32_ekv4096.litertlm",
            commitHash = "30b451940c06f50711a5b1bbb7e693b0bc11b3d8",
            sizeInBytes = 931241056L,
            sha256 = "8d2457b54397731c5f451babb6bebfb9877545b4b070ac76914aab348fd954b0",
            gated = false,
        ),
        ModelInfo(
            displayName = "Llama-3.2-1B",
            modelId = "litert-community/Llama-3.2-1B",
            modelFile = "llama3_2_1b_mixed_int4_gpu.litertlm",
            commitHash = "fd0e16d32f23ffc87473750e294fef8d8bf081e1",
            sizeInBytes = 963903488L,
            sha256 = "c6f21c5304c40f45b69e5bfb15bb45acbaad7938e941d25a0a391aef0ec6ac27",
            gated = true,
        ),
        ModelInfo(
            displayName = "FastVLM-0.5B",
            modelId = "litert-community/FastVLM-0.5B",
            modelFile = "FastVLM-0.5B.litertlm",
            commitHash = "460013246392191f8532e45e518576bb6513eace",
            sizeInBytes = 1156342768L,
            sha256 = "ccba1e8bfa0bab78345f5d009fdffd20bd8c907cf39b4bc632e391b0a96f3b18",
            gated = false,
        ),
        ModelInfo(
            displayName = "TinySwallow-1.5B-Instruct",
            modelId = "litert-community/TinySwallow-1.5B-Instruct",
            modelFile = "TinySwallow-1.5B-Instruct.litertlm",
            commitHash = "cf67d4d779c4602a7fdf43331c21897bc63ee67f",
            sizeInBytes = 1567604736L,
            sha256 = "a79dab105dca3474188cb2cd7f3e94380567dd10d788c830ca7f7388d4ce816b",
            gated = false,
        ),
        ModelInfo(
            displayName = "VibeThinker-1.5B",
            modelId = "litert-community/VibeThinker-1.5B",
            modelFile = "VibeThinker-1.5B.litertlm",
            commitHash = "24367f44117d086d513ad41f08fa145df8807fd4",
            sizeInBytes = 1567604736L,
            sha256 = "2b954abf0df11deb7bea9c4ce779566918dd81f4e578d673e7f618b10799e412",
            gated = false,
        ),
        ModelInfo(
            displayName = "Qwen2-1.5B-Instruct",
            modelId = "litert-community/Qwen2-1.5B-Instruct",
            modelFile = "Qwen2_1.5B_Instruct.litertlm",
            commitHash = "0292a162494daa04ab6d67da70a39ef353a281f2",
            sizeInBytes = 1802843056L,
            sha256 = "f0009d992f962157ee7164e7bf93d16229992fbb4ffe4ee055d0da47f189ad62",
            gated = false,
        ),
        ModelInfo(
            displayName = "SmolLM3-3B",
            modelId = "litert-community/SmolLM3-3B",
            modelFile = "SmolLM3-3B_q4_block32_ekv4096.litertlm",
            commitHash = "7621a4f099a00b0518e0a6e7c5628af08ab4184e",
            sizeInBytes = 2004633520L,
            sha256 = "bdd7eb05b6764fb5a5173bdbdd59354a26f66e4c20b040f0d3da3532071d18f6",
            gated = false,
        ),
        ModelInfo(
            // main(2026-10)은 litert-lm 0.18.0용 chat template(content parts)로 갱신돼 0.16.x에서
            // 히스토리/프롬프트가 깨진다. litertlm을 0.18+로 올리기 전까지 이전 리비전에 고정.
            displayName = "Qwen3-1.7B",
            modelId = "litert-community/Qwen3-1.7B",
            modelFile = "Qwen3_1.7B.litertlm",
            commitHash = "5d8fd1f27c771dbbbb185c9f05c3547760dd3cbd",
            sizeInBytes = 2056729520L,
            sha256 = "66064a4e9269cb693e124c4e3040bcb8a446b10bca42663896329495add3861c",
            gated = false,
        ),
        ModelInfo(
            displayName = "VibeThinker-3B",
            modelId = "litert-community/VibeThinker-3B",
            modelFile = "model.litertlm",
            commitHash = "7790b109a7861b2f6982ccc9877d27faba57a712",
            sizeInBytes = 2059236272L,
            sha256 = "7885496ccd8f70680474e66e88eccfdc966c1870a39b5fdbea837f897e4742a0",
            gated = false,
        ),
        ModelInfo(
            displayName = "Llama-3.2-3B",
            modelId = "litert-community/Llama-3.2-3B",
            modelFile = "llama3_2_3b_mixed_int4_gpu.litertlm",
            commitHash = "401735124eace78bbaee7bbf8f95581ab2ee63af",
            sizeInBytes = 2207940608L,
            sha256 = "63419d689ef141cc77d6ab5739f27426d779e1165546a4904edefa9fc36fea51",
            gated = true,
        ),
        ModelInfo(
            displayName = "Ministral-3-3B-Reasoning-2512",
            modelId = "litert-community/Ministral-3-3B-Reasoning-2512",
            modelFile = "model.litertlm",
            commitHash = "58e9cf609a545954a35fd01dcaaa66477d9d5f50",
            sizeInBytes = 2340982768L,
            sha256 = "a1f54b249b5a685617ab068b0aea882141f40923f7050460f1546485da5a1db0",
            gated = false,
        ),
        ModelInfo(
            displayName = "Ministral-3-3B-Instruct-2512",
            modelId = "litert-community/Ministral-3-3B-Instruct-2512",
            modelFile = "Ministral-3-3B-Instruct-2512_q4_block32_ekv4096.litertlm",
            commitHash = "f0d5ce0bf03a85e905b0590330fb69ae72784559",
            sizeInBytes = 2342866928L,
            sha256 = "13c47bcbaf305e4337f280b27c991168b0c624a6d60e00df10f0f23ebd28ad83",
            gated = false,
        ),
        ModelInfo(
            displayName = "Nanbeige4.1-3B",
            modelId = "litert-community/Nanbeige4.1-3B",
            modelFile = "model.litertlm",
            commitHash = "f81f27f37d41efca0f2e14e45659731f1659b3b9",
            sizeInBytes = 2409326576L,
            sha256 = "3393e469417d847d0a60d765d1af66cbf6c396e430dccc59162fb4867bd9f4e7",
            gated = false,
        ),
        ModelInfo(
            displayName = "Qwen3-4B-Thinking-2507",
            modelId = "litert-community/Qwen3-4B-Thinking-2507",
            modelFile = "model.litertlm",
            commitHash = "e1de66e5b50f3bc72203f55f6c109a0de6fd5069",
            sizeInBytes = 2477192112L,
            sha256 = "8a8a6a419a5570b3f4814d358fb795c0dcb6931206c7dae29480f47564c3f04d",
            gated = false,
        ),
        ModelInfo(
            displayName = "FastContext-1.0-4B-SFT",
            modelId = "litert-community/FastContext-1.0-4B-SFT",
            modelFile = "model_block128.litertlm",
            commitHash = "7100310612f694b7ffe8ee9a50a1c1af881037fe",
            sizeInBytes = 2474357680L,
            sha256 = "3842a564a1f8db2ed53992ec6e2d5e2579aff72131dfac664c19cf2ec90542ce",
            gated = false,
        ),
        ModelInfo(
            displayName = "Jan-nano",
            modelId = "litert-community/Jan-nano",
            modelFile = "model.litertlm",
            commitHash = "2fe2a324e8694570b1b889e9d9824b2acfdbf678",
            sizeInBytes = 2474357680L,
            sha256 = "67cfe76fd79acb0db652a767c22b5abcc7409cc064484de80faf516ccedf790a",
            gated = false,
        ),
        ModelInfo(
            displayName = "Polaris-4B-Preview",
            modelId = "litert-community/Polaris-4B-Preview",
            modelFile = "model.litertlm",
            commitHash = "e471ef18a5c70b7ba0fa608a7abd1fa950027caf",
            sizeInBytes = 2474357680L,
            sha256 = "e1edad5042c8354bec1d6187c22cad9e460a38428da517fc7ef22b3aa828eb3b",
            gated = false,
        ),
        ModelInfo(
            displayName = "MedGemma-1.5-4B-IT",
            modelId = "litert-community/MedGemma-1.5-4B-IT",
            modelFile = "medgemma-1.5-4b-it_q4_block32_ekv2048.litertlm",
            commitHash = "abba4da1132343617b51c180e7147a9742a26559",
            sizeInBytes = 2583871056L,
            sha256 = "6778edd3137104d8b47f65b24dbc21c8eecb0329c987a3d64bd2fba201e6bf51",
            gated = true,
        ),
        ModelInfo(
            // main(2026-10)은 litert-lm 0.18.0용 chat template(content parts)로 갱신돼 0.16.x에서
            // 히스토리/프롬프트가 깨진다. litertlm을 0.18+로 올리기 전까지 이전 리비전에 고정.
            displayName = "Qwen3-4B",
            modelId = "litert-community/Qwen3-4B",
            modelFile = "qwen3_4b_mixed_int4.litertlm",
            commitHash = "84cc5a35c9c65cd18fcd65bb1f3a7d77a4acfe6e",
            sizeInBytes = 2659057664L,
            sha256 = "f0794bc77efeaaf4f7af815f04c483b19b8f2ae4a102cef1b7b760a25848a18e",
            gated = false,
        ),
        ModelInfo(
            displayName = "Qwen3-4B-Instruct-2507",
            modelId = "litert-community/Qwen3-4B-Instruct-2507",
            modelFile = "qwen3_4b_instruct_2507_mixed_int4.litertlm",
            commitHash = "4d409771b414d86b270ca54d27ffc45032a81142",
            sizeInBytes = 2659057664L,
            sha256 = "9e48b165836256f5344d9d044930607b9c47f6ef34e27f82e96881664f3ba2fd",
            gated = false,
        ),
        ModelInfo(
            displayName = "Phi-4-mini-reasoning",
            modelId = "litert-community/Phi-4-mini-reasoning",
            modelFile = "model.litertlm",
            commitHash = "62a183af7b3d3d46f8df17dde9bf8381988bbf68",
            sizeInBytes = 2784187376L,
            sha256 = "d507262ca3d01d93d59c573a82e6f2eeb3aec21d9a67c31fb4c0f93d17225b8f",
            gated = false,
        ),
        ModelInfo(
            displayName = "Qwen2.5-Coder-3B-Instruct",
            modelId = "litert-community/Qwen2.5-Coder-3B-Instruct",
            modelFile = "Qwen2.5_Coder_3B_It.litertlm",
            commitHash = "f02778f1b0bb06315051c1e212abd19a389bff34",
            sizeInBytes = 3433083824L,
            sha256 = "78d23da074383f52f852b945b8090870e6c9dded02a842f535ee3ccb9e2874f3",
            gated = false,
        ),
        ModelInfo(
            displayName = "Phi-4-mini-instruct",
            modelId = "litert-community/Phi-4-mini-instruct",
            modelFile = "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            commitHash = "8cd368be75fdb94d5a6f6f5b40f1ab22a6c2543e",
            sizeInBytes = 3910090752L,
            sha256 = "7764d4deb53800578307be33039476b38a6c370fff71bedb3c0552563e23ab02",
            gated = false,
        ),
        ModelInfo(
            displayName = "DeepSeek-R1-Distill-Qwen-7B",
            modelId = "litert-community/DeepSeek-R1-Distill-Qwen-7B",
            modelFile = "DeepSeek-R1-Distill-Qwen-7B_q4_block32_ekv4096.litertlm",
            commitHash = "90dc72b44ebe0be5ba14c8b31de8b21ce3bcb11a",
            sizeInBytes = 4533682160L,
            sha256 = "050787a59d80acf064ed71209e45a28a7ae734a06f409f271751f254643216e1",
            gated = false,
        ),
        ModelInfo(
            displayName = "Qwen3-8B",
            modelId = "litert-community/Qwen3-8B",
            modelFile = "qwen3_8b_mixed_int4.litertlm",
            commitHash = "71ff705588319d52d374977eff3da4eee0c0d26e",
            sizeInBytes = 4887412736L,
            sha256 = "cb4e6d0de4bbf6656d177812cf0c6a983967dedd17e7f88e84b901c3a9862a42",
            gated = false,
        ),
        ModelInfo(
            displayName = "Qwen3-14B",
            modelId = "litert-community/Qwen3-14B",
            modelFile = "qwen3_14b_mixed_int4.litertlm",
            commitHash = "e4122fd370cec85c61467274b180e0954e4f422d",
            sizeInBytes = 8655863808L,
            sha256 = "71de7d58f1b46a3fcba2f7bb700ebcc3c3715877d9a7028d93dd1bcd89bbe946",
            gated = false,
        ),
    ).sortedBy { it.displayName.lowercase() }

    /** modelId 로 카탈로그 항목을 찾는다. */
    fun byId(modelId: String): ModelInfo? = ALL.firstOrNull { it.modelId == modelId }
}
