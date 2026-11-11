package ovh.litapp.neurhome3.data.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the PRNG against DiceBear's own cross-language parity fixtures
 * (`tests/fixtures/parity/fnv1a.json` and `prng.json` in the dicebear repository, 10.x).
 *
 * These are the vectors every reference port is tested with, so agreeing with them is what
 * makes this port's avatars match api.dicebear.com for the same seed. The Unicode cases
 * matter most: FNV-1a hashes UTF-16 code units, so an emoji seed has to hash as two
 * surrogate units, and the sort order of `pick`/`shuffle` compares those units too.
 */
class DiceBearPrngTest {

    @Test
    fun fnv1aMatchesTheReferenceVectors() {
        val vectors = listOf(
            "" to "811c9dc5",
            "a" to "e40c292c",
            "hello" to "4f9f2cab",
            "test:flip" to "84c1764e",
            "some-long-seed:optionName" to "0cf1678c",
            "é" to "6c0b6c44",
            "日本語" to "5406374e",
            "🎲" to "3f2e6deb",
        )
        for ((input, expected) in vectors) {
            assertEquals(
                "fnv1a($input)",
                expected,
                Fnv1a.hash(input).toUInt().toString(16).padStart(8, '0'),
            )
        }
    }

    @Test
    fun getValueMatchesTheReferenceVectors() {
        val vectors = listOf(
            Triple("test", "flip", 0.5045499159023166),
            Triple("test", "scale", 0.7226385520771146),
            Triple("test", "rotate", 0.22723248694092035),
            Triple("hello", "eyesVariant", 0.0353052020072937),
            Triple("日本語", "unicode-key", 0.5431328006088734),
            Triple("", "empty-seed", 0.39638678822666407),
        )
        for ((seed, key, expected) in vectors) {
            assertEquals("getValue($seed, $key)", expected, DiceBearPrng(seed).value(key), 1e-15)
        }
    }

    @Test
    fun pickMatchesTheReferenceVectors() {
        val five = listOf("a", "b", "c", "d", "e")
        assertEquals("a", DiceBearPrng("test").pick("a", five))
        assertEquals("d", DiceBearPrng("test").pick("b", five))
        assertEquals("b", DiceBearPrng("test").pick("c", five))
        assertEquals("blue", DiceBearPrng("hello").pick("eyes", listOf("blue", "green", "brown", "hazel")))
        // Caller order and duplicates must not change the outcome.
        assertEquals("a", DiceBearPrng("test").pick("a", five.reversed()))
        assertEquals("a", DiceBearPrng("test").pick("a", five + five))
        assertEquals("only", DiceBearPrng("test").pick("single", listOf("only")))
        assertEquals("only", DiceBearPrng("test").pick("single", listOf("only", "only", "only")))
        // Sorted by UTF-16 code unit: a surrogate pair (D83D) sorts before U+E000.
        assertEquals("🤖", DiceBearPrng("test").pick("a", listOf("🤖", "\uE000")))
        assertEquals("🤖", DiceBearPrng("hello").pick("k", listOf("z", "\uE000", "🤖", "a")))
        assertNull(DiceBearPrng("test").pick("a", emptyList()))
    }

    @Test
    fun weightedPickMatchesTheReferenceVectors() {
        val weights = mapOf("a" to 1.0, "b" to 4.0, "c" to 2.0)
        assertEquals("b", DiceBearPrng("test").weightedPick("k", weights))
        assertEquals("b", DiceBearPrng("test").weightedPick("l", weights))
        assertEquals("b", DiceBearPrng("hello").weightedPick("k", weights))
        assertEquals("b", DiceBearPrng("test").weightedPick("k", mapOf("c" to 2.0, "a" to 1.0, "b" to 4.0)))
        assertEquals("common", DiceBearPrng("test").weightedPick("k", mapOf("rare" to 0.0, "common" to 1.0)))
        assertEquals("only", DiceBearPrng("test").weightedPick("k", mapOf("only" to 1.0)))
        assertEquals("a", DiceBearPrng("test").weightedPick("k", mapOf("c" to 0.1, "a" to 0.2, "b" to 0.3)))
        assertEquals("a", DiceBearPrng("test").weightedPick("k", mapOf("🤖" to 1.0, "\uE000" to 2.0, "a" to 3.0)))
        assertNull(DiceBearPrng("test").weightedPick("k", emptyMap()))
    }

    @Test
    fun boolMatchesTheReferenceVectors() {
        val prng = DiceBearPrng("test")
        assertEquals(false, prng.bool("k", 0.0))
        assertEquals(true, prng.bool("k", 25.0))
        assertEquals(true, prng.bool("k", 50.0))
        assertEquals(true, prng.bool("k", 75.0))
        assertEquals(true, prng.bool("k", 100.0))
        assertEquals(false, DiceBearPrng("hello").bool("flip", 50.0))
        assertEquals(true, DiceBearPrng("dicebear").bool("flip", 50.0))
    }

    @Test
    fun floatMatchesTheReferenceVectors() {
        val prng = DiceBearPrng("test")
        assertEquals(0.1722, prng.float("k", 0.0, 1.0), 1e-9)
        assertEquals(-6.555, prng.float("k", -10.0, 10.0), 1e-9)
        assertEquals(11.7225, prng.float("k", 10.0, 20.0), 1e-9)
        // A reversed range is sorted internally.
        assertEquals(11.7225, prng.float("k", 20.0, 10.0), 1e-9)
        assertEquals(15.0, prng.float("k", 0.0, 100.0, step = 5.0), 1e-9)
        assertEquals(3.0, prng.float("k", 3.0, 23.0, step = 5.0), 1e-9)
        assertEquals(5.0, DiceBearPrng("parity-step").float("k", -10.0, 10.0, step = 2.5), 1e-9)
        assertEquals(42.0, prng.float("k", 42.0, 42.0), 1e-9)
        // Halves round toward +Infinity, so this is -0.4062 rather than -0.4063.
        assertEquals(-0.4062, DiceBearPrng("549449").float("colorAngle", -1.0, 1.0), 1e-9)
    }

    @Test
    fun integerMatchesTheReferenceVectors() {
        val prng = DiceBearPrng("test")
        assertEquals(2, prng.integer("k", 1.0, 6.0))
        assertEquals(17, prng.integer("k", 0.0, 100.0))
        assertEquals(-33, prng.integer("k", -50.0, 50.0))
    }

    @Test
    fun shuffleMatchesTheReferenceVectors() {
        val eight = listOf("a", "b", "c", "d", "e", "f", "g", "h")
        assertEquals(
            listOf("e", "h", "f", "d", "a", "g", "c", "b"),
            DiceBearPrng("test").shuffle("k", eight),
        )
        assertEquals(
            listOf("b", "h", "a", "c", "d", "g", "e", "f"),
            DiceBearPrng("hello").shuffle("k", eight),
        )
        assertEquals(
            listOf("e", "h", "f", "d", "a", "g", "c", "b"),
            DiceBearPrng("test").shuffle("k", eight.reversed()),
        )
        assertEquals(listOf("b", "c", "a"), DiceBearPrng("test").shuffle("k", listOf("a", "a", "b", "b", "c", "c")))
        assertEquals(
            listOf("z", "🤖", "\uE000", "a"),
            DiceBearPrng("test").shuffle("k", listOf("z", "\uE000", "🤖", "a")),
        )
        assertEquals(emptyList<String>(), DiceBearPrng("test").shuffle("k", emptyList()))
        assertEquals(listOf("only"), DiceBearPrng("test").shuffle("k", listOf("only")))
    }
}
