package com.eltnegcellist.emma.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class RuriTopicRescueTest {
    private fun prediction(p: Double = .3, margin: Double = .1, label: String? = "none", ip: Double = .9) =
        SemanticTopicPrediction("sleep", p, margin, interaction = label, interactionProbability = ip)
    private fun reply(p: SemanticTopicPrediction?, enabled: Boolean = true) =
        LiteResponseEngine().respondRuri("arbitrary text", "", p, enabled)

    @Test fun rescueIncludesBothBoundariesAndRecordsReason() {
        val r = reply(prediction())
        assertEquals("sleep", r.scene)
        assertEquals("topic_rescued_both_generic", r.routingReason)
        assertEquals("generic", reply(prediction(.299999)).scene)
        assertEquals("generic", reply(prediction(margin = .099999)).scene)
        assertEquals("sleep", reply(prediction(.45184531619496915, .3236204758600866)).scene)
    }

    @Test fun acceptedInteractionIsNeverGivenAnExtraTopic() {
        assertEquals("interaction:affection", reply(prediction(label = "affection", ip = .5)).scene)
        assertEquals("sleep", reply(prediction(label = "affection", ip = .499999)).scene)
        assertEquals("combined:sleep:reassurance", reply(prediction(.5, .05, "reassurance", .5)).scene)
    }

    @Test fun normalFiftyPercentDecisionRetainsFivePointMargin() {
        val r = reply(prediction(.5, .05))
        assertEquals("sleep", r.scene)
        assertEquals("interaction_none", r.routingReason)
    }

    @Test fun missingDisabledGenericOrInvalidPredictionsCannotTriggerRescue() {
        assertEquals("generic", reply(prediction(label = null)).scene)
        assertEquals("generic", reply(prediction(), false).scene)
        assertEquals("generic", reply(null).scene)
        assertEquals("generic", reply(prediction().copy(topic = "generic")).scene)
        assertEquals("generic", reply(prediction(Double.NaN)).scene)
        assertEquals("generic", reply(prediction(ip = Double.NaN)).scene)
    }

    @Test fun diagnosticScoreIsNotReplacedByAnAcceptanceSentinel() {
        val p = prediction(.45184531619496915, .3236204758600866)
        assertEquals("sleep", reply(p).scene)
        assertEquals(.45184531619496915, p.probability, 0.0)
        assertEquals(.3236204758600866, p.margin, 0.0)
    }
}
