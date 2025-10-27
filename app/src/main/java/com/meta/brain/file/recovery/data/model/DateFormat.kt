package com.meta.brain.file.recovery.data.model

data class DateFormat(
    val id: String,
    val displayName: String,
    val pattern: String
) {
    companion object {
        const val DEFAULT_FORMAT_ID = "mdy"

        fun getAvailableDateFormats(): List<DateFormat> = listOf(
            DateFormat("mdy", "MM/DD/YYYY", "MM/dd/yyyy"),
            DateFormat("dmy", "DD/MM/YYYY", "dd/MM/yyyy"),
            DateFormat("ymd", "YYYY-MM-DD", "yyyy-MM-dd")
        )

        fun getDateFormatById(id: String): DateFormat {
            return getAvailableDateFormats().find { it.id == id }
                ?: getAvailableDateFormats()[0] // Default to MM/DD/YYYY
        }
    }
}

