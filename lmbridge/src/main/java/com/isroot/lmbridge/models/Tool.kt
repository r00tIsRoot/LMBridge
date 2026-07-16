package com.isroot.lmbridge.models

/**
 * LMBridge 도구 선언.
 *
 * litertlm의 `ToolProvider`/`OpenApiTool`을 공개 API로 노출하지 않기 위한 자체 타입이다.
 * 어댑터가 이 선언을 litertlm 도구 형식으로 변환한다(버전 독립성).
 *
 * @property name 함수 이름(모델이 호출할 식별자)
 * @property description 함수 설명(모델이 언제 호출할지 판단하는 근거)
 * @property params 매개변수 스키마
 * @property executor (선택) 자동 도구 호출 시 실행 핸들러. `null`이면 선언 전용이며,
 *   모델의 호출은 [GenerationChunk.ToolCall]로 소비 앱에 전달되어 앱이 직접 처리한다.
 *   지정 시 인자 맵을 받아 결과를 JSON 문자열로 반환한다.
 */
data class Tool(
    val name: String,
    val description: String,
    val params: List<Param> = emptyList(),
    val executor: ((args: Map<String, Any?>) -> String)? = null,
) {
    /**
     * 도구 매개변수.
     *
     * @property name 매개변수 이름
     * @property type JSON 스키마 타입("string", "number", "integer", "boolean", "array", "object")
     * @property description 매개변수 설명
     * @property required 필수 여부
     */
    data class Param(
        val name: String,
        val type: String = "string",
        val description: String = "",
        val required: Boolean = true,
    )

    class Builder(private val name: String, private val description: String) {
        private val params = mutableListOf<Param>()
        private var executor: ((Map<String, Any?>) -> String)? = null

        fun param(name: String, type: String = "string", description: String = "", required: Boolean = true) =
            apply { params.add(Param(name, type, description, required)) }

        fun executor(handler: (args: Map<String, Any?>) -> String) = apply { this.executor = handler }

        fun build() = Tool(name, description, params.toList(), executor)
    }

    companion object {
        fun builder(name: String, description: String) = Builder(name, description)
    }
}
