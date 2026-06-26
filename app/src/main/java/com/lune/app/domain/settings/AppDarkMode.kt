package com.lune.app.domain.settings

/**
 * 應用程式的顯示模式設定。
 */
enum class AppDarkMode(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        /**
         * 從字串值取得列舉，若找不到或無效則回傳 [SYSTEM]。
         */
        fun fromValue(value: String?): AppDarkMode {
            return entries.find { it.value == value } ?: SYSTEM
        }
    }
}
