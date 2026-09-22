package com.eltnegcellist.emma.ai

enum class BabyGender(
    val label: String,
    val promptInstruction: String,
) {
    UNSPECIFIED(
        label = "指定しない",
        promptInstruction = "The baby's gender is not specified. Never infer gender from the baby's name or context. Avoid gendered pronouns when possible; use the baby's name, \"the baby\", \"little one\", or singular they/them when natural.",
    ),
    GIRL(
        label = "女の子",
        promptInstruction = "The baby is a girl. When referring to the baby in the third person, use she/her/her. Never call the baby he/him/his.",
    ),
    BOY(
        label = "男の子",
        promptInstruction = "The baby is a boy. When referring to the baby in the third person, use he/him/his. Never call the baby she/her/hers.",
    );

    companion object {
        fun fromSaved(value: String?): BabyGender = entries.firstOrNull { it.name == value } ?: UNSPECIFIED
    }
}
