package com.eltnegcellist.emma.ai

/**
 * Human-authored, preapproved-format candidate pairings of Ruri Topic × Interaction.
 * No Japanese text, keywords, or rule matcher is used to classify either axis.
 * A missing pairing falls back to a fixed single-axis English bank.
 */
internal object RuriCompositeReplies {
    val combinations: Map<String, List<String>> = mapOf(
        "milk|invitation" to listOf("Shall we have milk? Little sips are fine. I'm here with you."),
        "milk|praise" to listOf("You drank some milk! Well done, little one. I'm proud of you."),
        "milk|achievement" to listOf("You drank your milk! You did it! Well done, sweetheart."),
        "milk|finish" to listOf("Milk is all done! You're finished now. Let's rest together."),
        "milk|finish_check" to listOf("Finished your milk? Shall we check? What do you think?"),
        "milk|encouragement" to listOf("Try a little sip. There's no hurry. I'm right here."),
        "diaper|invitation" to listOf("Shall we change diapers? I'll be gentle. Let's get comfy."),
        "diaper|finish" to listOf("All done changing! Nice and fresh. Here we are."),
        "diaper|finish_check" to listOf("Diaper all done? Let's have a look. All clean now?"),
        "diaper|reassurance" to listOf("It's diaper time. I'll be gentle. I'm here with you."),
        "hug|invitation" to listOf("Want a little cuddle? Come here, sweetheart. I'm right here."),
        "hug|reassurance" to listOf("Come for a cuddle. I'm right here. I've got you."),
        "cry|reassurance" to listOf("I hear you crying. I'm right here. Come for a cuddle."),
        "cry|inquiry" to listOf("I hear you crying. What's the matter? I'm listening, sweetheart."),
        "sleep|invitation" to listOf("Shall we rest now? Let's get cozy. I'll stay close."),
        "sleep|reassurance" to listOf("Ready for a rest? I'm right here. We have time."),
        "wake|affection" to listOf("Hello, sleepyhead! You're awake now. I love seeing you."),
        "book|invitation" to listOf("Shall we read together? Here's our book. Let's look inside."),
        "book|repeat" to listOf("One more page? Let's read again. Turn the page."),
        "book|start" to listOf("Here comes our book. Let's begin reading. What comes next?"),
        "smile|affection" to listOf("What a lovely smile! You're so sweet. I love you."),
        "smile|praise" to listOf("What a sweet smile! So lovely to see. Hello, little one."),
        "play|invitation" to listOf("Shall we play together? Let's try something. This could be fun."),
        "play|repeat" to listOf("Let's play again! One more time? Ready when you are."),
        "play|start" to listOf("Ready to play? Let's get started. Here we go."),
        "food|invitation" to listOf("Shall we eat? One little bite. I'll stay close."),
        "food|praise" to listOf("You took a bite! Good job, little one. That was lovely."),
        "outside|invitation" to listOf("Shall we go outside? Let's look around. What do you see?"),
        "outside|start" to listOf("Outside we go! Let's look around. What can you see?"),
        "music|invitation" to listOf("Shall we sing? La, la, la! Let's try together."),
        "music|start" to listOf("Let's sing together! Here we go. La, la, la!"),
        "music|repeat" to listOf("One more song? Let's sing again. La, la, la!"),
        "drink|invitation" to listOf("Want a little sip? Here is your drink. Sip if you like."),
        "hands|praise" to listOf("Look at those hands! You're doing great. Well done, little one."),
        "feet|praise" to listOf("Look at those feet! You're doing great. Well done, little one.")
    )

    fun forAxes(topic: String?, interaction: String?): List<String>? =
        if (topic == null || interaction == null) null else combinations["$topic|$interaction"]
}
