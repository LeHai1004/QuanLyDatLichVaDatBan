package com.ute.quanlydatban.viewmodel

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.repository.AuthRepository

data class AuthViewState(
    val loading: Boolean = false,
    val messageRes: Int? = null,
    val destinationRole: String? = null,
    val sessionChecked: Boolean = false
)

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableLiveData(AuthViewState())
    val state: LiveData<AuthViewState> = _state

    fun signIn(email: String, password: String) {
        val normalizedEmail = email.trim()
        if (!isEmailValid(normalizedEmail)) {
            showMessage(R.string.auth_error_email)
            return
        }
        if (password.isBlank()) {
            showMessage(R.string.auth_error_password_required)
            return
        }

        _state.value = AuthViewState(loading = true)
        repository.signIn(normalizedEmail, password, ::handleResult)
    }

    fun register(name: String, email: String, phone: String, password: String, role: String) {
        val normalizedEmail = email.trim()
        if (name.isBlank()) {
            showMessage(R.string.auth_error_name)
            return
        }
        if (!isEmailValid(normalizedEmail)) {
            showMessage(R.string.auth_error_email)
            return
        }
        if (phone.isBlank()) {
            showMessage(R.string.auth_error_phone)
            return
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            showMessage(R.string.auth_error_weak_password)
            return
        }
        if (role != ROLE_CUSTOMER && role != ROLE_OWNER) {
            showMessage(R.string.auth_error_role)
            return
        }

        _state.value = AuthViewState(loading = true)
        repository.register(name.trim(), normalizedEmail, phone.trim(), password, role, ::handleResult)
    }

    fun sendPasswordReset(email: String) {
        val normalizedEmail = email.trim()
        if (!isEmailValid(normalizedEmail)) {
            showMessage(R.string.auth_error_email)
            return
        }

        _state.value = AuthViewState(loading = true)
        repository.sendPasswordReset(normalizedEmail) { result ->
            _state.value = result.fold(
                onSuccess = { AuthViewState(messageRes = R.string.auth_reset_sent) },
                onFailure = { AuthViewState(messageRes = it.toAuthMessage()) }
            )
        }
    }

    fun restoreSession() {
        _state.value = AuthViewState(loading = true)
        repository.restoreSession { result ->
            _state.value = AuthViewState(
                destinationRole = result.getOrNull(),
                sessionChecked = true,
                messageRes = result.exceptionOrNull()?.toAuthMessage()
            )
        }
    }

    fun consumeState() {
        _state.value = AuthViewState()
    }

    private fun handleResult(result: Result<String>) {
        _state.value = result.fold(
            onSuccess = { AuthViewState(destinationRole = it) },
            onFailure = { AuthViewState(messageRes = it.toAuthMessage()) }
        )
    }

    private fun showMessage(messageRes: Int) {
        _state.value = AuthViewState(messageRes = messageRes)
    }

    private fun isEmailValid(email: String): Boolean =
        email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun Throwable.toAuthMessage(): Int = when (this) {
        is FirebaseAuthWeakPasswordException -> R.string.auth_error_weak_password
        is FirebaseAuthUserCollisionException -> R.string.auth_error_account_exists
        is FirebaseAuthInvalidCredentialsException,
        is FirebaseAuthInvalidUserException -> R.string.auth_error_credentials
        is FirebaseNetworkException -> R.string.auth_error_network
        else -> R.string.auth_error_generic
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 6
        private const val ROLE_CUSTOMER = "customer"
        private const val ROLE_OWNER = "owner"

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (!modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
                return checkNotNull(modelClass.cast(AuthViewModel(AuthRepository())))
            }
        }
    }
}
