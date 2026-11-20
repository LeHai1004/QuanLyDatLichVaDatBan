package com.ute.quanlydatban.ui.customer.booking

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ActivityBookingQrBinding
import com.ute.quanlydatban.util.QrCodeGenerator
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.viewmodel.PaymentViewModel

class BookingQRActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookingQrBinding

    private val viewModel by lazy {
        ViewModelProvider(this, PaymentViewModel.Factory())[PaymentViewModel::class.java]
    }

    private var bookingId: String = ""
    private var restaurantId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBookingQrBinding.inflate(layoutInflater)
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
            viewModel.loadData(bookingId, restaurantId)
        }
    }

    private fun initIntentData() {
        bookingId = intent.getStringExtra(EXTRA_BOOKING_ID).orEmpty()
        restaurantId = intent.getStringExtra(EXTRA_RESTAURANT_ID).orEmpty()

        if (bookingId.isEmpty()) {
            SnackbarHelper.show(binding.root, getString(R.string.payment_missing_booking_id))
            return
        }

        val bookingCode = "BT$bookingId".take(12)
        binding.tvBookingCode.text = bookingCode

        // Tạo QR Code vé ban đầu từ mã booking
        val qrBitmap = QrCodeGenerator.generateQrBitmap("BOOKTABLE_TICKET:$bookingCode")
        binding.ivQrTicket.setImageBitmap(qrBitmap)
    }

    private fun setupListeners() {
        // Nút quay lại: BookingQR về Confirm
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Nút nhập mã thủ công khi không hiện được QR
        binding.btnManualInput.setOnClickListener {
            val currentCode = binding.tvBookingCode.text.toString()
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.qr_manual_dialog_title))
                .setMessage(getString(R.string.qr_manual_dialog_message, currentCode))
                .setPositiveButton(getString(R.string.qr_manual_dialog_btn_close)) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }

        // Nút "Xem sơ đồ quán" (T18) - chừa chỗ, bấm hiện Snackbar "Sắp ra mắt"
        binding.btnViewMap.setOnClickListener {
            SnackbarHelper.show(binding.root, getString(R.string.feature_coming_soon))
        }

        // Nút "Tôi đã đến quán" -> Mở QRScanActivity (T19)
        binding.btnArrived.setOnClickListener {
            val intent = android.content.Intent(this, QRScanActivity::class.java).apply {
                putExtra(QRScanActivity.EXTRA_BOOKING_ID, bookingId)
                putExtra(QRScanActivity.EXTRA_RESTAURANT_ID, restaurantId)
            }
            startActivity(intent)
        }
    }

    private fun observeViewModel() {
        viewModel.booking.observe(this) { b ->
            if (b != null) {
                val guestsStr = getString(R.string.booking_guest_format, b.guests)
                val summary = listOf(b.date, b.time, guestsStr).filter { it.isNotBlank() }.joinToString(" · ")
                binding.tvBookingDateTimeGuests.text = summary

                val code = "BT${b.id}".take(12)
                binding.tvBookingCode.text = code

                val qrString = if (b.qrCode.isNotBlank()) b.qrCode else "BOOKTABLE_TICKET:$code"
                val qrBitmap = QrCodeGenerator.generateQrBitmap(qrString)
                binding.ivQrTicket.setImageBitmap(qrBitmap)
            }
        }

        viewModel.restaurant.observe(this) { res ->
            if (res != null) {
                binding.tvRestaurantName.text = res.name
                binding.tvWelcomeBottom.text = getString(R.string.qr_restaurant_welcome_format, res.name)
            }
        }
    }

    companion object {
        const val EXTRA_BOOKING_ID = "extra_booking_id"
        const val EXTRA_RESTAURANT_ID = "extra_restaurant_id"
        const val ROLE_CUSTOMER = "customer"
        const val ROLE_OWNER = "owner"
    }
}
