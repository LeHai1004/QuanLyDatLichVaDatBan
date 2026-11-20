package com.ute.quanlydatban.ui.customer.history

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import com.ute.quanlydatban.data.model.DiningTable
import com.ute.quanlydatban.databinding.ActivityBookingChangeBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.viewmodel.BookingChangeViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class BookingChangeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOOKING_ID = "extra_booking_id"
        const val EXTRA_SHOW_CANCEL_DIRECT = "extra_show_cancel_direct"
    }

    private lateinit var binding: ActivityBookingChangeBinding
    private val viewModel: BookingChangeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBookingChangeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val bookingId = intent.getStringExtra(EXTRA_BOOKING_ID).orEmpty()
        if (bookingId.isBlank()) {
            Toast.makeText(this, "Không tìm thấy mã đơn đặt bàn", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupListeners()
        observeViewModel()

        viewModel.loadBooking(bookingId)

        if (intent.getBooleanExtra(EXTRA_SHOW_CANCEL_DIRECT, false)) {
            showCancelConfirmationDialog()
        }
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener { finish() }

        binding.llSelectDate.setOnClickListener { showDatePicker() }
        binding.llSelectTime.setOnClickListener { showTimePicker() }
        binding.llSelectTable.setOnClickListener { showTablePicker() }

        binding.btnConfirmChange.setOnClickListener {
            viewModel.submitReschedule()
        }

        binding.btnCancelBooking.setOnClickListener {
            showCancelConfirmationDialog()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.flLoadingOverlay.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnConfirmChange.isEnabled = !loading
            binding.btnCancelBooking.isEnabled = !loading
        }

        viewModel.errorMessage.observe(this) { error ->
            if (!error.isNullOrBlank()) {
                SnackbarHelper.show(binding.root, error)
            }
        }

        viewModel.booking.observe(this) { booking ->
            if (booking != null) {
                binding.tvCurrentBookingId.text = getString(R.string.booking_change_label_booking_id, booking.id)
                binding.tvCurrentDateTime.text = getString(
                    R.string.booking_change_label_current_time,
                    booking.date,
                    booking.time
                )
                binding.tvCurrentTable.text = getString(
                    R.string.booking_change_label_current_table,
                    if (booking.tableId.isNotBlank()) "Bàn ${booking.tableId}" else "Chưa chọn bàn"
                )
                binding.tvCurrentGuests.text = getString(
                    R.string.booking_change_label_guests,
                    booking.guests
                )
            }
        }

        viewModel.restaurant.observe(this) { res ->
            if (res != null) {
                binding.tvCurrentRestaurantName.text = res.name
            }
        }

        viewModel.selectedDate.observe(this) { date ->
            binding.tvNewDate.text = date
        }

        viewModel.selectedTime.observe(this) { time ->
            binding.tvNewTime.text = time
        }

        viewModel.selectedTable.observe(this) { table ->
            if (table != null) {
                binding.tvNewTable.text = "${table.name} (Tầng ${table.floor} - ${table.capacity} chỗ)"
            } else {
                binding.tvNewTable.text = getString(R.string.booking_change_label_new_table)
            }
        }

        viewModel.changeSuccess.observe(this) { success ->
            if (success) {
                Toast.makeText(this, getString(R.string.booking_change_success), Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        viewModel.cancelSuccess.observe(this) { success ->
            if (success) {
                Toast.makeText(this, getString(R.string.booking_cancel_success), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val currentStr = viewModel.selectedDate.value.orEmpty()
        if (currentStr.isNotBlank()) {
            try {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val parsed = sdf.parse(currentStr)
                if (parsed != null) calendar.time = parsed
            } catch (_: Exception) {}
        }

        val datePicker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val formatted = String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month + 1, year)
                viewModel.setDate(formatted)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.datePicker.minDate = System.currentTimeMillis()
        datePicker.show()
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val currentStr = viewModel.selectedTime.value.orEmpty()
        if (currentStr.isNotBlank()) {
            try {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                val parsed = sdf.parse(currentStr)
                if (parsed != null) calendar.time = parsed
            } catch (_: Exception) {}
        }

        val timePicker = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val formatted = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                viewModel.setTime(formatted)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
        timePicker.show()
    }

    private fun showTablePicker() {
        val tables = viewModel.tables.value.orEmpty()
        val currentBooking = viewModel.booking.value
        val requiredGuests = currentBooking?.guests ?: 1L

        // Lọc các bàn có đủ sức chứa
        val eligibleTables = tables.filter { it.capacity >= requiredGuests }
        if (eligibleTables.isEmpty()) {
            SnackbarHelper.show(binding.root, "Không có bàn nào đủ sức chứa $requiredGuests khách")
            return
        }

        val tableNames = eligibleTables.map { table ->
            val statusNote = if (table.status == "booked") " (Đang có khách)" else " (Trống)"
            "${table.name} - Tầng ${table.floor} (${table.capacity} chỗ)$statusNote"
        }.toTypedArray()

        val selectedTable = viewModel.selectedTable.value
        val currentIndex = eligibleTables.indexOfFirst { it.id == selectedTable?.id }

        AlertDialog.Builder(this)
            .setTitle(R.string.booking_change_label_new_table)
            .setSingleChoiceItems(tableNames, currentIndex) { dialog, which ->
                val chosen = eligibleTables[which]
                viewModel.selectTable(chosen)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCancelConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.booking_change_dialog_cancel_title)
            .setMessage(R.string.booking_change_dialog_cancel_msg)
            .setPositiveButton(R.string.booking_change_dialog_cancel_positive) { _, _ ->
                viewModel.submitCancel()
            }
            .setNegativeButton(R.string.booking_change_dialog_cancel_negative, null)
            .show()
    }
}
