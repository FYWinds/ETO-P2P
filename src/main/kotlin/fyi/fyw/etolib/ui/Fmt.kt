package fyi.fyw.etolib.ui

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

/** Number and rate formatting, following Factory Flow's rules. */
object Fmt {

    /** A clock to show rates by. Rates are kept per second; [perSecond] is how many seconds one unit of it lasts. */
    enum class RateUnit(val suffix: String, val perSecond: Double) {
        TICK("/t", 1.0 / 20.0),
        SECOND("/s", 1.0),
        MINUTE("/min", 60.0),
        HOUR("/hr", 3600.0),
    }

    private val SUFFIXES = arrayOf("", "k", "M", "G", "T", "P")

    /** Below this a number is round-off and is shown as 0. */
    private const val NOTHING = 1e-6

    /**
     * Compact number: exact 0 (or round-off below a millionth) prints "0"; below 1 keeps 2 significant digits;
     * otherwise 2 decimals (1 from 100, none from 1000 before the suffix steps in) with trailing zeros dropped, then
     * k, M, G... e.g. 6930 -> "6.93k".
     */
    fun compact(value: Double): String {
        if (value == 0.0 || value.isNaN()) return "0"
        if (value.isInfinite()) return if (value > 0) "∞" else "-∞"
        val sign = if (value < 0) "-" else ""
        var v = abs(value)
        if (v < NOTHING) return "0"
        if (v < 1) return sign + strip(BigDecimal(v).round(MathContext(2)))
        var tier = 0
        while (v >= 1000 && tier < SUFFIXES.size - 1) {
            v /= 1000
            tier++
        }
        // Rounding up across a boundary (999.999 -> 1000) moves to the next suffix.
        val decimals = if (v >= 100) 1 else 2
        var rounded = BigDecimal.valueOf(v).setScale(decimals, RoundingMode.HALF_UP)
        if (rounded >= BigDecimal.valueOf(1000) && tier < SUFFIXES.size - 1) {
            tier++
            rounded = BigDecimal.valueOf(v / 1000).setScale(2, RoundingMode.HALF_UP)
        }
        return sign + strip(rounded) + SUFFIXES[tier]
    }

    /**
     * At most three significant figures, then k, M, G...: for numbers that must fit a small space whole ("167",
     * "33.3", "33.3k"). Below 1 it keeps two, as [compact] does.
     */
    fun brief(value: Double): String {
        if (value == 0.0 || value.isNaN()) return "0"
        if (value.isInfinite()) return if (value > 0) "∞" else "-∞"
        val sign = if (value < 0) "-" else ""
        var v = abs(value)
        if (v < NOTHING) return "0"
        if (v < 1) return sign + strip(BigDecimal(v).round(MathContext(2)))
        var tier = 0
        // 999.5 and up rounds to 1000, which reads as the next suffix.
        while (v >= 999.5 && tier < SUFFIXES.size - 1) {
            v /= 1000
            tier++
        }
        return sign + strip(BigDecimal(v).round(MathContext(3))) + SUFFIXES[tier]
    }

    /** A per-second rate in the given unit, e.g. "900/hr"; fluids get " L" before the unit ("180k L/hr"). */
    fun rate(perSecond: Double, unit: RateUnit, fluid: Boolean = false): String {
        val number = compact(perSecond * unit.perSecond)
        return if (fluid) number + " L" + unit.suffix else number + unit.suffix
    }

    /** EU/t with the compact rules, e.g. "6.93k". */
    fun power(euPerTick: Double): String = compact(euPerTick)

    private fun strip(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        return if (stripped.scale() < 0) stripped.setScale(0).toPlainString() else stripped.toPlainString()
    }
}
