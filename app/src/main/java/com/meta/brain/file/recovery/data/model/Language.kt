package com.meta.brain.file.recovery.data.model

data class Language(
    val code: String,
    val displayName: String
) {
    companion object {
        fun getAvailableLanguages(): List<Language> = listOf(
            Language("en", "English"),
            Language("in", "Indonesia"),
            Language("pt", "Portuguese"),
            Language("es", "Spanish"),
            Language("hi", "Hindi"),
            Language("tr", "Turkish"),
            Language("fr", "French"),
            Language("vi", "Vietnamese"),
            Language("ru", "Russian")
        )

        fun getLanguageByCode(code: String): Language {
            return getAvailableLanguages().find { it.code == code }
                ?: Language("en", "English")
        }
    }
}
