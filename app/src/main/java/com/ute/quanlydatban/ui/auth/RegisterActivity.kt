package com.ute.quanlydatban.ui.auth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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

        binding.btnRegisterBack.setOnClickListener { finish() }
        binding.tvRegisterLogin.setOnClickListener { finish() }
        binding.btnCreateAccount.setOnClickListener {
            val role = if (binding.tgRegisterRole.checkedButtonId == R.id.btnRoleOwner) {
                ROLE_OWNER
            } else {
                ROLE_CUSTOMER
            }
            viewModel.register(
                binding.etRegisterName.text?.toString().orEmpty(),
                binding.etRegisterEmail.text?.toString().orEmpty(),
                binding.etRegisterPhone.text?.toString().orEmpty(),
                binding.etRegisterPassword.text?.toString().orEmpty(),
                role
            )
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
        private const val ROLE_CUSTOMER = "customer"
        private const val ROLE_OWNER = "owner"
    }
}
