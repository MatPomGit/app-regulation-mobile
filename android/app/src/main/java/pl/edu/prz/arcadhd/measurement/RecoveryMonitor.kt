package pl.edu.prz.arcadhd.measurement

import kotlin.math.max
import kotlin.math.sqrt

enum class RecoveryFeatureModel { B, B_S, B_V, B_S_V }

data class RecoveryReference(
    val goCorrect: Int,
    val goTotal: Int,
    val noGoCorrect: Int,
    val noGoTotal: Int,
    val correctGoRtMs: List<Double>,
    val deviceTimingFloorMs: Double,
) {
    init {
        require(goTotal >= 0 && noGoTotal >= 0)
        require(goCorrect in 0..goTotal && noGoCorrect in 0..noGoTotal)
        require(correctGoRtMs.all { it.isFinite() && it >= 0 })
        require(deviceTimingFloorMs.isFinite() && deviceTimingFloorMs > 0)
    }
}

data class RecoveryWindow(
    val goCorrect: Int,
    val goTotal: Int,
    val noGoCorrect: Int,
    val noGoTotal: Int,
    val correctGoRtMs: List<Double>,
) {
    init {
        require(goTotal >= 0 && noGoTotal >= 0)
        require(goCorrect in 0..goTotal && noGoCorrect in 0..noGoTotal)
        require(correctGoRtMs.all { it.isFinite() && it >= 0 })
    }
}

data class RecoveryScore(
    val model: RecoveryFeatureModel,
    val q: Double,
    val behavioralDeficit: Double,
    val slowingDeficit: Double?,
    val variabilityDeficit: Double?,
)

object RecoveryScorer {
    fun score(
        reference: RecoveryReference,
        window: RecoveryWindow,
        model: RecoveryFeatureModel,
    ): RecoveryScore? {
        if (reference.goTotal < 4 || reference.noGoTotal < 4 ||
            window.goTotal < 2 || window.noGoTotal < 2
        ) {
            return null
        }

        val pRefGo = jeffreys(reference.goCorrect, reference.goTotal)
        val pRefNoGo = jeffreys(reference.noGoCorrect, reference.noGoTotal)
        val pWinGo = jeffreys(window.goCorrect, window.goTotal)
        val pWinNoGo = jeffreys(window.noGoCorrect, window.noGoTotal)
        val bRef = 0.5 * (pRefGo + pRefNoGo)
        val bNow = 0.5 * (pWinGo + pWinNoGo)
        val scaleB = 0.5 * sqrt(
            betaVariance(reference.goCorrect, reference.goTotal) +
                betaVariance(reference.noGoCorrect, reference.noGoTotal) +
                pRefGo * (1.0 - pRefGo) / window.goTotal +
                pRefNoGo * (1.0 - pRefNoGo) / window.noGoTotal,
        )
        val dB = max(0.0, (bRef - bNow) / max(scaleB, EPSILON_B))

        val needsS = model == RecoveryFeatureModel.B_S || model == RecoveryFeatureModel.B_S_V
        val needsV = model == RecoveryFeatureModel.B_V || model == RecoveryFeatureModel.B_S_V
        if ((needsS || needsV) && reference.correctGoRtMs.size < 4) return null
        if (needsS && window.correctGoRtMs.size < 3) return null
        if (needsV && window.correctGoRtMs.size < 4) return null

        val timingScale = if (needsS || needsV) {
            max(1.4826 * mad(reference.correctGoRtMs), reference.deviceTimingFloorMs)
        } else {
            null
        }
        val dS = if (needsS) {
            max(
                0.0,
                (median(window.correctGoRtMs) - median(reference.correctGoRtMs)) /
                    requireNotNull(timingScale),
            )
        } else {
            null
        }
        val dV = if (needsV) {
            val currentScale = max(
                1.4826 * mad(window.correctGoRtMs),
                reference.deviceTimingFloorMs,
            )
            max(0.0, currentScale / requireNotNull(timingScale) - 1.0)
        } else {
            null
        }

        val q = when (model) {
            RecoveryFeatureModel.B -> -dB
            RecoveryFeatureModel.B_S -> -(0.5 * dB + 0.5 * requireNotNull(dS))
            RecoveryFeatureModel.B_V -> -(0.5 * dB + 0.5 * requireNotNull(dV))
            RecoveryFeatureModel.B_S_V ->
                -(0.5 * dB + 0.25 * requireNotNull(dS) + 0.25 * requireNotNull(dV))
        }
        return RecoveryScore(model, q, dB, dS, dV)
    }

    private fun jeffreys(successes: Int, total: Int): Double =
        (successes + 0.5) / (total + 1.0)

    private fun betaVariance(successes: Int, total: Int): Double {
        val a = successes + 0.5
        val b = total - successes + 0.5
        val sum = a + b
        return (a * b) / (sum * sum * (sum + 1.0))
    }

    private fun median(values: List<Double>): Double {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }

    private fun mad(values: List<Double>): Double {
        val center = median(values)
        return median(values.map { kotlin.math.abs(it - center) })
    }

    private const val EPSILON_B = 1e-9
}

// Study-specific scoring above; generic monitoring is owned by LIB-ADAPT.
typealias RecoveryMonitorConfig = org.matpomgit.adapt.RecoveryMonitorConfig
typealias RecoveryMonitorStatus = org.matpomgit.adapt.RecoveryMonitorStatus
typealias RecoveryMonitorState = org.matpomgit.adapt.RecoveryMonitorState

/** Synthetic engineering configuration; not a frozen participant protocol. */
fun phase0SyntheticMonitorConfig() = RecoveryMonitorConfig(
    delta = 0.5,
    stabilityWindowUpdates = 3,
    censorLimitMs = 20_000,
)
