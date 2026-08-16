package com.p59.windows

import kotlin.math.abs

data class FuelCorrection(
    val multiplier: Double,
    val percent: Double,
    val correctedValue: Double
)

data class IdleRecommendation(
    val rpmError: Int,
    val airflowPercent: Double,
    val correctedAirflow: Double,
    val stable: Boolean
)

data class SparkRecommendation(
    val timingChange: Double,
    val recommendedTiming: Double,
    val message: String
)

object TuningUtilities {
    fun fuelCorrection(targetAfr: Double, measuredAfr: Double, currentValue: Double): FuelCorrection {
        require(targetAfr in 7.0..22.0) { "Target AFR must be between 7 and 22." }
        require(measuredAfr in 7.0..22.0) { "Measured AFR must be between 7 and 22." }
        require(currentValue > 0.0) { "Current table value must be positive." }
        val multiplier = measuredAfr / targetAfr
        return FuelCorrection(multiplier, (multiplier - 1.0) * 100.0, currentValue * multiplier)
    }

    fun idleRecommendation(targetRpm: Int, measuredRpm: Int, currentAirflow: Double): IdleRecommendation {
        require(targetRpm in 400..2000) { "Target idle must be between 400 and 2000 RPM." }
        require(measuredRpm in 0..3000) { "Measured idle must be between 0 and 3000 RPM." }
        require(currentAirflow > 0.0) { "Current airflow must be positive." }
        val error = targetRpm - measuredRpm
        val stable = abs(error) <= 25
        val adjustment = if (stable) 0.0 else (error.toDouble() / targetRpm * 35.0).coerceIn(-10.0, 10.0)
        return IdleRecommendation(error, adjustment, currentAirflow * (1.0 + adjustment / 100.0), stable)
    }

    fun sparkRecommendation(currentTiming: Double, knockRetard: Double, widebandAfr: Double): SparkRecommendation {
        require(currentTiming in -20.0..60.0) { "Spark timing must be between -20 and 60 degrees." }
        require(knockRetard in 0.0..30.0) { "Knock retard must be between 0 and 30 degrees." }
        require(widebandAfr in 7.0..22.0) { "Wideband AFR must be between 7 and 22." }
        if (widebandAfr > 13.2) {
            return SparkRecommendation(0.0, currentTiming, "Mixture is lean for a typical gasoline WOT pull; correct fueling before changing spark.")
        }
        if (knockRetard <= 0.5) {
            return SparkRecommendation(0.0, currentTiming, "No meaningful knock detected. Hold timing and validate with repeated logs.")
        }
        val reduction = (knockRetard + 1.0).coerceAtMost(6.0)
        return SparkRecommendation(-reduction, currentTiming - reduction, "Remove timing in the affected cells, then repeat the pull and inspect knock again.")
    }
}
