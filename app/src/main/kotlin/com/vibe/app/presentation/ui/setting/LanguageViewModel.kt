package com.vibe.app.presentation.ui.setting

import androidx.lifecycle.ViewModel
import com.vibe.app.data.preferences.LanguageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val languageManager: LanguageManager
) : ViewModel() {

    /**
     * The currently applied language.
     */
    val language: StateFlow<String> =
        languageManager.language

    /**
     * The language currently selected in the UI.
     *
     * This is intentionally separate from the applied language,
     * so selecting Arabic/English does not change the application
     * until the user presses Confirm/Save.
     */
    private val _selectedLanguage =
        MutableStateFlow(
            languageManager.getCurrentLanguage()
        )

    val selectedLanguage: StateFlow<String> =
        _selectedLanguage.asStateFlow()

    /**
     * Temporarily select a language in the UI.
     *
     * The application language is NOT changed here.
     */
    fun selectLanguage(
        language: String
    ) {
        _selectedLanguage.value =
            normalizeLanguage(language)
    }

    /**
     * Apply the currently selected language.
     *
     * This is called when the user presses Confirm/Save.
     */
    fun confirmLanguage() {
        languageManager.setLanguage(
            _selectedLanguage.value
        )
    }

    /**
     * Change and immediately apply a language.
     *
     * Kept for existing callers that expect immediate
     * language changes.
     */
    fun changeLanguage(
        language: String
    ) {
        val normalizedLanguage =
            normalizeLanguage(language)

        _selectedLanguage.value =
            normalizedLanguage

        languageManager.setLanguage(
            normalizedLanguage
        )
    }

    /**
     * Existing API kept for compatibility with the
     * current settings UI.
     *
     * This method immediately applies the language.
     */
    fun setLanguage(
        language: String
    ) {
        changeLanguage(language)
    }

    /**
     * Returns true when the user has explicitly selected
     * a language before.
     */
    fun isLanguageSelected(): Boolean {
        return languageManager.isLanguageSelected()
    }

    private fun normalizeLanguage(
        language: String
    ): String {
        return when (
            language.lowercase()
        ) {
            "ar" -> "ar"
            "en" -> "en"
            else -> "en"
        }
    }
}
