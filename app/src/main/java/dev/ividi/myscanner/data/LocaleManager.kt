package dev.ividi.myscanner.data

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Applies the user's chosen in-app language via the per-app language APIs, independent
 * of the device's system locale.
 */
object LocaleManager {
    fun apply(language: AppLanguage) {
        val locales = LocaleListCompat.forLanguageTags(language.tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
