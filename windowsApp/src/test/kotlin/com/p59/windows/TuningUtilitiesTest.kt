package com.p59.windows

import kotlin.math.abs
import kotlin.test.Test

class TuningUtilitiesTest {
    @Test
    fun leanWidebandIncreasesFuelTableValue() {
        val result = TuningUtilities.fuelCorrection(12.8, 13.44, 100.0)
        check(abs(result.percent - 5.0) < 0.01)
        check(abs(result.correctedValue - 105.0) < 0.01)
    }

    @Test
    fun lowIdleRequestsMoreAirflow() {
        val result = TuningUtilities.idleRecommendation(750, 650, 8.0)
        check(result.rpmError == 100)
        check(result.airflowPercent > 0)
        check(result.correctedAirflow > 8.0)
    }

    @Test
    fun knockPullsTimingButLeanAfrBlocksSparkChange() {
        val knock = TuningUtilities.sparkRecommendation(24.0, 2.0, 12.7)
        check(knock.recommendedTiming == 21.0)
        val lean = TuningUtilities.sparkRecommendation(24.0, 2.0, 14.0)
        check(lean.recommendedTiming == 24.0)
    }
}
