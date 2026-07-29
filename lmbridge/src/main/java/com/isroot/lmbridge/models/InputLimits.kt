package com.isroot.lmbridge.models

/**
 * 인메모리 멀티모달 입력(오디오/이미지 바이트·문서 텍스트)의 크기 예산을 산출한다.
 *
 * 큰 미디어 바이트가 가드 없이 JVM 힙과 네이티브로 들어가면 저사양 기기에서
 * OOM/네이티브 크래시로 이어진다(비전이 4GB 기기에서 LMK에 SIGKILL되던 것과 동일한
 * 메모리 천장). 이를 우아하게 [LMBridgeError.InvalidInput]으로 막기 위해, 초기화 시점의
 * 기기 총 RAM으로 예산을 정한다. 파일 경로 소스([MultimodalContent.ImageFile]/
 * [MultimodalContent.AudioFile])는 힙에 적재되지 않으므로 이 예산의 적용 대상이 아니다.
 *
 * 순수 로직으로 분리해 JVM 단위 테스트가 가능하다(Android 의존 없음).
 */
internal object InputLimits {
    private const val MB: Long = 1024L * 1024
    private const val GB: Long = 1024L * 1024 * 1024

    /** ≤4GB RAM 기기의 인메모리 입력 예산. */
    const val TIER_LOW: Long = 4L * MB

    /** ≤6GB RAM 기기의 인메모리 입력 예산. */
    const val TIER_MID: Long = 16L * MB

    /** >6GB RAM 기기의 인메모리 입력 예산. */
    const val TIER_HIGH: Long = 64L * MB

    /**
     * 총 RAM(바이트)과 저사양 플래그로 인메모리 입력 바이트 예산을 산출한다.
     *
     * - `≤4GB` → [TIER_LOW], `≤6GB` → [TIER_MID], `>6GB` → [TIER_HIGH]
     * - [isLowRamDevice]가 true면 한 단계 낮춘다(최저 단계면 유지).
     *
     * @param totalMemBytes `ActivityManager.MemoryInfo.totalMem`(기기 총 RAM).
     */
    fun budgetForDevice(totalMemBytes: Long, isLowRamDevice: Boolean): Long {
        val base = when {
            totalMemBytes <= 4 * GB -> TIER_LOW
            totalMemBytes <= 6 * GB -> TIER_MID
            else -> TIER_HIGH
        }
        return if (isLowRamDevice) demote(base) else base
    }

    /** 예산을 한 단계 낮춘다(이미 최저 단계면 그대로 유지). */
    private fun demote(tier: Long): Long = when (tier) {
        TIER_HIGH -> TIER_MID
        TIER_MID -> TIER_LOW
        else -> TIER_LOW
    }
}
