package com.eltnegcellist.emma.ai

object AiCharacterName {
    const val DEFAULT = "Emma"
    const val MAX_CHARS = 24

    fun sanitizeForEditing(raw: String): String =
        raw
            .filterNot { it == '\n' || it == '\r' || it == '\t' }
            .filter { it.isLetter() || it == '\'' || it == '’' || it == '-' || it == ' ' }
            .filter { it.code < 128 }
            .replace(Regex("\\s+"), " ")
            .take(MAX_CHARS)

    // Fixed speaker identity: no per-user name replacement in recorded audio.
    fun resolve(@Suppress("UNUSED_PARAMETER") raw: String?): String = DEFAULT

    fun introduction(@Suppress("UNUSED_PARAMETER") name: String): String = "Hi, I'm Emma."

    fun stripLeadingSpeakerLabel(text: String, name: String): String {
        val resolved = resolve(name)
        val label = Regex(
            "^\\s*(?:${Regex.escape(resolved)}|AI)\\s*[:：\\-–—]\\s*",
            RegexOption.IGNORE_CASE,
        )
        return text.replaceFirst(label, "").trimStart()
    }
}
