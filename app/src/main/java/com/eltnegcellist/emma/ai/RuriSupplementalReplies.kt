package com.eltnegcellist.emma.ai

/** Extra Topic × Interaction replies synchronized with Web supplemental-replies.js.
 * The Ruri model/classification thresholds are unchanged.
 */
internal object RuriSupplementalReplies {
    val combinations: Map<String, List<String>> = mapOf(
        "milk|affection" to listOf(
            "Milk time, sweetheart! You're so lovely. I love you.",
            "Milk time with you. My sweet little one. I love you.",
            "Your milk is here. You're so precious. I'm right beside you."
        ),
        "milk|start" to listOf(
            "Here comes your milk. Let's get comfy. Ready for a sip?",
            "Milk time starts now. You're all cozy. Here we go!",
            "Time for your milk! Let's sit together. Here it comes."
        ),
        "milk|inquiry" to listOf(
            "Want some more milk? Are you still hungry? Let's check together.",
            "How is your milk? Is it yummy? Smack those little lips!",
            "Need another little sip? Shall we try? Take your time."
        ),
        "milk|repeat" to listOf(
            "Want another sip? We can try. No need to hurry.",
            "More milk, little one? Only if you like. I'm right here.",
            "Want more milk? Just a little? Take your time."
        ),
        "drink|affection" to listOf(
            "A drink with you. My sweet little one. I love you.",
            "Here is your drink. You're so precious. I love you.",
            "A little drink together. You're my sweetheart. I'm right here."
        ),
        "drink|inquiry" to listOf(
            "Would you like water? Are you thirsty? Let's find out.",
            "Another sip for you? Do you want more? It's up to you.",
            "How is your drink? Is that enough? I'm here with you."
        ),
        "diaper|affection" to listOf(
            "Fresh and cozy now. My sweet little one. I love you.",
            "A gentle diaper change. You're so precious. I'm right here.",
            "Your little diaper, sweetheart. I'm here with you. I love you."
        ),
        "diaper|praise" to listOf(
            "Diaper change, all done! We did it! Well done, sweetheart.",
            "Look at you! All clean now. You did great!",
            "A fresh diaper now. We did that. Good job, little one!"
        ),
        "diaper|start" to listOf(
            "Let's change your diaper. We'll be gentle. Here we go!",
            "Time for a change. I'll be gentle. Ready, little one?",
            "A fresh diaper now. Let's get ready. I'm right here."
        ),
        "sleep|affection" to listOf(
            "Sleepy little sweetheart. I love you. I'm right here.",
            "Time for cozy rest. You're so precious. I love you.",
            "Sweet dreams, little one. You're so loved. I'm beside you."
        ),
        "sleep|care" to listOf(
            "You look sleepy. Let's rest now. I'll stay close.",
            "Rest your little eyes. We have time. I'm right here.",
            "Time for a break. Let's get cozy. I'll stay nearby."
        ),
        "wake|praise" to listOf(
            "You're awake now! Look at you! What a lovely day.",
            "You opened your eyes! Hello, little one. What a lovely sight.",
            "Here you are! You're awake now. So good to see!"
        ),
        "wake|reassurance" to listOf(
            "You're awake now. I'm right here. You're safe with me.",
            "Hello, little one. I'll stay close. We're together now.",
            "You woke up. Take your time. I'm beside you."
        ),
        "bath|affection" to listOf(
            "Warm water together. My sweet little one. I love you.",
            "Bath time, sweetheart! You're so lovely. I'm right here.",
            "Little splashes together. You're so precious. I love you."
        ),
        "bath|praise" to listOf(
            "You're splashing water! That's so lovely. Well done, sweetheart.",
            "Look at you! What lovely splashes! You're doing great.",
            "You're enjoying bath time! That's so lovely. Well done, sweetheart."
        ),
        "food|affection" to listOf(
            "Snack time with you. My sweet little one. I love you.",
            "Little bites together. You're so precious. I'm right here.",
            "A meal together. My darling little one. I love you."
        ),
        "food|finish" to listOf(
            "Mealtime is all done. Let's wipe up. Time to rest.",
            "You've finished your food. Well done, sweetheart. Let's rest now.",
            "Those bites are finished. We are done. Let's clean up."
        ),
        "book|affection" to listOf(
            "Story time with you. My sweet little one. I love you.",
            "A book together. You're so precious. Let's turn pages.",
            "This story is ours. I love you. Stay close, sweetheart."
        ),
        "book|finish_check" to listOf(
            "Is our story done? One more page? Let's check together.",
            "Last page already? Are we finished? Let's look together.",
            "Is that the end? Shall we check? One last look."
        ),
        "music|affection" to listOf(
            "A song for you. My sweet little one. I love you.",
            "Music time together. You're so precious. I'm right here.",
            "This song is yours. I love you. Let's listen together."
        ),
        "music|finish_check" to listOf(
            "Is our song done? Shall we listen? One more note?",
            "Song finished already? Let's check together. Is that all?",
            "Is that the end? One more sound? Let's listen together."
        ),
        "play|affection" to listOf(
            "Playtime with you. You're so precious. I love you.",
            "My little playmate. This is lovely. I'm right here.",
            "We play together. You're my sweetheart. What lovely fun!"
        ),
        "play|finish_check" to listOf(
            "All done playing? One more game? Let's play or rest.",
            "Is playtime over? Shall we stop? We can choose.",
            "All finished playing? One last turn? What do you think?"
        ),
        "clothes|affection" to listOf(
            "Your cozy little clothes. You're so precious. I love you.",
            "All snug and warm. My sweet little one. I'm right here.",
            "A little outfit today. You're so lovely. I love you."
        ),
    )
}
