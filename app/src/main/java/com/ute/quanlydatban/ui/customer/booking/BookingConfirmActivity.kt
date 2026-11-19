package com.ute.quanlydatban.ui.customer.booking

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.ute.quanlydatban.MainActivity
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ActivityBookingConfirmBinding
import com.ute.quanlydatban.databinding.ItemConfirmDishBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.formatMoney
import com.ute.quanlydatban.viewmodel.PaymentViewModel

class BookingConfirmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookingConfirmBinding

    private val viewModel by lazy {
        ViewModelProvider(this, PaymentViewModel.Factory())[PaymentViewModel::class.java]
    }

    private var bookingId: String = ""
    private var restaurantId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBookingConfirmBinding.inflate(layoutInflater)
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
        binding.tvBookingCodeBottom.text = getString(R.string.confirm_booking_code_format, bookingCode)
    }

    private fun setupListeners() {
        // Nút quay lại: Từ Confirm trở đi không quay về Payment/Cart -> Xóa back stack, về Trang chủ
        binding.btnBack.setOnClickListener {
            goToHomeScreen()
        }

        // Bấm nút "Xem mã QR đặt bàn" -> sang BookingQRActivity
        binding.btnViewQr.setOnClickListener {
            val intent = Intent(this, BookingQRActivity::class.java).apply {
                putExtra(BookingQRActivity.EXTRA_BOOKING_ID, bookingId)
                putExtra(BookingQRActivity.EXTRA_RESTAURANT_ID, restaurantId)
            }
            startActivity(intent)
        }

        // Nút "Mời bạn bè" (chừa chỗ cho T31)
        binding.btnInviteFriends.setOnClickListener {
            SnackbarHelper.show(binding.root, getString(R.string.feature_coming_soon))
        }
    }

    private fun observeViewModel() {
        viewModel.booking.observe(this) { b ->
            if (b != null) {
                binding.tvBookingDate.text = b.date
                binding.tvBookingTime.text = b.time
                binding.tvBookingGuests.text = getString(R.string.booking_guest_format, b.guests)

                val deposit = b.depositAmount.takeIf { it > 0 } ?: 100_000L
                binding.tvDepositBadge.text = getString(R.string.confirm_deposit_badge_format, formatMoney(deposit))
                binding.tvDepositDeducted.text = "−${formatMoney(deposit)}"

                val bookingCode = "BT${b.id}".take(12)
                binding.tvBookingCodeBottom.text = getString(R.string.confirm_booking_code_format, bookingCode)
            }
        }

        viewModel.restaurant.observe(this) { res ->
            if (res != null) {
                binding.tvRestaurantName.text = res.name
                binding.tvRestaurantAddress.text = res.address
            }
        }

        viewModel.orderItems.observe(this) { items ->
            val container = binding.llDishesContainer
            container.removeAllViews()

            if (items.isNullOrEmpty()) {
                val emptyBinding = ItemConfirmDishBinding.inflate(LayoutInflater.from(this), container, false)
                emptyBinding.tvDishNameQty.text = getString(R.string.cart_empty)
                emptyBinding.tvDishTotalPrice.text = ""
                container.addView(emptyBinding.root)
            } else {
                for (item in items) {
                    val dishBinding = ItemConfirmDishBinding.inflate(LayoutInflater.from(this), container, false)
                    dishBinding.tvDishNameQty.text = "${item.qty} × ${item.name}"
                    dishBinding.tvDishTotalPrice.text = formatMoney(item.qty * item.unitPrice)
                    container.addView(dishBinding.root)
                }
            }

            val remaining = viewModel.calculateRemainingAmount()
            binding.tvRemainingAmount.text = formatMoney(remaining)
        }
    }

    private fun goToHomeScreen() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        goToHomeScreen()
    }

    companion object {
        const val EXTRA_BOOKING_ID = "extra_booking_id"
        const val EXTRA_RESTAURANT_ID = "extra_restaurant_id"
        const val ROLE_CUSTOMER = "customer"
        const val ROLE_OWNER = "owner"
    }
}
