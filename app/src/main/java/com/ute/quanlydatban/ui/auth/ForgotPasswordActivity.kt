package com.ute.quanlydatban.ui.auth

import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ActivityForgotPasswordBinding
import com.ute.quanlydatban.viewmodel.AuthViewModel

class ForgotPasswordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityForgotPasswordBinding
    private val viewModel by lazy {
        ViewModelProvider(this, AuthViewModel.Factory)[AuthViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureAuthWindow(binding.forgotPasswordRoot, R.color.white)

        binding.etForgotEmail.doAfterTextChanged { binding.tilForgotEmail.error = null }

        binding.btnForgotBack.setOnClickListener { finish() }
        binding.tvForgotBackToLogin.setOnClickListener { finish() }

        binding.btnSendReset.setOnClickListener {
            val email = binding.etForgotEmail.text?.toString().orEmpty().trim()
            if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilForgotEmail.error = getString(R.string.auth_error_email)
                return@setOnClickListener
            }
            viewModel.sendPasswordReset(email)
        }

        viewModel.state.observe(this) { state ->
            binding.btnSendReset.isEnabled = !state.loading
            binding.btnSendReset.setText(
                if (state.loading) R.string.auth_reset_progress else R.string.auth_send_reset
            )
            if (state.messageRes != null) {
                showAuthMessage(binding.forgotPasswordRoot, state.messageRes)
                viewModel.consumeState()
            }
        }
    }
}
