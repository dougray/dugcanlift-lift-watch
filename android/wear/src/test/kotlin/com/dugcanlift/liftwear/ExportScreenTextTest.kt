package com.dugcanlift.liftwear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-logic slice of ExportScreen's watchOS-parity fixes (M5, M9, M10 in the parity audit).
 *
 * The confirmation *gate* itself (M5 -- "Done — clear these" must arm a dialog rather than clear
 * immediately) is a Compose interaction with no test harness in this module: :wear has no
 * androidx.compose.ui.test / createComposeRule dependency, so the click -> dialog -> destructive-tap
 * wiring in ExportScreen's HorizontalPager content is not exercised by a unit test. What is covered
 * here is everything that can be pulled out as pure functions: the exact copy the confirmation dialog
 * must show (matched verbatim against ExportFoodsView.swift), and the empty-state / caption /
 * pluralisation text logic.
 */
class ExportScreenTextTest {
    // M9: Wear's ordinary empty state used to read "Nothing to export.", diverging word-for-word from
    // watchOS's "Nothing logged yet." for the identical state. The expired-log variant is Wear-only
    // (watchOS deletes on write instead of hiding on read, see audit #2/#3) and must be kept.
    @Test fun `empty state matches watchOS when nothing was ever logged`() {
        assertEquals("Nothing logged yet.", exportEmptyMessage(expiredCount = 0))
    }

    @Test fun `the aged-out variant is retained and takes priority over the base message`() {
        assertEquals("Nothing left to export — 9 logged over 60 days ago.", exportEmptyMessage(expiredCount = 9))
    }

    // M10: watchOS suppresses the "n / total" caption for a single code (ExportFoodsView.swift:59).
    @Test fun `no page caption is drawn for a single-code export`() {
        assertNull(exportPageCaption(page = 0, total = 1))
    }

    @Test fun `a multi-code export still captions each page`() {
        assertEquals("1 / 3", exportPageCaption(page = 0, total = 3))
        assertEquals("3 / 3", exportPageCaption(page = 2, total = 3))
    }

    // M10: watchOS reads "Scanned it?" for one code, "Scan all N codes, then:" for many
    // (ExportFoodsView.swift:73-75); Wear's confirm-page question mirrors the same split.
    @Test fun `the confirm prompt is not pluralised for a single code`() {
        assertEquals("Scanned it?", exportConfirmPrompt(total = 1))
    }

    @Test fun `the confirm prompt pluralises for more than one code`() {
        assertEquals("Scanned all 3?", exportConfirmPrompt(total = 3))
    }

    // M10 (the non-wording half): the caption's reserved band must not survive into the size
    // calculation for a single code, or the fix would be cosmetic only -- the code would still be
    // drawn smaller than it needs to be, on the one screen where module size decides whether a
    // phone camera can focus.
    @Test fun `no caption band is reserved in the QR size calculation for a single code`() {
        assertEquals(0, captionReserveFor(total = 1, captionReservePx = 60))
    }

    @Test fun `the caption band is still reserved for a multi-code export`() {
        assertEquals(60, captionReserveFor(total = 3, captionReservePx = 60))
    }

    // M5: the confirmation dialog's copy must match ExportFoodsView.swift's `confirmationDialog`
    // verbatim, including the "cannot be undone" line -- this is what confirms the fix was ported
    // from watchOS rather than invented.
    @Test fun `the clear-log confirmation copy matches watchOS verbatim`() {
        assertEquals("Clear the log?", CLEAR_LOG_TITLE)
        assertEquals("Only do this once the codes have been scanned. This cannot be undone.", CLEAR_LOG_MESSAGE)
    }
}
