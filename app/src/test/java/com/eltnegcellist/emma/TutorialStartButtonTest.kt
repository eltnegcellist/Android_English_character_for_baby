package com.eltnegcellist.emma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialStartButtonTest {
    @Test
    fun finishedIntroductionMovesOnlyFirstStepToConversationStart() {
        assertEquals(1, tutorialStepAfterIntroduction(0))
        assertEquals(1, tutorialStepAfterIntroduction(1))
        assertEquals(2, tutorialStepAfterIntroduction(2))
        assertEquals(null, tutorialStepAfterIntroduction(null))
    }

    @Test
    fun tutorialStepTwoButtonStaysEnabledWhenOnlyBusyIsStale() {
        assertTrue(tutorialStartButtonEnabled(modelReady = true, busy = true, tutorialStep = 1))
        assertTrue(tutorialStartButtonEnabled(modelReady = true, busy = false, tutorialStep = 1))
        assertFalse(tutorialStartButtonEnabled(modelReady = false, busy = false, tutorialStep = 1))
        assertFalse(tutorialStartButtonEnabled(modelReady = true, busy = true, tutorialStep = null))
        assertFalse(tutorialStartButtonEnabled(modelReady = true, busy = true, tutorialStep = 0))
        assertFalse(tutorialStartButtonEnabled(modelReady = true, busy = true, tutorialStep = 2))
    }
    @Test
    fun tutorialStepsTwoAndThreeHighlightAnAction() {
        assertFalse(tutorialHighlightsAction(null))
        assertFalse(tutorialHighlightsAction(0))
        assertTrue(tutorialHighlightsAction(1))
        assertTrue(tutorialHighlightsAction(2))
    }

}
