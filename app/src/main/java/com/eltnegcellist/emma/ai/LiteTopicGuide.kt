package com.eltnegcellist.emma.ai

/** Offline guide for the Android Lite detector; examples are checked against the engine. */
internal object LiteTopicGuide {
    data class Topic(
        val scene: String,
        val title: String,
        val clues: String,
        val examples: List<String>,
        val note: String = "",
    )
    data class Section(val title: String, val paragraphs: List<String>)

    const val INTRO = "親の日本語から育児の話題を選び、赤ちゃんへ短い英語で返します。ここでは、Android版のLiteの判定方法と呼びかけ例を紹介します。"
    const val TOPICS_INTRO = "20種類の育児の話題に加え、飲む物を限定しない「飲む」の返答があります。下の言葉は手がかりの一部で、例は現在の判定処理で確認したものです。"

    val logic = Section(
        "どうやって話題を判定する？",
        listOf(
            "1. 音声を日本語の文字にします。判定に使うのは、実際に話した音声そのものではなく、音声認識が聞き取った文章です。",
            "2. 話題を表す言葉と動作を探します。「ミルク」「お風呂」などの言葉や、「寝る」「飲む」「読む」などの動作を手がかりにします。「寝よう／寝ましょう」の語尾や、助詞、漢字・かな表記の違いを吸収します。",
            "3. 少し崩れた言葉も、音と動作を合わせて照合します。たとえば「みるこを飲もう」は、「ミルク」に近い音と「飲む」を合わせて拾います。音が似ているだけでは、ほかの話題に取り違えないよう慎重に判定します。",
            "4. 決まった話題の英語から返答を選びます。同じ話題にも複数の返答があります。Liteは、発話を一文ずつ自由に英訳する仕組みではありません。",
            "呼びかけ例をそのまま暗記する必要はありません。ただし、あらゆる言い回しや誤認識に対応できるわけではなく、聞き取り結果が別の意味になると話題を拾えないことがあります。",
            "モデル準備後の音声認識・話題判定・返答の音声合成は、この端末内で処理します。この説明もアプリに保存されています。"
        ),
    )

    val context = Section(
        "直前の話題を引き継ぎます",
        listOf(
            "一度話題が決まると、話題を特定できない続きの発話でも最大6ターンは前の話題を使います。時間ではなく、返答した回数で数えます。別の話題を表す明確な言葉があれば、途中でも切り替わります。",
            "「ミルク飲もうね」→「おいしいね」：ミルクの話題を続けます。",
            "「ミルク飲もうね」→「飲むかい？」：ミルクの返答になります。",
            "「飲むかい？」だけ：飲む物を決めつけず、少しずつ飲む英語で返します。",
            "「ミルク飲もうね」→「お水を飲むかい？」：ミルクと決めつけない返答に切り替わります。",
            "「ミルク飲もうね」→「お風呂入ろうね」：お風呂の話題に切り替わります。",
            "ジェネラルになるのは？\n今回の発話で話題を特定できず、引き継げる話題もない場合です。たとえば、新しい会話での「かわいいね」や、育児の話題に該当しない文章には、一般的な短い英語で返します。「あー」「うー」だけの発声などは、返答を控えます。"
        ),
    )

    val tips = Section(
        "話題が拾われにくいとき",
        listOf(
            "会話に表示された日本語を見て、聞き取りが合っているか確認してください。話題をはっきりさせたいときは、「ミルクを飲もうね」「絵本を読もうね」のように、対象と動作を一緒に伝えると拾いやすくなります。",
            "「足・あし」「手・て」「指・ゆび」「声・こえ」「本・ほん」「服・ふく」「歌・うた」「お腹・おなか」は、単独でも話題として拾います。「足だね」「指を見て」「このほん」のような呼びかけにも対応します。「足りない」「手伝う」「本当」などの別の言葉とは区別します。お腹がすいたという発話は、食事の話題になります。",
            "似た言葉でも意味が違う「寝返り」「手伝う」「足りない」などを、ねんね・おてて・あんよと取り違えないようにしています。また、登録されていない話題や、複数の話題が混ざった文章では、意図と違う返答になることがあります。"
        ),
    )

    val topics = listOf(
        Topic(
            scene = "bath",
            title = "お風呂",
            clues = "お風呂・沐浴・シャワー・体を洗う",
            examples = listOf("お風呂入ろうね", "からだを洗いましょうね"),
        ),
        Topic(
            scene = "milk",
            title = "ミルク・授乳",
            clues = "ミルク・母乳・おっぱい・哺乳瓶",
            examples = listOf("ミルク飲むかい", "授乳の時間だね"),
        ),
        Topic(
            scene = "sleep",
            title = "ねんね",
            clues = "寝る・眠い・ねんね・おやすみ",
            examples = listOf("こんにちは、そろそろ寝ましょうね", "眠くなってきたね"),
        ),
        Topic(
            scene = "wake",
            title = "起きる",
            clues = "起きる・おはよう・目を覚ます",
            examples = listOf("そろそろ起きましょうね", "目を覚ましたね"),
        ),
        Topic(
            scene = "diaper",
            title = "おむつ",
            clues = "おむつ・うんち・おしっこ",
            examples = listOf("おむつを取り替えましょう", "おしっこが出ましたね"),
        ),
        Topic(
            scene = "clothes",
            title = "着替え",
            clues = "着替え・洋服・パジャマ・靴下",
            examples = listOf("きがえましょうね", "洋服を着ましょうね"),
        ),
        Topic(
            scene = "hug",
            title = "抱っこ",
            clues = "抱っこ・抱きしめる・ぎゅー",
            examples = listOf("だっこしましょうね", "ぎゅっと抱きしめよう"),
        ),
        Topic(
            scene = "hands",
            title = "おてて",
            clues = "おてて・手を握る・指をつかむ",
            examples = listOf("おててをにぎってるね", "ゆびをつかんだね"),
        ),
        Topic(
            scene = "feet",
            title = "あんよ",
            clues = "足・あし・あんよ・足を動かす・キック",
            examples = listOf("あんよがばたばたしてるね", "あしを動かしてるね", "足", "足がかわいいね"),
        ),
        Topic(
            scene = "smile",
            title = "笑顔",
            clues = "笑う・にこにこ・笑顔",
            examples = listOf("にこにこしていますね", "かわいいえがおですね"),
        ),
        Topic(
            scene = "cry",
            title = "泣く",
            clues = "泣く・ぐずぐず・えーん",
            examples = listOf("泣いていますね", "ぐずぐずしてるね"),
        ),
        Topic(
            scene = "voice",
            title = "声・おしゃべり",
            clues = "声を出す・おしゃべり・喃語",
            examples = listOf("こえを出しているね", "おしゃべりしていますね"),
        ),
        Topic(
            scene = "tummy",
            title = "げっぷ・おなか",
            clues = "げっぷ・吐き戻し・おなかいっぱい",
            examples = listOf("げっぷが出ましたね", "おなかいっぱいですね"),
        ),
        Topic(
            scene = "play",
            title = "遊ぶ",
            clues = "遊ぶ・おもちゃ・ガラガラ・ぬいぐるみ",
            examples = listOf("いっしょに遊びましょうね", "おもちゃで遊ぼうね"),
        ),
        Topic(
            scene = "outside",
            title = "お散歩・外",
            clues = "散歩・ベビーカー・公園・お外",
            examples = listOf("さんぽに行きましょうね", "こうえんに行こうね"),
        ),
        Topic(
            scene = "rain",
            title = "雨",
            clues = "雨・雨音",
            examples = listOf("あめが降っていますね", "あまおとが聞こえるね"),
        ),
        Topic(
            scene = "sun",
            title = "晴れ・お日様",
            clues = "晴れ・太陽・お日様・ぽかぽか",
            examples = listOf("はれてきましたね", "たいようが出ているね"),
        ),
        Topic(
            scene = "food",
            title = "ごはん・離乳食",
            clues = "ごはん・食べる・離乳食・おなかがすく",
            examples = listOf("ごはんを食べましょうね", "おなかがすいたね"),
        ),
        Topic(
            scene = "book",
            title = "絵本",
            clues = "絵本・読む・ページをめくる",
            examples = listOf("えほんを読みましょうね", "絵本見ようか"),
        ),
        Topic(
            scene = "music",
            title = "歌・音楽",
            clues = "歌う・音楽・リズム・踊る",
            examples = listOf("うたを歌いましょうね", "踊りましょうね"),
        ),
        Topic(
            scene = "drink",
            title = "飲む",
            clues = "飲む・飲もう・飲みたい",
            examples = listOf("そろそろ飲むかい", "飲もうか", "のむ？"),
            note = "飲む物が不明なときの例です。ミルクの話の続きならミルクの返答になります。",
        ),
    )
}
