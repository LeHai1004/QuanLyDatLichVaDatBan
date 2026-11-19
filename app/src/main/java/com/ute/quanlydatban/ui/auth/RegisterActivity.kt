package com.ute.quanlydatban.ui.auth

import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ActivityRegisterBinding
import com.ute.quanlydatban.viewmodel.AuthViewModel

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private val viewModel by lazy {
        ViewModelProvider(this, AuthViewModel.Factory)[AuthViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureAuthWindow(binding.registerRoot)

        binding.etRegisterName.doAfterTextChanged { binding.tilRegisterName.error = null }
        binding.etRegisterEmail.doAfterTextChanged { binding.tilRegisterEmail.error = null }
        binding.etRegisterPhone.doAfterTextChanged { binding.tilRegisterPhone.error = null }
        binding.etRegisterPassword.doAfterTextChanged { binding.tilRegisterPassword.error = null }

        binding.btnRegisterBack.setOnClickListener { finish() }
        binding.tvRegisterLogin.setOnClickListener { finish() }

        binding.btnCreateAccount.setOnClickListener {
            val name = binding.etRegisterName.text?.toString().orEmpty().trim()
            val email = binding.etRegisterEmail.text?.toString().orEmpty().trim()
            val phone = binding.etRegisterPhone.text?.toString().orEmpty().trim()
            val password = binding.etRegisterPassword.text?.toString().orEmpty()

            var hasError = false
            if (name.isBlank()) {
                binding.tilRegisterName.error = getString(R.string.auth_error_name)
                hasError = true
            }
            if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilRegisterEmail.error = getString(R.string.auth_error_email)
                hasError = true
            }
            if (phone.isBlank()) {
                binding.tilRegisterPhone.error = getString(R.string.auth_error_phone)
                hasError = true
            }
            if (password.isBlank() || password.length < MIN_PASSWORD_LENGTH) {
                binding.tilRegisterPassword.error = getString(R.string.auth_error_weak_password)
                hasError = true
            }

            if (!hasError) {
                val role = if (binding.tgRegisterRole.checkedButtonId == R.id.btnRoleOwner) {
                    ROLE_OWNER
                } else {
                    ROLE_CUSTOMER
                }
                viewModel.register(name, email, phone, password, role)
            }
        }

        viewModel.state.observe(this) { state ->
            binding.btnCreateAccount.isEnabled = !state.loading
            binding.btnCreateAccount.setText(
                if (state.loading) R.string.auth_create_progress else R.string.auth_create_account
            )
            when {
                state.destinationRole != null -> navigateToHome(state.destinationRole)
                state.messageRes != null -> {
                    showAuthMessage(binding.registerRoot, state.messageRes)
                    viewModel.consumeState()
                }
            }
        }
    }

    companion object {
        const val ROLE_CUSTOMER = "customer"
        const val ROLE_OWNER = "owner"
        private const val MIN_PASSWORD_LENGTH = 6
    }
}
