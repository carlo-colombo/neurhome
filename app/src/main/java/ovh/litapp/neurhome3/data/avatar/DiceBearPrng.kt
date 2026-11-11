package ovh.litapp.neurhome3.data.avatar

import kotlin.math.floor

/**
 * DiceBear v10's key-derived PRNG, ported from the reference implementation
 * (<https://www.dicebear.com/specification/implement-dicebear-core/>).
 *
 * No decision is drawn from a shared sequence: every value is derived from
 * `"$seed:$key"`, so the same seed and key always produce the same value no matter which
 * other decisions were asked for first. That is what makes an avatar reproducible from
 * its seed alone. [shuffle] is the one exception - it is the only method that advances a
 * stateful [Mulberry32] instance, which is still deterministic for the same seed and key.
 */
class DiceBearPrng(private val seed: String) {

    /** The float in `[0, 1)` behind `"$seed:$key"`. */
    fun value(key: String): Double = Mulberry32(Fnv1a.hash("$seed:$key")).nextFloat()

    /**
     * Picks one of [items], or null when there are none. Multi-entry input is
     * deduplicated and sorted first so that caller order and duplicates cannot change the
     * result.
     */
    fun pick(key: String, items: List<String>): String? {
        if (items.isEmpty()) return null
        if (items.size == 1) return items[0]
        val unique = items.distinct()
        if (unique.size == 1) return unique[0]
        val ordered = unique.sorted()
        return ordered[floor(value(key) * ordered.size).toInt()]
    }

    /**
     * Picks one of [weights]' keys with probability proportional to its weight. All-zero
     * weights fall back to an unweighted [pick] so the choice stays deterministic.
     */
    fun weightedPick(key: String, weights: Map<String, Double>): String? {
        if (weights.isEmpty()) return null
        if (weights.size == 1) return weights.keys.first()
        val ordered = weights.keys.sorted()
        val total = ordered.sumOf { weights.getValue(it) }
        if (total == 0.0) return pick(key, ordered)
        val threshold = value(key) * total
        var cumulative = 0.0
        for (name in ordered) {
            cumulative += weights.getValue(name)
            if (threshold < cumulative) return name
        }
        return ordered.last()
    }

    /** True with probability `likelihood / 100`. */
    fun bool(key: String, likelihood: Double = 50.0): Boolean = value(key) * 100 < likelihood

    /**
     * A float in `[min, max]`, rounded to four decimals like the reference. A positive
     * [step] samples from the discrete buckets `min + i * step`, which is how a style asks
     * for "any multiple of 90 degrees"; `min` and `max` may be given in either order.
     */
    fun float(key: String, min: Double, max: Double, step: Double? = null): Double {
        val low = minOf(min, max)
        val high = maxOf(min, max)
        val raw = if (step != null && step > 0) {
            val buckets = floor((high - low) / step).toInt() + 1
            low + floor(value(key) * buckets) * step
        } else {
            low + value(key) * (high - low)
        }
        return jsRound(raw * 10000) / 10000
    }

    /** An integer in `[min, max]`, inclusive; `min` and `max` may be given in either order. */
    fun integer(key: String, min: Double, max: Double): Int {
        val low = minOf(min, max)
        val high = maxOf(min, max)
        return floor(value(key) * (high - low + 1)).toInt() + low.toInt()
    }

    /**
     * Fisher-Yates over a deduplicated, sorted copy of [items], driven by a stateful
     * [Mulberry32] seeded from `"$seed:$key"`. Colour groups take their winner from the
     * front of this rather than from [pick], which is why the shuffle has to match the
     * reference exactly.
     */
    fun shuffle(key: String, items: List<String>): List<String> {
        if (items.size <= 1) return items.toList()
        val result = items.distinct().sorted().toMutableList()
        val random = Mulberry32(Fnv1a.hash("$seed:$key"))
        for (i in result.lastIndex downTo 1) {
            val j = floor(random.nextFloat() * (i + 1)).toInt()
            val swap = result[i]
            result[i] = result[j]
            result[j] = swap
        }
        return result
    }
}

/**
 * JavaScript's `Math.round`: halves round toward positive infinity, where Kotlin's
 * `round` rounds halves away from zero. The reference rounds through this on every
 * sampled float, so a value landing exactly on a half must not round the other way.
 */
private fun jsRound(value: Double): Double {
    val floor = floor(value)
    return if (value - floor >= 0.5) floor + 1 else floor
}

/**
 * FNV-1a over UTF-16 code units. A character outside the Basic Multilingual Plane (an
 * emoji seed, for instance) hashes as its two surrogate code units, not as one code
 * point, or the avatar would differ from every other DiceBear port for that seed.
 */
internal object Fnv1a {
    fun hash(input: String): Int {
        var hash = 0x811C9DC5.toInt()
        for (index in input.indices) {
            hash = hash xor input[index].code
            hash *= 0x01000193
        }
        return hash
    }
}

/** Mulberry32: the reference's 32-bit stateful generator, kept bit-exact by `Int` overflow. */
internal class Mulberry32(seed: Int) {
    private var state: Int = seed

    fun nextUint32(): Int {
        state += 0x6D2B79F5
        val z = state
        var t = (z xor (z ushr 15)) * (z or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return t xor (t ushr 14)
    }

    /** The unsigned 32-bit value divided by 2^32, computed in `Double` as the reference does. */
    fun nextFloat(): Double = (nextUint32().toLong() and 0xFFFFFFFFL) / 4294967296.0
}
