package com.luneapp.official.domain.settings

/**
 * Controls the display mode of the application.
 */
enum class AppDarkMode(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        /**
         * Parsed from a stored string value; falls back to [SYSTEM] if unknown.
         */
        fun fromValue(value: String?): AppDarkMode {
            return entries.find { it.value == value } ?: SYSTEM
        }
    }
}
