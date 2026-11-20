package com.ute.quanlydatban.ui.customer.booking

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ActivityPaymentBinding
import com.ute.quanlydatban.util.QrCodeGenerator
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.util.formatMoney
import com.ute.quanlydatban.viewmodel.PaymentViewModel

class PaymentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentBinding

    private val viewModel by lazy {
        ViewModelProvider(this, PaymentViewModel.Factory())[PaymentViewModel::class.java]
    }

    private var bookingId: String = ""
    private var restaurantId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPaymentBinding.inflate(layoutInflater)
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
            finish()
            return
        }

        // Tạo mã QR VietQR mẫu hiển thị
        val transferMemo = "BT$bookingId".take(12)
        binding.tvTransferContent.text = getString(R.string.payment_content_format, transferMemo)
        val qrBitmap = QrCodeGenerator.generateQrBitmap(
            "VIETQR|BEPNHA|0123456789|100000|$transferMemo"
        )
        binding.ivQrCode.setImageBitmap(qrBitmap)
    }

    private fun setupListeners() {
        // Nút quay lại: Payment về Cart
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Bấm nút "Tôi đã chuyển khoản"
        binding.btnPaid.setOnClickListener {
            val deposit = viewModel.getDepositAmount()
            viewModel.confirmDeposit(bookingId, deposit)
        }

        // Đổi phương thức: chừa chỗ
        binding.btnChangeMethod.setOnClickListener {
            SnackbarHelper.show(binding.root, getString(R.string.payment_method_coming_soon))
        }

        // Voucher: mở màn hình VoucherFragment
        binding.llVoucherRow.setOnClickListener {
            val totalBill = viewModel.calculateRawTotalBill()
            val fragment = com.ute.quanlydatban.ui.customer.voucher.VoucherFragment()
            val args = Bundle().apply {
                putString(com.ute.quanlydatban.ui.customer.voucher.VoucherFragment.ARG_RESTAURANT_ID, restaurantId)
                putLong(com.ute.quanlydatban.ui.customer.voucher.VoucherFragment.ARG_TOTAL_BILL, totalBill)
            }
            fragment.arguments = args

            supportFragmentManager.setFragmentResultListener(
                com.ute.quanlydatban.ui.customer.voucher.VoucherFragment.REQUEST_KEY, this
            ) { _, bundle ->
                val code = bundle.getString(com.ute.quanlydatban.ui.customer.voucher.VoucherFragment.RESULT_VOUCHER_CODE).orEmpty()
                if (code.isNotBlank()) {
                    viewModel.applyVoucherByCode(code)
                }
            }

            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit()
        }
        binding.llPointsRow.setOnClickListener {
            SnackbarHelper.show(binding.root, getString(R.string.feature_coming_soon))
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.btnPaid.isEnabled = !loading
            if (loading) {
                UiStateHelper.showLoading(binding.layoutStates.root)
            } else {
                UiStateHelper.hideAll(binding.layoutStates.root)
            }
        }

        viewModel.booking.observe(this) { b ->
            if (b != null) {
                val guestsStr = getString(R.string.booking_guest_format, b.guests)
                val summary = listOf(b.date, b.time, guestsStr).filter { it.isNotBlank() }.joinToString(" · ")
                binding.tvBookingTimeGuests.text = summary

                val memo = "BT${b.id}".take(12)
                binding.tvTransferContent.text = getString(R.string.payment_content_format, memo)

                // Cập nhật lại ảnh QR với mã booking chính xác
                val qrBitmap = QrCodeGenerator.generateQrBitmap(
                    "VIETQR|BEPNHA|0123456789|${b.depositAmount.takeIf { it > 0 } ?: 100_000L}|$memo"
                )
                binding.ivQrCode.setImageBitmap(qrBitmap)
            }
        }

        viewModel.restaurant.observe(this) { res ->
            if (res != null) {
                binding.tvRestaurantName.text = res.name
            }
        }

        viewModel.selectedVoucher.observe(this) { voucher ->
            if (voucher != null) {
                binding.tvVoucherLabel.text = "Mã ưu đãi: ${voucher.code}"
                binding.tvVoucherLabel.setTextColor(getColor(R.color.booktable_orange))
            } else {
                binding.tvVoucherLabel.text = getString(R.string.payment_voucher_placeholder)
                binding.tvVoucherLabel.setTextColor(getColor(R.color.booktable_muted))
            }
            updatePriceBreakdown()
        }

        viewModel.voucherDiscount.observe(this) {
            updatePriceBreakdown()
        }

        // Khi cọc thành công: chuyển sang BookingConfirmActivity
        viewModel.isPaymentSuccess.observe(this) { success ->
            if (success) {
                val intent = Intent(this, BookingConfirmActivity::class.java).apply {
                    putExtra(BookingConfirmActivity.EXTRA_BOOKING_ID, bookingId)
                    putExtra(BookingConfirmActivity.EXTRA_RESTAURANT_ID, restaurantId)
                }
                startActivity(intent)
                finish()
            }
        }
    }

    private fun updatePriceBreakdown() {
        val totalBill = viewModel.calculateTotalBill()
        val deposit = viewModel.getDepositAmount()
        val remaining = viewModel.calculateRemainingAmount()
        val discount = viewModel.voucherDiscount.value ?: 0L

        binding.tvDepositAmount.text = formatMoney(deposit)
        binding.tvDepositAmountRow.text = formatMoney(deposit)
        binding.tvTotalBillAmount.text = formatMoney(totalBill)
        binding.tvRemainingAmount.text = formatMoney(remaining)

        if (discount > 0L) {
            binding.llVoucherDiscountRow.visibility = View.VISIBLE
            binding.tvVoucherDiscountAmount.text = "-${formatMoney(discount)}"
        } else {
            binding.llVoucherDiscountRow.visibility = View.GONE
        }
    }

    companion object {
        const val EXTRA_BOOKING_ID = "extra_booking_id"
        const val EXTRA_RESTAURANT_ID = "extra_restaurant_id"
        const val ROLE_CUSTOMER = "customer"
        const val ROLE_OWNER = "owner"
    }
}
