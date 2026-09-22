package com.eltnegcellist.emma.ai

/**
 * Fixed baby-directed response catalog used by standard Emma.
 *
 * The catalog is stored locally so standard mode does not require a language model at runtime.
 */
internal object CanonicalEmmaPhrases {
    private val byScene: Map<String, List<String>> = mapOf(
        "bath" to listOf(
            "Bath time! Water fun. Let's splash now. Here we go!",
            "Get in the water. So much water. Play time starts.",
            "Time to splash. Water goes everywhere. Yay, bath!",
            "Here we go, sweetie. Big water fun. Splash, splash!",
            "Bath time is here. Let's get wet. So much water.",
        ),
        "milk" to listOf(
            "Milk time now. Drink the milk. Sip, sip, sip.",
            "Here we go. Drink this milk. Good sips.",
            "Milk. Drink it. Sip it now. Yes, milk.",
            "Milk time. Take a drink. Sip slow.",
            "Drink the milk. Here we go. Sip now.",
        ),
        "sleep" to listOf(
            "Shhh. Quiet now. Sleep time. Rest time. Sleepy time.",
            "Night-night time. Sleep now. Quiet time is here. Soft time for you.",
            "Sleepy time. Close your eyes. Rest now, little one. Shh, shh.",
            "Quiet time. Sleep time. Rest now, rest now. Sleepy time.",
            "Sleepy. Time for rest. Shh, shh. Quiet now. Good night.",
        ),
        "wake" to listOf(
            "Good morning! Hello there. Time to wake up now. Here we are.",
            "Hello! We are awake. Good morning, sweet one. Time to wake up.",
            "Wake up! Hello, little one. Morning time is here. Let's wake up.",
            "Good morning! Hello. Ready to wake up now. Here we are.",
            "Here we are awake. Hello, good morning! Time for the day.",
        ),
        "diaper" to listOf(
            "Diaper time now. Change time here. Let's go, go!",
            "Time for change. Here we go now. See the change.",
            "Diaper change start. Change time is here. Good change now.",
            "We change now. Diaper time is fun. Let's do it.",
            "Change time starts. Diaper time is here. We change.",
        ),
        "clothes" to listOf(
            "Here we go. Clothes on. Look at you. Putting them on. Yes, dressing!",
            "Time to dress. Socks are here. Put them on. Let's go. Simple dressing.",
            "Clothes are on. Yes, we are dressed. Put the socks on now. All done dressing.",
            "Here we go. Dressing is easy. Socks are on. Good job. We are dressed now.",
            "Put the clothes on. This is fun. Legs feel nice. Dressing is here.",
        ),
        "hug" to listOf(
            "Hold me close. This is nice. Snuggle is fun. So good. Here we go.",
            "Hug me now. So soft. Hold me close. Yes. Snuggle time is fun.",
            "This hold is sweet. So good. We cuddle here. Stay close. Hug hug hug.",
            "Hold me now. Big hug. This snuggle is fun. Yes. We stay close.",
            "Hug time. Feel this hold. Snuggle is nice. So sweet. Hold me tight.",
        ),
        "hands" to listOf(
            "Your hands are here. Feel your little fingers. They move.",
            "Little hands look good. Touch your fingers now. They are so soft.",
            "See your hands. Feel your little fingers. They are there.",
            "Your little hands. They are so small. Look at them now.",
            "See those little hands. They are so neat. What hands.",
        ),
        "feet" to listOf(
            "Wiggle your feet. Kick them now. Move them fast.",
            "Look at those feet. They are so busy. Wiggle, wiggle, wiggle.",
            "Little feet move. Tap, tap, tap. Good kicking!",
            "Your toes are wiggling. Move them around. Such fun feet.",
            "Kick them up high. Move your legs. Up and down.",
        ),
        "smile" to listOf(
            "See that big smile. That smile is so bright. Smile again now.",
            "Oh, a big smile! I see your smile. Smile with me.",
            "That is a nice smile. Show me your smile. Smile, smile, smile.",
            "Look at your smile. It is a sweet smile. Smile now.",
            "See the smile. It is a good smile. Smile more.",
        ),
        "cry" to listOf(
            "I hear you now. That sound is there. Listen to it.",
            "Hear that sound. Soft sound. Listen to it.",
            "Listen to me. Hear my voice. It is here.",
            "Sound is here. Listen to it. It is loud.",
            "Your sound is here. I hear it. What a sound.",
        ),
        "voice" to listOf(
            "I hear that sound. Oh, a sound. Listen to that noise. It is a sound.",
            "That sound is there. I hear it. Listen to it now. What a sound.",
            "Ah. That sound came out. Hear that sound. It is a sound.",
            "You made a sound. I hear it. Listen to that noise. That is a sound.",
            "I hear the sound. It is right here. Listen to that sound. A sound is here.",
        ),
        "tummy" to listOf(
            "See your tummy. It is so round. Let us look now.",
            "After the food. Your tummy is there. Time to be still.",
            "Your tummy is there. It is warm. We wait now.",
            "Gentle tummy time. Slow breaths now. See your soft tummy.",
            "Look at your tummy. Little tummy goes slow. Sweet time for you.",
        ),
        "play" to listOf(
            "Let's play now. Play time is here. We can play fun.",
            "Time to play. Let's go play. Have fun now.",
            "Look at this. Play this thing. Let's go play!",
            "Play, play, play. Fun time starts. Here we go!",
            "Time to move. Let's play now. So much fun.",
        ),
        "outside" to listOf(
            "Let's go out now. We walk so slow. Look around here.",
            "Walk, walk, walk. Time to move. Fun walk time.",
            "Let's go outside. Look! Look now. See things.",
            "Listen to sounds. Hear them now. Good walking.",
            "We go out now. Walking is fun. So much to see.",
        ),
        "rain" to listOf(
            "Rain is falling. Listen now. Pitter-patter sound. So nice. Hear the drops fall.",
            "Listen to the rain. It goes soft. Pitter-patter sounds good. Rain is here.",
            "Rain is falling. Hear the drops. Drip, drop, drip. So wet now. Listen close to the sound.",
            "Pitter-patter sound. Rain is falling. Listen to the drops. It sounds nice.",
            "Rain is falling. Hear the sound. Drip, drip, drip. Rain is falling. Yes.",
        ),
        "sun" to listOf(
            "The sun is up. Bright day! Sunshine is here. So light!",
            "Look at the bright sun. Wow! So much light today.",
            "The sun is up. So bright. Shiny day.",
            "Sun is shining. So much light. Pretty sun.",
            "Shine, shine, sun. So bright and sunny. Good light.",
        ),
        "food" to listOf(
            "Meal time now. Time to eat. Here we go.",
            "Time to eat. Let's try this. Food time.",
            "Here we go. Eat now. This is food.",
            "Meal time fun. Time to eat. Let's go.",
            "Food time starts. Little bite. Good time.",
        ),
        "book" to listOf(
            "Book time! Let's read. Look at this page. Turn the page now.",
            "Read, read, read. Look at the book. Page time!",
            "Turn the page. Turn the page. We read now. So good.",
            "Look at this page. Read the story. Yes, read time.",
            "Read, read, read. Look here. Look there.",
        ),
        "music" to listOf(
            "Music time now. Listen to the sound. So nice. Hear the beat. Feel the sound.",
            "Let's listen. Music time is fun. Good sound. Rhythm is here. Listen to the music.",
            "Listen to the song. Music time! What a sound. Hear the rhythm. Play the music.",
            "Music is playing. Listen to it. Good sound. Feel the music now. Hear the sound.",
            "Listen to the rhythm. It goes fast. Music is fun. Sound, sound, sound. Music time.",
        ),
    )

    fun forScene(sceneId: String): List<String> =
        requireNotNull(byScene[sceneId]) { "Unknown canonical Emma scene: $sceneId" }

    fun all(): Map<String, List<String>> = byScene
}
