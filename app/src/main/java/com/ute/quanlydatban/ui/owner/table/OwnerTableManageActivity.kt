package com.ute.quanlydatban.ui.owner.table

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ActivityOwnerTableManageBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.viewmodel.OwnerTableManageViewModel

class OwnerTableManageActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RESTAURANT_ID = "extra_restaurant_id"
        const val EXTRA_TABLE_ID = "extra_table_id"
        const val EXTRA_DEFAULT_FLOOR = "extra_default_floor"
    }

    private lateinit var binding: ActivityOwnerTableManageBinding
    private val viewModel: OwnerTableManageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityOwnerTableManageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val restaurantId = intent.getStringExtra(EXTRA_RESTAURANT_ID).orEmpty()
        val tableId = intent.getStringExtra(EXTRA_TABLE_ID)
        val defaultFloor = intent.getLongExtra(EXTRA_DEFAULT_FLOOR, 1L)

        if (restaurantId.isBlank()) {
            Toast.makeText(this, "Thiếu thông tin nhà hàng", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val isEditMode = !tableId.isNullOrBlank()
        binding.tvToolbarTitle.text = if (isEditMode) {
            getString(R.string.owner_table_manage_title_edit)
        } else {
            getString(R.string.owner_table_manage_title_add)
        }
        binding.btnDelete.visibility = if (isEditMode) View.VISIBLE else View.GONE

        // Đặt tầng mặc định nếu tạo mới
        if (!isEditMode) {
            if (defaultFloor == 2L) {
                binding.rbFloor2.isChecked = true
            } else {
                binding.rbFloor1.isChecked = true
            }
        }

        setupListeners()
        observeViewModel()

        viewModel.init(restaurantId, tableId)
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener { finish() }

        binding.btnSave.setOnClickListener {
            handleSave()
        }

        binding.btnDelete.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.flLoadingOverlay.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnSave.isEnabled = !loading
            binding.btnDelete.isEnabled = !loading
        }

        viewModel.table.observe(this) { table ->
            if (table != null) {
                if (binding.etTableName.text.isNullOrBlank()) {
                    binding.etTableName.setText(table.name)
                }
                if (table.floor == 2L) {
                    binding.rbFloor2.isChecked = true
                } else {
                    binding.rbFloor1.isChecked = true
                }
                if (table.type == "room") {
                    binding.rbTypeRoom.isChecked = true
                } else {
                    binding.rbTypeTable.isChecked = true
                }
                if (binding.etCapacity.text.isNullOrBlank()) {
                    binding.etCapacity.setText(table.capacity.toString())
                }
                if (binding.etMapX.text.isNullOrBlank()) {
                    binding.etMapX.setText(table.mapX.toString())
                }
                if (binding.etMapY.text.isNullOrBlank()) {
                    binding.etMapY.setText(table.mapY.toString())
                }
                binding.tvQrCodeValue.text = table.qrCode.ifBlank { "Tự động sinh khi lưu" }
            }
        }

        viewModel.nameError.observe(this) { error ->
            binding.tilTableName.error = error
        }

        viewModel.capacityError.observe(this) { error ->
            binding.tilCapacity.error = error
        }

        viewModel.errorMessage.observe(this) { error ->
            if (!error.isNullOrBlank()) {
                SnackbarHelper.show(binding.root, error)
            }
        }

        viewModel.saveSuccess.observe(this) { success ->
            if (success) {
                Toast.makeText(this, getString(R.string.owner_table_save_success), Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        viewModel.deleteSuccess.observe(this) { success ->
            if (success) {
                Toast.makeText(this, getString(R.string.owner_table_delete_success), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun handleSave() {
        val name = binding.etTableName.text?.toString().orEmpty()
        val floor = if (binding.rbFloor2.isChecked) 2L else 1L
        val type = if (binding.rbTypeRoom.isChecked) "room" else "table"
        val capacity = binding.etCapacity.text?.toString()?.toLongOrNull() ?: 0L
        val mapX = binding.etMapX.text?.toString()?.toDoubleOrNull() ?: 0.0
        val mapY = binding.etMapY.text?.toString()?.toDoubleOrNull() ?: 0.0

        viewModel.saveTable(
            name = name,
            floor = floor,
            type = type,
            capacity = capacity,
            mapX = mapX,
            mapY = mapY
        )
    }

    private fun showDeleteConfirmationDialog() {
        val tableName = binding.etTableName.text?.toString().orEmpty().ifBlank { "bàn này" }
        AlertDialog.Builder(this)
            .setTitle(R.string.owner_table_manage_dialog_delete_title)
            .setMessage(getString(R.string.owner_table_manage_dialog_delete_msg, tableName))
            .setPositiveButton(R.string.owner_table_manage_dialog_delete_confirm) { _, _ ->
                viewModel.deleteTable()
            }
            .setNegativeButton(R.string.owner_table_manage_dialog_delete_cancel, null)
            .show()
    }
}
