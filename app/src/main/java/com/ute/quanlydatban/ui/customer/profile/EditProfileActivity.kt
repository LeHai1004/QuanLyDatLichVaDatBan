package com.ute.quanlydatban.ui.customer.profile

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ActivityEditProfileBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.ProfileViewModel

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding

    private val viewModel by lazy {
        ViewModelProvider(this, ProfileViewModel.Factory())[ProfileViewModel::class.java]
    }

    private var selectedImageUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            binding.ivAvatar.setImageURI(uri)
            binding.ivAvatar.visibility = View.VISIBLE
            binding.tvAvatarInitial.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (FirebaseAuth.getInstance().currentUser == null) {
            finish()
            return
        }

        setupUI()
        observeViewModel()

        viewModel.loadProfile()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnChangeAvatar.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.flAvatarContainer.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.etName.doAfterTextChanged { binding.tilName.error = null }
        binding.etPhone.doAfterTextChanged { binding.tilPhone.error = null }

        binding.btnSave.setOnClickListener {
            val name = binding.etName.text?.toString().orEmpty().trim()
            val phone = binding.etPhone.text?.toString().orEmpty().trim()

            var hasError = false
            if (name.isBlank()) {
                binding.tilName.error = getString(R.string.profile_edit_error_name_empty)
                hasError = true
            }

            if (!phone.matches(Regex("^0\\d{9}$"))) {
                binding.tilPhone.error = getString(R.string.profile_edit_error_phone_invalid)
                hasError = true
            }

            if (!hasError) {
                val currentAvatarUrl = viewModel.user.value?.avatarUrl.orEmpty()
                val finalAvatarUrl = selectedImageUri?.toString() ?: currentAvatarUrl
                viewModel.updateProfile(name, phone, finalAvatarUrl)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.btnSave.isEnabled = !loading
            if (loading) {
                UiStateHelper.showLoading(binding.layoutStates.root)
            } else {
                UiStateHelper.hideAll(binding.layoutStates.root)
            }
        }

        viewModel.user.observe(this) { user ->
            if (user != null && binding.etName.text.isNullOrEmpty()) {
                binding.etName.setText(user.name)
                binding.etEmail.setText(user.email)
                binding.etPhone.setText(user.phone)

                if (selectedImageUri != null) {
                    binding.ivAvatar.setImageURI(selectedImageUri)
                    binding.ivAvatar.visibility = View.VISIBLE
                    binding.tvAvatarInitial.visibility = View.GONE
                } else if (user.avatarUrl.isNotBlank()) {
                    try {
                        binding.ivAvatar.setImageURI(Uri.parse(user.avatarUrl))
                        binding.ivAvatar.visibility = View.VISIBLE
                        binding.tvAvatarInitial.visibility = View.GONE
                    } catch (e: Exception) {
                        binding.ivAvatar.visibility = View.GONE
                        binding.tvAvatarInitial.visibility = View.VISIBLE
                        binding.tvAvatarInitial.text = user.name.firstOrNull()?.toString()?.uppercase() ?: "U"
                    }
                } else {
                    binding.ivAvatar.visibility = View.GONE
                    binding.tvAvatarInitial.visibility = View.VISIBLE
                    binding.tvAvatarInitial.text = user.name.firstOrNull()?.toString()?.uppercase() ?: "U"
                }
            }
        }

        viewModel.saveSuccess.observe(this) { success ->
            if (success) {
                SnackbarHelper.show(binding.root, getString(R.string.profile_edit_save_success))
                viewModel.clearMessages()
                finish()
            }
        }

        viewModel.errorMessage.observe(this) { err ->
            if (err != null) {
                SnackbarHelper.show(binding.root, err)
                viewModel.clearMessages()
            }
        }
    }
}
