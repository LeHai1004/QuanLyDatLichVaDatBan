package com.ute.quanlydatban.ui.customer.booking

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ActivityCheckInSuccessBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.viewmodel.CheckInViewModel

class CheckInSuccessActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCheckInSuccessBinding

    private val viewModel by lazy {
        ViewModelProvider(this, CheckInViewModel.Factory())[CheckInViewModel::class.java]
    }

    private var bookingId: String = ""
    private var restaurantId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCheckInSuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initIntentData()
        setupListeners()
        observeViewModel()

        if (bookingId.isNotBlank()) {
            viewModel.loadDetails(bookingId, restaurantId)
        }
    }

    private fun initIntentData() {
        bookingId = intent.getStringExtra(EXTRA_BOOKING_ID).orEmpty()
        restaurantId = intent.getStringExtra(EXTRA_RESTAURANT_ID).orEmpty()

        if (bookingId.isNotBlank()) {
            val code = "BT$bookingId".take(12)
            binding.tvBookingCode.text = code
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnNotification.setOnClickListener {
            SnackbarHelper.show(binding.root, getString(R.string.feature_coming_soon))
        }

        // Nút "Xem đường đến bàn" (T19) - KHÔNG LÀM trong thẻ này
        binding.btnViewMap.setOnClickListener {
            SnackbarHelper.show(binding.root, getString(R.string.checkin_success_map_coming_soon))
        }

        // Nút "Quét QR bàn gọi món" (T26) - KHÔNG LÀM trong thẻ này
        binding.btnTableQr.setOnClickListener {
            SnackbarHelper.show(binding.root, getString(R.string.checkin_success_table_qr_coming_soon))
        }
    }

    private fun observeViewModel() {
        viewModel.booking.observe(this) { b ->
            if (b != null) {
                val code = "BT${b.id}".take(12)
                binding.tvBookingCode.text = code

                val guestsStr = getString(R.string.booking_guest_format, b.guests)
                val summary = listOf(b.time, guestsStr).filter { it.isNotBlank() }.joinToString(" · ")
                binding.tvTimeGuests.text = summary
                binding.tvBookingDate.text = b.date
            }
        }

        viewModel.restaurant.observe(this) { res ->
            if (res != null) {
                binding.tvHeaderWelcome.text = getString(R.string.checkin_success_welcome_header, res.name)
                binding.tvRestaurantWelcome.text = getString(R.string.checkin_success_restaurant_welcome, res.name)
            }
        }

        viewModel.table.observe(this) { tbl ->
            val roomName = if (tbl != null) {
                "${tbl.name} · Tầng ${tbl.floor}"
            } else {
                getString(R.string.table_type_room)
            }
            binding.tvBookedRoom.text = roomName
            binding.tvTableWaiting.text = getString(R.string.checkin_success_table_waiting_format, roomName)
        }

        viewModel.errorMessage.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                SnackbarHelper.show(binding.root, msg)
                viewModel.clearError()
            }
        }
    }

    companion object {
        const val EXTRA_BOOKING_ID = "extra_booking_id"
        const val EXTRA_RESTAURANT_ID = "extra_restaurant_id"
    }
}
