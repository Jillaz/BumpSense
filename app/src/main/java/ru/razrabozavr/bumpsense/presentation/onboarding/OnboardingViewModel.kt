package ru.razrabozavr.bumpsense.presentation.onboarding

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("app_onboarding_prefs", Context.MODE_PRIVATE)

    private val _showTermsDialog = MutableStateFlow(!prefs.getBoolean(KEY_TERMS_ACCEPTED, false))
    val showTermsDialog: StateFlow<Boolean> = _showTermsDialog.asStateFlow()

    companion object {
        private const val KEY_TERMS_ACCEPTED = "is_terms_accepted"
    }

    /**
     * Пользователь принял условия
     */
    fun acceptTerms() {
        viewModelScope.launch {
            prefs.edit().putBoolean(KEY_TERMS_ACCEPTED, true).apply()
            _showTermsDialog.value = false
        }
    }

    /**
     * Пользователь отклонил условия (приложение должно быть закрыто)
     */
    fun declineTerms() {
        _showTermsDialog.value = false // Скрываем диалог, Activity вызовет finish()
    }
}