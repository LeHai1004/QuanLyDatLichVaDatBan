package com.ute.quanlydatban.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ActivityLoginBinding
import com.ute.quanlydatban.viewmodel.AuthViewModel

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val viewModel by lazy {
        ViewModelProvider(this, AuthViewModel.Factory)[AuthViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureAuthWindow(binding.loginRoot)

        binding.etLoginEmail.doAfterTextChanged { binding.tilLoginEmail.error = null }
        binding.etLoginPassword.doAfterTextChanged { binding.tilLoginPassword.error = null }

        binding.btnLogin.setOnClickListener {
            val email = binding.etLoginEmail.text?.toString().orEmpty().trim()
            val password = binding.etLoginPassword.text?.toString().orEmpty()

            var hasError = false
            if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilLoginEmail.error = getString(R.string.auth_error_email)
                hasError = true
            }
            if (password.isBlank()) {
                binding.tilLoginPassword.error = getString(R.string.auth_error_password_required)
                hasError = true
            } else if (password.length < MIN_PASSWORD_LENGTH) {
                binding.tilLoginPassword.error = getString(R.string.auth_error_weak_password)
                hasError = true
            }

            if (!hasError) {
                viewModel.signIn(email, password)
            }
        }

        binding.tvLoginRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.tvLoginForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        intent.getIntExtra(EXTRA_AUTH_MESSAGE, 0)
            .takeIf { it != 0 }
            ?.let { showAuthMessage(binding.loginRoot, it) }

        viewModel.state.observe(this) { state ->
            binding.btnLogin.isEnabled = !state.loading
            binding.btnLogin.setText(
                if (state.loading) R.string.auth_login_progress else R.string.auth_login
            )
            when {
                state.destinationRole != null -> navigateToHome(state.destinationRole)
                state.messageRes != null -> {
                    showAuthMessage(binding.loginRoot, state.messageRes)
                    viewModel.consumeState()
                }
            }
        }
    }

    companion object {
        const val EXTRA_AUTH_MESSAGE = "auth_message"
        private const val MIN_PASSWORD_LENGTH = 6
    }
}
