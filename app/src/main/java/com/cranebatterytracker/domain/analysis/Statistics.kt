package com.cranebatterytracker.domain.analysis

import kotlin.math.abs

/** Small robust-statistics helpers. The app favors median/MAD over mean/stddev (spec section 35). */
object Statistics {

    fun median(values: List<Long>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid].toDouble()
        }
    }

    /** Median Absolute Deviation: median of |x - median(x)|. */
    fun medianAbsoluteDeviation(values: List<Long>): Double? {
        val med = median(values) ?: return null
        val deviations = values.map { abs(it - med) }
        return median(deviations.map { it.toLong() })
    }

    /** Modified z-score using MAD, per Iglewicz & Hoaglin. Returns null if MAD is zero/undefined. */
    fun modifiedZScore(value: Long, values: List<Long>): Double? {
        val med = median(values) ?: return null
        val mad = medianAbsoluteDeviation(values) ?: return null
        if (mad == 0.0) return null
        return 0.6745 * (value - med) / mad
    }
}
