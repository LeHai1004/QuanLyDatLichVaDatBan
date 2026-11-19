package com.ute.quanlydatban.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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

        binding.btnLogin.setOnClickListener {
            viewModel.signIn(
                binding.etLoginEmail.text?.toString().orEmpty(),
                binding.etLoginPassword.text?.toString().orEmpty()
            )
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
    }
}
