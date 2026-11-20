package com.ute.quanlydatban.ui.owner.checkout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.repository.CheckoutData
import com.ute.quanlydatban.databinding.ActivityOwnerCheckoutBinding
import com.ute.quanlydatban.databinding.ItemCheckoutDishBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.formatMoney
import com.ute.quanlydatban.viewmodel.OwnerCheckoutViewModel

class OwnerCheckoutActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOOKING_ID = "extra_booking_id"
    }

    private lateinit var binding: ActivityOwnerCheckoutBinding
    private val viewModel: OwnerCheckoutViewModel by viewModels()

    private var currentBookingId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityOwnerCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        currentBookingId = intent.getStringExtra(EXTRA_BOOKING_ID).orEmpty()
        if (currentBookingId.isBlank()) {
            Toast.makeText(this, "Thiếu mã đơn đặt bàn để thanh toán", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupListeners()
        observeViewModel()

        viewModel.loadCheckoutData(currentBookingId)
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.llMethodCash.setOnClickListener {
            selectPaymentMethod("cash")
        }

        binding.llMethodVietQr.setOnClickListener {
            selectPaymentMethod("vietqr")
        }

        binding.btnSplitBill.setOnClickListener {
            val totalPayable = viewModel.checkoutData.value?.finalAmount ?: 0L
            val intent = com.ute.quanlydatban.ui.customer.split.SplitBillActivity.newIntent(
                this,
                currentBookingId,
                totalPayable
            )
            startActivity(intent)
        }

        binding.btnConfirmPayment.setOnClickListener {
            showConfirmPaymentDialog()
        }
    }

    private fun selectPaymentMethod(method: String) {
        viewModel.selectPaymentMethod(method)

        val isCash = (method == "cash")
        binding.rbMethodCash.isChecked = isCash
        binding.rbMethodVietQr.isChecked = !isCash

        if (isCash) {
            binding.llMethodCash.setBackgroundResource(R.drawable.bg_owner_payment_method_selected)
            binding.tvMethodCash.setTextColor(ContextCompat.getColor(this, R.color.booktable_orange))
            binding.llMethodVietQr.setBackgroundResource(R.drawable.bg_owner_payment_method_unselected)
            binding.tvMethodVietQr.setTextColor(ContextCompat.getColor(this, R.color.booktable_dark))
        } else {
            binding.llMethodCash.setBackgroundResource(R.drawable.bg_owner_payment_method_unselected)
            binding.tvMethodCash.setTextColor(ContextCompat.getColor(this, R.color.booktable_dark))
            binding.llMethodVietQr.setBackgroundResource(R.drawable.bg_owner_payment_method_selected)
            binding.tvMethodVietQr.setTextColor(ContextCompat.getColor(this, R.color.booktable_orange))
        }
    }

    private fun observeViewModel() {
        viewModel.isCheckoutLoading.observe(this) { loading ->
            binding.flLoadingOverlay.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnConfirmPayment.isEnabled = !loading
            binding.btnSplitBill.isEnabled = !loading
        }

        viewModel.checkoutError.observe(this) { error ->
            if (!error.isNullOrBlank()) {
                SnackbarHelper.show(binding.root, error)
            }
        }

        viewModel.checkoutData.observe(this) { data ->
            if (data != null) {
                bindCheckoutData(data)
            }
        }

        viewModel.checkoutSuccessPayment.observe(this) { payment ->
            if (payment != null) {
                Toast.makeText(this, getString(R.string.owner_checkout_success), Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun bindCheckoutData(data: CheckoutData) {
        val booking = data.booking
        val table = data.table
        val customer = data.customerUser

        binding.tvBookingCode.text = "#BT-${booking.id.takeLast(4)}"
        binding.tvTableName.text = if (table != null) table.name else "Bàn #${booking.tableId}"

        val customerName = customer?.name?.ifBlank { "Khách hàng" } ?: "Khách hàng"
        val customerPhone = customer?.phone?.ifBlank { "090 123 4567" } ?: ""
        binding.tvCustomerNamePhone.text = "$customerName · $customerPhone"

        binding.tvBookingDateTime.text = "${booking.date} · ${booking.time}"

        // Danh sách món
        val container = binding.llDishesContainer
        container.removeAllViews()

        if (data.orderItems.isEmpty()) {
            binding.tvDishEmpty.visibility = View.VISIBLE
        } else {
            binding.tvDishEmpty.visibility = View.GONE
            for (dish in data.orderItems) {
                val itemBinding = ItemCheckoutDishBinding.inflate(LayoutInflater.from(this), container, false)
                itemBinding.tvDishNameQty.text = "${dish.qty}×  ${dish.name}"
                itemBinding.tvDishSource.text = if (dish.source == "pre") "Đặt trước (Pre-order)" else "Gọi tại bàn"
                val totalPrice = dish.qty * dish.unitPrice
                itemBinding.tvDishPrice.text = formatMoney(totalPrice)
                container.addView(itemBinding.root)
            }
        }

        // Bảng kê thanh toán (Long)
        binding.tvDishesTotal.text = formatMoney(data.totalDishPrice)
        binding.tvDepositPaid.text = "-${formatMoney(data.depositAmount)}"
        binding.tvFinalAmount.text = formatMoney(data.finalAmount)
    }

    private fun showConfirmPaymentDialog() {
        val data = viewModel.checkoutData.value ?: return
        val method = viewModel.selectedPaymentMethod.value ?: "cash"
        val methodText = if (method == "cash") "Tiền mặt" else "Chuyển khoản (VietQR)"
        val amountText = formatMoney(data.finalAmount)

        AlertDialog.Builder(this)
            .setTitle(R.string.owner_checkout_confirm_dialog_title)
            .setMessage(getString(R.string.owner_checkout_confirm_dialog_msg, amountText, methodText))
            .setPositiveButton(R.string.owner_checkout_confirm_dialog_btn_ok) { _, _ ->
                viewModel.confirmPayment()
            }
            .setNegativeButton(R.string.owner_checkout_confirm_dialog_btn_cancel, null)
            .show()
    }
}
