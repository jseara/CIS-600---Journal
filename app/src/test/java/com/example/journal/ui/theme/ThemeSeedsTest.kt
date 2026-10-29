package com.example.journal.ui.theme

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [initialSeedPage] and the [ThemeSeeds] list.
 *
 * [initialSeedPage] works on plain ARGB ints, so most tests pass in small hand-made lists.
 * The list checks compare colours via `toArgb()` (pure Kotlin, runs on the JVM without Android)
 * and check that the string-resource ids are distinct; whether each id points at a real string
 * is left to the build.
 */
class ThemeSeedsTest {

    private val argbs = listOf(0xFF111111.toInt(), 0xFF222222.toInt(), 0xFF333333.toInt())

    // ---- initialSeedPage ----

    @Test
    fun initialSeedPage_nullSelectionInListWithoutNull_returnsDefault() {
        // No null (Default) page in this list, so a null selection falls back to DEFAULT_SEED_PAGE.
        assertEquals(DEFAULT_SEED_PAGE, initialSeedPage(argbs, null))
    }

    @Test
    fun initialSeedPage_matchInMiddle_returnsItsIndex() {
        assertEquals(1, initialSeedPage(argbs, 0xFF222222.toInt()))
    }

    @Test
    fun initialSeedPage_matchAtLastIndex_returnsLastIndex() {
        assertEquals(2, initialSeedPage(argbs, 0xFF333333.toInt()))
    }

    @Test
    fun initialSeedPage_emptyList_returnsDefaultForAnySelection() {
        assertEquals(DEFAULT_SEED_PAGE, initialSeedPage(emptyList(), 0xFF111111.toInt()))
        assertEquals(DEFAULT_SEED_PAGE, initialSeedPage(emptyList(), null))
    }

    @Test
    fun initialSeedPage_unknownColor_returnsDefault() {
        assertEquals(DEFAULT_SEED_PAGE, initialSeedPage(argbs, 0xFF999999.toInt()))
    }

    @Test
    fun initialSeedPage_sameRgbDifferentAlpha_isNotAMatch() {
        // ARGB comparison is exact, so a translucent version of a seed is a different colour.
        assertEquals(DEFAULT_SEED_PAGE, initialSeedPage(argbs, 0x80333333.toInt()))
    }

    @Test
    fun initialSeedPage_duplicateColors_firstIndexWins() {
        val withDuplicates = listOf(0xFF111111.toInt(), 0xFF222222.toInt(), 0xFF222222.toInt())
        assertEquals(1, initialSeedPage(withDuplicates, 0xFF222222.toInt()))
    }

    // ---- ThemeSeeds ----

    @Test
    fun themeSeeds_hasTenSeeds() {
        assertEquals(10, ThemeSeeds.size)
    }

    @Test
    fun themeSeeds_nameResourcesAreAllDistinct() {
        // Each seed needs its own spoken name for TalkBack; a copy-pasted id would repeat one.
        assertEquals(ThemeSeeds.size, ThemeSeeds.map { it.nameRes }.toSet().size)
    }

    @Test
    fun themeSeeds_colorValues_matchTheMaterial500Palette_inOrder() {
        // Pins the exact data the user picks from: Red, Pink, Purple, Indigo, Blue, Teal,
        // Green, Amber, Orange, Brown (all fully opaque).
        val expected = listOf(
            0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF3F51B5, 0xFF2196F3,
            0xFF009688, 0xFF4CAF50, 0xFFFFC107, 0xFFFF9800, 0xFF795548,
        ).map { it.toInt() }
        assertEquals(expected, ThemeSeeds.map { it.color.toArgb() })
    }

    @Test
    fun themeSeeds_colorsAreAllDistinct() {
        // Duplicate colours would make two picker pages indistinguishable and break saved-selection lookup.
        val colors = ThemeSeeds.map { it.color.toArgb() }
        assertEquals(colors.size, colors.toSet().size)
    }

    @Test
    fun themeSeeds_colorsAreFullyOpaque() {
        assertTrue(ThemeSeeds.all { (it.color.toArgb() ushr 24) == 0xFF })
    }

    // ---- ThemePickerPages: the picker's real input (Default page first, then the seeds) ----

    /** The list the picker really passes to initialSeedPage: null for Default, else the ARGB. */
    private val pageArgbs: List<Int?> = ThemePickerPages.map { it?.color?.toArgb() }

    @Test
    fun initialSeedPage_listWithSeveralNulls_nullSelectionReturnsFirstNull() {
        val withNulls = listOf(0xFF111111.toInt(), null, 0xFF222222.toInt(), null)
        assertEquals(1, initialSeedPage(withNulls, null))
    }

    @Test
    fun initialSeedPage_listWithNull_colorSelectionSkipsNullEntries() {
        assertEquals(2, initialSeedPage(listOf(null, 0xFF111111.toInt(), 0xFF222222.toInt()), 0xFF222222.toInt()))
    }

    @Test
    fun themePickerPages_hasElevenEntries() {
        assertEquals(11, ThemePickerPages.size)
    }

    @Test
    fun themePickerPages_entriesOneToTen_equalThemeSeedsInOrder() {
        assertEquals(ThemeSeeds, ThemePickerPages.drop(1))
    }

    @Test
    fun themePickerPages_defaultSeedPage_pointsAtTheNullEntry() {
        assertNull(ThemePickerPages[DEFAULT_SEED_PAGE])
    }

    @Test
    fun pickerInput_nullSelection_returnsDefaultPage() {
        // Documents the picker's real input only: a null selection matches the null Default page
        // at index 0, and the "no match" fallback is also 0, so this can't tell the two apart.
        assertEquals(0, initialSeedPage(pageArgbs, null))
    }

    @Test
    fun pickerInput_eachSeedArgb_returnsSeedIndexPlusOne() {
        // Page 0 is Default, so the seed at ThemeSeeds[i] lives on page i + 1.
        ThemeSeeds.forEachIndexed { i, seed ->
            assertEquals("seed $i", i + 1, initialSeedPage(pageArgbs, seed.color.toArgb()))
        }
    }

}
