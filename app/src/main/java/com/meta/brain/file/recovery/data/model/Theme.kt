package com.meta.brain.file.recovery.data.model

data class Theme(
    val id: String,
    val displayName: String
) {
    companion object {
        const val AUTO = "auto"
        const val LIGHT = "light"
        const val DARK = "dark"

        fun getAvailableThemes(): List<Theme> = listOf(
            Theme(AUTO, "Auto"),
            Theme(LIGHT, "Light"),
            Theme(DARK, "Dark")
        )

        fun getThemeById(id: String): Theme {
            return getAvailableThemes().find { it.id == id }
                ?: Theme(AUTO, "Auto")
        }
    }
}

