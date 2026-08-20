package com.autopanel.core.data.preferences

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalAppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var languageTag: String
        get() = preferences.getString(KEY_LANGUAGE, LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM
        set(value) {
            require(value in SUPPORTED_LANGUAGES) { "Unsupported language: $value" }
            preferences.edit().putString(KEY_LANGUAGE, value).commit()
        }

    var biometricEnabled: Boolean
        get() = preferences.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()
        }

    companion object {
        const val LANGUAGE_SYSTEM = "system"
        const val LANGUAGE_CHINESE = "zh-CN"
        const val LANGUAGE_ENGLISH = "en"

        val SUPPORTED_LANGUAGES = setOf(
            LANGUAGE_SYSTEM,
            LANGUAGE_CHINESE,
            LANGUAGE_ENGLISH
        )

        private const val PREFERENCES_NAME = "local_app_preferences"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

        fun localizedContext(base: Context): Context {
            val preferences = base.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val languageTag = preferences.getString(KEY_LANGUAGE, LANGUAGE_SYSTEM)
                ?: LANGUAGE_SYSTEM
            if (languageTag == LANGUAGE_SYSTEM) return base

            val locale = Locale.forLanguageTag(languageTag)
            val configuration = Configuration(base.resources.configuration).apply {
                setLocales(LocaleList(locale))
            }
            return base.createConfigurationContext(configuration)
        }
    }
}
