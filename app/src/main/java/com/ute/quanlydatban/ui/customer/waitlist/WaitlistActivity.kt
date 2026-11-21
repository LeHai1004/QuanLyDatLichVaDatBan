package com.ute.quanlydatban.ui.customer.waitlist

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.User
import com.ute.quanlydatban.data.model.WaitlistEntry
import com.ute.quanlydatban.databinding.ActivityWaitlistBinding
import com.ute.quanlydatban.ui.customer.booking.BookingActivity
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.viewmodel.WaitlistViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WaitlistActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RESTAURANT_ID = "extra_restaurant_id"
        const val EXTRA_RESTAURANT_NAME = "extra_restaurant_name"
        const val EXTRA_DATE = "extra_date"
        const val EXTRA_TIME = "extra_time"
        const val EXTRA_GUESTS = "extra_guests"
    }

    private lateinit var binding: ActivityWaitlistBinding
    private val viewModel by lazy {
        ViewModelProvider(this, WaitlistViewModel.Factory())[WaitlistViewModel::class.java]
    }

    private lateinit var myAdapter: WaitlistAdapter
    private var restaurantId: String = ""
    private var restaurantName: String = ""
    private var selectedDate: String = ""
    private var selectedTime: String = ""
    private var guestCount: Long = 2L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityWaitlistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.coordinatorWaitlistRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        extractIntentExtras()
        setupViews()
        setupListeners()
        setupRecyclerView()
        loadUserProfile()
        observeViewModel()

        viewModel.listenMyWaitlist()
    }

    private fun extractIntentExtras() {
        restaurantId = intent.getStringExtra(EXTRA_RESTAURANT_ID).orEmpty()
        restaurantName = intent.getStringExtra(EXTRA_RESTAURANT_NAME).orEmpty()
        selectedDate = intent.getStringExtra(EXTRA_DATE).orEmpty()
        selectedTime = intent.getStringExtra(EXTRA_TIME).orEmpty()
        guestCount = intent.getLongExtra(EXTRA_GUESTS, 2L).coerceAtLeast(1L)

        val cal = Calendar.getInstance()
        if (selectedDate.isBlank()) {
            val dateSdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            selectedDate = dateSdf.format(cal.time)
        }
        if (selectedTime.isBlank()) {
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = if (cal.get(Calendar.MINUTE) < 30) 30 else 0
            val nextHour = if (minute == 0) hour + 1 else hour
            selectedTime = String.format(Locale.getDefault(), "%02d:%02d", nextHour % 24, minute)
        }
    }

    private fun setupViews() {
        binding.ivBack.setOnClickListener { finish() }

        binding.tvRestaurantName.text = if (restaurantName.isNotBlank()) {
            restaurantName
        } else {
            getString(R.string.waitlist_field_restaurant)
        }

        binding.tvSelectedDate.text = selectedDate
        binding.tvSelectedTime.text = selectedTime
        updateGuestDisplay()
    }

    private fun updateGuestDisplay() {
        binding.tvGuestNumber.text = guestCount.toString()
        binding.tvGuestsCount.text = getString(R.string.booking_guest_format, guestCount)
    }

    private fun setupListeners() {
        // Date Picker
        binding.llDateContainer.setOnClickListener {
            showDatePicker()
        }

        // Time Picker
        binding.llTimeContainer.setOnClickListener {
            showTimePicker()
        }

        // Guests Stepper
        binding.btnMinusGuest.setOnClickListener {
            if (guestCount > 1) {
                guestCount--
                updateGuestDisplay()
            }
        }
        binding.btnPlusGuest.setOnClickListener {
            if (guestCount < 30) {
                guestCount++
                updateGuestDisplay()
            }
        }

        // Register Button
        binding.btnRegisterWaitlist.setOnClickListener {
            handleRegister()
        }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        val parts = selectedDate.split("/")
        if (parts.size == 3) {
            parts[0].toIntOrNull()?.let { cal.set(Calendar.DAY_OF_MONTH, it) }
            parts[1].toIntOrNull()?.let { cal.set(Calendar.MONTH, it - 1) }
            parts[2].toIntOrNull()?.let { cal.set(Calendar.YEAR, it) }
        }

        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDate = String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month + 1, year)
                binding.tvSelectedDate.text = selectedDate
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        dialog.datePicker.minDate = System.currentTimeMillis() - 1000
        dialog.show()
    }

    private fun showTimePicker() {
        val cal = Calendar.getInstance()
        val parts = selectedTime.split(":")
        var initialHour = cal.get(Calendar.HOUR_OF_DAY)
        var initialMin = cal.get(Calendar.MINUTE)
        if (parts.size == 2) {
            initialHour = parts[0].toIntOrNull() ?: initialHour
            initialMin = parts[1].toIntOrNull() ?: initialMin
        }

        val dialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                selectedTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                binding.tvSelectedTime.text = selectedTime
            },
            initialHour,
            initialMin,
            true
        )
        dialog.show()
    }

    private fun loadUserProfile() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val user = doc.toObject(User::class.java)
                if (user != null) {
                    if (binding.etName.text.isNullOrBlank()) {
                        binding.etName.setText(user.name)
                    }
                    if (binding.etPhone.text.isNullOrBlank()) {
                        binding.etPhone.setText(user.phone)
                    }
                }
            }
    }

    private fun handleRegister() {
        val name = binding.etName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()

        if (name.isBlank() || phone.isBlank()) {
            SnackbarHelper.show(binding.root, getString(R.string.waitlist_err_invalid_input))
            return
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val entry = WaitlistEntry(
            userId = uid,
            restaurantId = restaurantId,
            restaurantName = restaurantName,
            date = selectedDate,
            time = selectedTime,
            guests = guestCount,
            customerName = name,
            customerPhone = phone
        )

        viewModel.checkAndRegister(
            entry = entry,
            onTableAvailable = {
                showTableAvailableDialog()
            },
            onRegistered = {
                SnackbarHelper.show(binding.root, getString(R.string.waitlist_register_success))
            },
            onError = { errMsg ->
                SnackbarHelper.show(binding.root, errMsg)
            }
        )
    }

    private fun showTableAvailableDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.waitlist_field_restaurant)
            .setMessage(R.string.waitlist_err_table_available)
            .setPositiveButton(R.string.waitlist_btn_book_now) { _, _ ->
                val intent = Intent(this, BookingActivity::class.java).apply {
                    putExtra("restaurantId", restaurantId)
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton(R.string.snackbar_action_close, null)
            .show()
    }

    private fun setupRecyclerView() {
        myAdapter = WaitlistAdapter(
            isOwnerMode = false,
            onCancelClicked = { entry ->
                showCancelConfirmDialog(entry)
            }
        )
        binding.rvMyWaitlist.apply {
            layoutManager = LinearLayoutManager(this@WaitlistActivity)
            adapter = myAdapter
        }
    }

    private fun showCancelConfirmDialog(entry: WaitlistEntry) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.waitlist_cancel_dialog_title))
            .setMessage(getString(R.string.waitlist_cancel_dialog_msg))
            .setPositiveButton(getString(R.string.waitlist_cancel_dialog_btn_confirm)) { dialog, _ ->
                dialog.dismiss()
                viewModel.cancelWaitlist(entry.id) { success, msg ->
                    if (success) {
                        SnackbarHelper.show(binding.root, getString(R.string.waitlist_cancel_success))
                    } else {
                        SnackbarHelper.show(binding.root, msg ?: getString(R.string.waitlist_cancel_error))
                    }
                }
            }
            .setNegativeButton(getString(R.string.split_bill_dialog_btn_cancel), null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnRegisterWaitlist.isEnabled = !loading
        }

        viewModel.myWaitlist.observe(this) { list ->
            myAdapter.submitList(list)
            binding.tvEmptyMyEntries.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
