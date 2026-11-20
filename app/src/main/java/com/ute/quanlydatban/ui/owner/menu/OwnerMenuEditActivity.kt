package com.ute.quanlydatban.ui.owner.menu

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.MenuItem
import com.ute.quanlydatban.databinding.ActivityOwnerMenuEditBinding
import com.ute.quanlydatban.viewmodel.MenuValidationResult
import com.ute.quanlydatban.viewmodel.OwnerMenuViewModel
import java.net.URL
import java.util.concurrent.Executors

class OwnerMenuEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RESTAURANT_ID = "extra_restaurant_id"
        const val EXTRA_MENU_ITEM_ID = "extra_menu_item_id"
        const val EXTRA_MENU_ITEM_NAME = "extra_menu_item_name"
        const val EXTRA_MENU_ITEM_PRICE = "extra_menu_item_price"
        const val EXTRA_MENU_ITEM_CATEGORY = "extra_menu_item_category"
        const val EXTRA_MENU_ITEM_IMAGE_URL = "extra_menu_item_image_url"
        const val EXTRA_MENU_ITEM_AVAILABLE = "extra_menu_item_available"
    }

    private lateinit var binding: ActivityOwnerMenuEditBinding
    private val viewModel by lazy {
        ViewModelProvider(this)[OwnerMenuViewModel::class.java]
    }

    private var restaurantId: String = ""
    private var menuItemId: String = ""
    private var originalImageUrl: String = ""
    private var selectedImageUri: Uri? = null

    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            binding.ivDishImage.setImageURI(uri)
            binding.llSelectImagePrompt.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOwnerMenuEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        extractExtras()
        setupToolbar()
        setupCategoryDropdown()
        setupForm()
        setupListeners()
    }

    private fun extractExtras() {
        restaurantId = intent.getStringExtra(EXTRA_RESTAURANT_ID).orEmpty()
        menuItemId = intent.getStringExtra(EXTRA_MENU_ITEM_ID).orEmpty()
        originalImageUrl = intent.getStringExtra(EXTRA_MENU_ITEM_IMAGE_URL).orEmpty()
    }

    private fun setupToolbar() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        if (menuItemId.isNotBlank()) {
            binding.tvToolbarTitle.text = getString(R.string.owner_menu_edit_title_edit)
            binding.ivDeleteDish.visibility = View.VISIBLE
            binding.ivDeleteDish.setOnClickListener {
                confirmDeleteDish()
            }
        } else {
            binding.tvToolbarTitle.text = getString(R.string.owner_menu_edit_title_add)
            binding.ivDeleteDish.visibility = View.GONE
        }
    }

    private fun setupCategoryDropdown() {
        val defaultCategories = listOf(
            "Khai vị",
            "Món chính",
            "Món lẩu",
            "Tráng miệng",
            "Đồ uống",
            "Món đặc biệt"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, defaultCategories)
        binding.actCategory.setAdapter(adapter)
    }

    private fun setupForm() {
        if (menuItemId.isNotBlank()) {
            binding.etDishName.setText(intent.getStringExtra(EXTRA_MENU_ITEM_NAME).orEmpty())
            val price = intent.getLongExtra(EXTRA_MENU_ITEM_PRICE, 0L)
            if (price > 0) {
                binding.etDishPrice.setText(price.toString())
            }
            binding.actCategory.setText(intent.getStringExtra(EXTRA_MENU_ITEM_CATEGORY).orEmpty(), false)
            val available = intent.getBooleanExtra(EXTRA_MENU_ITEM_AVAILABLE, true)
            binding.swAvailable.isChecked = available
            updateSwitchHint(available)

            if (originalImageUrl.isNotBlank() && originalImageUrl.startsWith("http")) {
                binding.llSelectImagePrompt.visibility = View.GONE
                loadRemoteImage(originalImageUrl)
            }
        } else {
            binding.swAvailable.isChecked = true
            updateSwitchHint(true)
        }
    }

    private fun setupListeners() {
        binding.cardDishImage.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        binding.swAvailable.setOnCheckedChangeListener { _, isChecked ->
            updateSwitchHint(isChecked)
        }

        binding.etDishName.doAfterTextChanged {
            binding.tilDishName.error = null
        }

        binding.etDishPrice.doAfterTextChanged {
            binding.tilDishPrice.error = null
        }

        binding.actCategory.doAfterTextChanged {
            binding.tilDishCategory.error = null
        }

        binding.btnSaveDish.setOnClickListener {
            performSaveDish()
        }
    }

    private fun updateSwitchHint(isAvailable: Boolean) {
        if (isAvailable) {
            binding.tvSwitchHint.text = getString(R.string.owner_menu_switch_available)
            binding.tvSwitchHint.setTextColor(ContextCompat.getColor(this, R.color.owner_menu_status_available))
        } else {
            binding.tvSwitchHint.text = getString(R.string.owner_menu_switch_unavailable)
            binding.tvSwitchHint.setTextColor(ContextCompat.getColor(this, R.color.owner_menu_status_unavailable))
        }
    }

    private fun performSaveDish() {
        val name = binding.etDishName.text?.toString().orEmpty()
        val priceStr = binding.etDishPrice.text?.toString().orEmpty()
        val category = binding.actCategory.text?.toString().orEmpty()
        val isAvailable = binding.swAvailable.isChecked

        val validation = viewModel.validateInput(name, priceStr, category)
        if (validation is MenuValidationResult.Invalid) {
            validation.nameError?.let { binding.tilDishName.error = it }
            validation.priceError?.let { binding.tilDishPrice.error = it }
            validation.categoryError?.let { binding.tilDishCategory.error = it }
            return
        }

        val priceLong = priceStr.trim().toLong()
        val currentDish = MenuItem(
            id = menuItemId,
            restaurantId = restaurantId,
            name = name.trim(),
            price = priceLong,
            category = category.trim(),
            imageUrl = originalImageUrl,
            available = isAvailable
        )

        setLoading(true)

        viewModel.saveDishWithImage(
            currentDish = currentDish,
            newName = name,
            newPrice = priceLong,
            newCategory = category,
            newAvailable = isAvailable,
            imageUri = selectedImageUri,
            onSuccess = {
                setLoading(false)
                finish()
            },
            onError = { errMsg ->
                setLoading(false)
                Snackbar.make(binding.root, errMsg, Snackbar.LENGTH_LONG)
                    .setAction(R.string.state_retry) {
                        performSaveDish()
                    }
                    .show()
            }
        )
    }

    private fun confirmDeleteDish() {
        val dishName = binding.etDishName.text?.toString().orEmpty().ifBlank { "món này" }
        AlertDialog.Builder(this)
            .setTitle(R.string.owner_menu_delete_dialog_title)
            .setMessage(getString(R.string.owner_menu_delete_dialog_message, dishName))
            .setPositiveButton(R.string.owner_menu_delete_dialog_confirm) { _, _ ->
                executeDeleteDish()
            }
            .setNegativeButton(R.string.owner_menu_delete_dialog_cancel, null)
            .show()
    }

    private fun executeDeleteDish() {
        if (menuItemId.isBlank()) return
        setLoading(true)
        viewModel.deleteDish(menuItemId) { result ->
            setLoading(false)
            result.onSuccess {
                finish()
            }.onFailure { error ->
                Snackbar.make(
                    binding.root,
                    error.message ?: getString(R.string.owner_menu_delete_failure),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.btnSaveDish.isEnabled = !isLoading
        binding.btnSaveDish.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
        binding.pbSaving.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun loadRemoteImage(url: String) {
        val executor = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())
        executor.execute {
            try {
                val stream = URL(url).openStream()
                val bitmap = BitmapFactory.decodeStream(stream)
                handler.post {
                    if (!isFinishing && bitmap != null) {
                        binding.ivDishImage.setImageBitmap(bitmap)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }
}
