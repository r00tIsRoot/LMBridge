package com.isroot.lmbridge.models

import org.junit.Assert.assertEquals
import org.junit.Test

/** InputLimits의 기기-적응형 예산 티어 로직 단위 테스트 — 기기 불필요. */
class InputLimitsTest {

    private val gb = 1024L * 1024 * 1024

    @Test
    fun `4GB device gets low tier`() {
        // SM-A426N(~4GB) 등 저용량 기기.
        assertEquals(InputLimits.TIER_LOW, InputLimits.budgetForDevice(4 * gb, isLowRamDevice = false))
        assertEquals(InputLimits.TIER_LOW, InputLimits.budgetForDevice(3 * gb, isLowRamDevice = false))
    }

    @Test
    fun `6GB device gets mid tier`() {
        assertEquals(InputLimits.TIER_MID, InputLimits.budgetForDevice(6 * gb, isLowRamDevice = false))
        assertEquals(InputLimits.TIER_MID, InputLimits.budgetForDevice(5 * gb, isLowRamDevice = false))
    }

    @Test
    fun `above 6GB device gets high tier`() {
        assertEquals(InputLimits.TIER_HIGH, InputLimits.budgetForDevice(8 * gb, isLowRamDevice = false))
        assertEquals(InputLimits.TIER_HIGH, InputLimits.budgetForDevice(12 * gb, isLowRamDevice = false))
    }

    @Test
    fun `boundary at 4GB is inclusive of low tier`() {
        // 정확히 4GB는 low, 4GB+1바이트는 mid.
        assertEquals(InputLimits.TIER_LOW, InputLimits.budgetForDevice(4 * gb, isLowRamDevice = false))
        assertEquals(InputLimits.TIER_MID, InputLimits.budgetForDevice(4 * gb + 1, isLowRamDevice = false))
    }

    @Test
    fun `low ram flag demotes one tier`() {
        assertEquals(InputLimits.TIER_MID, InputLimits.budgetForDevice(8 * gb, isLowRamDevice = true))
        assertEquals(InputLimits.TIER_LOW, InputLimits.budgetForDevice(6 * gb, isLowRamDevice = true))
    }

    @Test
    fun `low ram flag never goes below lowest tier`() {
        assertEquals(InputLimits.TIER_LOW, InputLimits.budgetForDevice(3 * gb, isLowRamDevice = true))
    }
}
