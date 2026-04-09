package com.isroot.lmbridge

import android.content.Context
import com.isroot.lmbridge.inference.ModelInferenceManager
import com.isroot.lmbridge.models.MultimodalInput

/**
 * LMBridge SDK 진입점.
 * 클라이언트 앱은 이 클래스를 통해 모델 초기화 및 추론을 수행합니다.
 */
class LMBridgeClient private constructor(
    private val context: Context,
    private val modelPath: String? = null
) {
    private val inferenceManager = ModelInferenceManager(context, modelPath)

    /**
     * 지정된 모델 경로를 사용하여 AI 추론 엔진을 초기화합니다.
     */
    suspend fun initialize() {
        inferenceManager.initialize()
    }

    /**
     * 멀티모달 입력(텍스트, 이미지, 오디오 등)을 기반으로 결과를 생성합니다.
     */
    suspend fun generateResponse(input: MultimodalInput): String {
        return inferenceManager.generate(input)
    }

    /**
     * 리소스를 해제합니다.
     */
    fun release() {
        inferenceManager.close()
    }

    class Builder(private val context: Context) {
        private var modelPath: String? = null

        fun setModelPath(path: String): Builder {
            this.modelPath = path
            return this
        }

        fun build(): LMBridgeClient {
            return LMBridgeClient(context, modelPath)
        }
    }
}
