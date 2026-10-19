package com.eltnegcellist.emma.ai

import org.junit.Assert.*
import org.junit.Test

class MultilingualRoutingTest {
    @Test fun e5PolicyDoesNotChangeJapaneseAcceptance() {
        val lowScore=SemanticTopicPrediction("milk",0.2,0.01,interaction="none",interactionProbability=0.2,interactionMargin=0.01)
        val japanese=LiteResponseEngine().respondRuri("ミルク",semantic=lowScore)
        val foreign=LiteResponseEngine().respondRuri("Milch",semantic=lowScore.copy(mode="multilingual"))
        assertEquals("generic",japanese.scene)
        assertEquals("milk",foreign.scene)
    }
    @Test fun genericE5DoesNotInventATopic() {
        val generic=SemanticTopicPrediction("generic",0.8,0.6,mode="multilingual",interaction="none",interactionProbability=0.8,interactionMargin=0.6)
        assertEquals("generic",LiteResponseEngine().respondRuri("Thank you",semantic=generic).scene)
    }
}
