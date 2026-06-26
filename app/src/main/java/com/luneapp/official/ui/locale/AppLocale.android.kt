package com.luneapp.official.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

object LocalAppLocale {
    private var default: Locale? = null

    val current: String
        @Composable get() = Locale.getDefault().language

    @Composable
    infix fun provides(value: String?): ProvidedValue<*> {
        val configuration = LocalConfiguration.current
        val context = LocalContext.current
        if (default == null) {
            default = Locale.getDefault()
        }
        val newLocale: Locale = when (value) {
            null -> default!!
            "zh-TW" -> Locale.TRADITIONAL_CHINESE
            "en" -> Locale.ENGLISH
            else -> Locale.forLanguageTag(value)
        }
        Locale.setDefault(newLocale)
        val newConfig = android.content.res.Configuration(configuration)
        newConfig.setLocale(newLocale)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(newConfig, context.resources.displayMetrics)
        return LocalConfiguration provides newConfig
    }
}
