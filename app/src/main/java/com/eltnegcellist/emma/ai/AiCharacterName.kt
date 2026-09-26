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

    fun resolve(raw: String?): String =
        sanitizeForEditing(raw.orEmpty()).trim().ifBlank { DEFAULT }

    fun introduction(name: String): String = "Hi, I'm ${resolve(name)}."
}
