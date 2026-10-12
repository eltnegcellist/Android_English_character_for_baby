package com.eltnegcellist.emma.ai

/**
 * Experimental fixed English Interaction bank. Same texts as Web PR-D.
 * Not human-approved or Kiki-auditioned. Opt in with interaction_experimental_enabled.
 * Explicit current childcare Topic has precedence over this provisional head.
 */
internal object InteractionReplyBank {
    val replies: Map<String, List<String>> = mapOf(
        "affection" to listOf(
            "You're so cute! What a sweetheart! I love you.",
            "You're so sweet. Just as you are. I love you dearly.",
            "What a sweetheart! You're adorable. Such a lovely child.",
            "You're absolutely adorable! My sweet little one. So precious.",
            "You are wonderful. Just as you are. I adore you.",
            "Hello, sweet one. You are adorable. Yes, you are!",
            "Such a cutie! That's you! My sweet little one.",
            "I love you. My darling little one. So very much."
        ),
        "praise" to listOf(
            "Well done! You did great. I'm proud of you!",
            "Good job! That's just right. You did so well!",
            "Great work! Look what you did! I'm proud of you.",
            "That's wonderful! You did well. Well done, little one!",
            "Very good! You did that yourself. How wonderful is that!",
            "Nicely done! That was lovely. Great job, little one!",
            "That's really good! What a great job! I'm so proud!",
            "Good job, little one! That's lovely. You did great!"
        ),
        "achievement" to listOf(
            "You did it! It worked! What a wonderful moment!",
            "Hooray! You did it! That was wonderful!",
            "You got it! Yes, you did! Let's celebrate together.",
            "There you go! You made it happen. We can celebrate!",
            "Yes! You made it! That's wonderful!",
            "You made it! That took some doing. What a wonderful moment!",
            "There it is! You did it! Hooray for you!",
            "It's done! You got it! What a lovely moment!"
        ),
        "effort" to listOf(
            "You're trying so hard. I see your effort. I'm cheering for you.",
            "You're really trying! You're working on it. I can see that!",
            "You're working so hard. I see you trying. Take your time.",
            "You keep on trying. You're giving it time. I see you.",
            "You're trying again. I can see that. I'm here with you.",
            "You're really trying. You haven't stopped yet. I'm cheering you on.",
            "You've been trying hard. I see that. I'm proud of you.",
            "Look at you trying! You're working so hard. I'm right beside you."
        ),
        "encouragement" to listOf(
            "You can try. I'm beside you. Go when you're ready.",
            "Give it a try! I'll stay close. You can do this!",
            "Keep trying, little one. No need to hurry. I'm cheering you on.",
            "Try again, sweetheart. That's okay. You've got time.",
            "One more try? I'm here too. You've got this!",
            "You can keep trying. Go at your pace. I'll stay nearby.",
            "Go on, little one! You can try. I believe in you.",
            "There's no hurry. Try when you're ready. I'll be close."
        ),
        "invitation" to listOf(
            "Shall we do this? Come with me. Let's try together.",
            "Want to try? Let's do it. Let's try together.",
            "What shall we do? Want to play together? Let's choose something.",
            "How about this? We can try. Let's do it together.",
            "Ready to join me? Let's do this. We'll try together.",
            "Shall we try something? Let's choose together. We can decide.",
            "Let's try something. Let's do it together. What do you think?",
            "Shall we go? I'll go with you. We can go together."
        ),
        "start" to listOf(
            "Here we go! We're beginning now. Ready, little one?",
            "Ready, set, go! Let's begin now. Off we go!",
            "It's time to start. Ready, little one? Let's do this.",
            "We're starting! Are you ready? Off we go!",
            "Let's begin! Now we start. Ready for this?",
            "Ready? We're starting now. Let's go together.",
            "Here we go! It's time to start. Ready for this?",
            "Three, two, one! It's time! Let's begin together."
        ),
        "finish" to listOf(
            "It's all done! We finished. Well done, little one.",
            "That's the end. All finished now. We did it.",
            "All done now. We're finished. Good work, little one.",
            "That's finished now. We can stop. Well done, little one.",
            "We're all done. That's the end. Well done, sweetheart.",
            "That's everything. We're all finished. Time for a break.",
            "All finished now! We can pause here. Thank you, little one.",
            "That's it! We're finished now. All done together."
        ),
        "finish_check" to listOf(
            "All done? Is that everything? Let's have a look.",
            "Are we finished? Shall we check? What do you think?",
            "Is it all done? Let's see together. Shall we check?",
            "Finished already? Let me see. Are we all done?",
            "Is that the end? Shall we see? Let's check together.",
            "Are you done? Let's have a look. Is that everything?",
            "All finished? Let's check together. Are we done now?",
            "Could that be it? Let's see. Are we done yet?"
        ),
        "care" to listOf(
            "You worked so hard. That's a lot. Rest if you like.",
            "That was a lot. You did well. Now let's rest.",
            "Good work today. You can rest. We can slow down.",
            "You did a lot. That took some effort. Shall we rest now?",
            "That took a lot. I'm right here. Let's rest together.",
            "That took effort. You worked hard. Let's take a break.",
            "You worked hard today. That was plenty. Time to rest.",
            "You did a lot. Now take a break. I'm still with you."
        ),
        "inquiry" to listOf(
            "What's the matter? I'm right here. Can you show me?",
            "What's going on? Let's figure it out. What do you need?",
            "Is something bothering you? I'm right here. What do you need?",
            "What is it? Can you show me? I'll listen closely.",
            "What's happening, little one? I'll listen closely. Tell me more.",
            "Do you need something? I'm with you. Let's find out.",
            "Are you okay? Let's take a look. What happened, sweetheart?",
            "What's up, little one? I'm listening closely. What do you need?"
        ),
        "reassurance" to listOf(
            "I'm here with you. You're not alone. I'm staying close.",
            "It's okay, sweetheart. I'm here to listen. We have time.",
            "You're not alone. I'll stay close. We can slow down.",
            "I'm here with you. I'll listen closely. We're together now.",
            "I've got you. I'll stay right here. We're together now.",
            "You're not alone. I'm here now. We'll take our time.",
            "Right here, little one. I've got you. I'll stay nearby.",
            "We're together now. I'm close by. There's no hurry."
        ),
        "repeat" to listOf(
            "One more time? Let's try again. Ready to go?",
            "Again? Let's do that. One more time!",
            "Shall we try again? Just once more. Ready to go?",
            "Let's do it again. Ready for more? We can start.",
            "Once more? Let's try together. Shall we go?",
            "One more go? Let's do it. Ready when you are.",
            "Want to try again? Let's try once more. Ready, little one?",
            "Let's have another go. We can try again. Shall we start?"
        ),
        "pause" to listOf(
            "Just a moment. I'll be right here. We can wait.",
            "Let's wait a little. No need to rush. We'll wait together.",
            "Hold on, sweetheart. Just a moment. I'm with you.",
            "We can pause now. There's no hurry. I'm staying right here.",
            "One little moment. Let's stay together. We have time.",
            "Shall we rest? We have time. I'll stay beside you.",
            "Wait a moment. Not just yet. I'll stay close.",
            "Let's take a break. We can wait. No rush."
        )
    )

    private val patterns = mapOf(
        "affection" to Regex("かわいい|可愛い|大好き|愛して|愛しい|いとしい|宝物"),
        "praise" to Regex("よくでき|えらい|偉い|上手|おりこう|すごい|素晴らし|ばっちり"),
        "achievement" to Regex("できた|やったね|成功し|やりきった|やり遂げた"),
        "effort" to Regex("頑張ってる|頑張っている|がんばってる|一生懸命|努力して"),
        "encouragement" to Regex("頑張って(?:ね|よ|ください|$)|がんばって(?:ね|よ|$)|できるよ|その調子|焦らなく|ゆっくりでいい"),
        "invitation" to Regex("しようか|やろうか|遊ぼうか|あそぼうか|一緒に|いっしょに|してみる"),
        "start" to Regex("始める|はじめる|スタート|せーの|よーい|いくよ|始めよう"),
        "finish" to Regex("終わった|終わり|おしまい|済んだ|終わりました|終わったよ"),
        "finish_check" to Regex("終わったかな|終わった[?？]|終わりかな|おしまいかな|できたかな|終わりにしようか|終わったのかな"),
        "care" to Regex("お疲れ|おつかれ|頑張った|がんばった|大変だった|頑張りました"),
        "inquiry" to Regex("どうした|なあに|何があった|大丈夫[?？]|大丈夫かな|なにかあった"),
        "reassurance" to Regex("大丈夫だよ|だいじょうぶだよ|そばにいる|ここにいる|安心して|一緒にいる"),
        "repeat" to Regex("もう一回|もう一度|またやろう|もういっかい"),
        "pause" to Regex("ちょっと待って|少し待って|待ってて|休もうか|ひと休み|一休み|休憩しよう")
    )
    private val negation = Regex("終わってない|終わらな|できなかった|できてない|まだできない|泣いてない|笑ってない|起きてない|眠くない|大丈夫じゃない|大丈夫ではない")
    private val question = Regex("(?:かな|かしら|でしょうか|[?？])\\s*$")

    fun choose(text: String, semantic: SemanticTopicPrediction?, explicitCurrentTopic: Boolean): String? {
        if (semantic == null || explicitCurrentTopic) return null
        val label = semantic.interaction ?: return null
        if (label !in replies || semantic.interactionProbability < 0.40 || semantic.interactionMargin < 0.0) return null
        val normalized = text.replace(Regex("[\\s、。!！]"), "")
        if (negation.containsMatchIn(normalized)) return null
        if (patterns[label]?.containsMatchIn(normalized) != true) return null
        if (label in setOf("finish","achievement","reassurance") && question.containsMatchIn(normalized)) return null
        if (label == "finish_check" && !question.containsMatchIn(normalized)) return null
        return label
    }
}
