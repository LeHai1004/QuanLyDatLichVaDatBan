package com.ute.quanlydatban.ui.owner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.databinding.ActivityOwnerBookingDetailBinding
import com.ute.quanlydatban.databinding.ItemConfirmDishBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.util.formatMoney
import com.ute.quanlydatban.viewmodel.BookingWithDetails
import com.ute.quanlydatban.viewmodel.OwnerBookingViewModel

class OwnerBookingDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOwnerBookingDetailBinding

    private val viewModel by lazy {
        ViewModelProvider(this, OwnerBookingViewModel.Factory())[OwnerBookingViewModel::class.java]
    }

    private var bookingId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityOwnerBookingDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bookingId = intent.getStringExtra(EXTRA_BOOKING_ID).orEmpty()
        if (bookingId.isBlank()) {
            SnackbarHelper.show(binding.root, "Không tìm thấy mã đơn đặt bàn.")
            finish()
            return
        }

        setupTopBar()
        setupListeners()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        if (bookingId.isNotBlank()) {
            viewModel.startListeningBooking(bookingId)
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopListeningBooking()
    }

    private fun setupTopBar() {
        // Nút quay lại: Detail về List
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnMoreOptions.setOnClickListener {
            SnackbarHelper.show(binding.root, getString(R.string.feature_coming_soon))
        }
    }

    private fun setupListeners() {
        // Bấm khối "Món đặt trước" (cả tiêu đề và thẻ danh sách) -> mở OwnerPreOrderActivity truyền bookingId
        binding.llPreOrderHeading.setOnClickListener {
            openPreOrderScreen()
        }

        binding.llPreOrderCard.setOnClickListener {
            openPreOrderScreen()
        }
    }

    private fun openPreOrderScreen() {
        if (bookingId.isBlank()) {
            SnackbarHelper.show(binding.root, "Không tìm thấy mã đơn đặt bàn.")
            return
        }

        val detail = viewModel.currentBookingDetail.value
        if (detail != null && detail.preOrderItems.isEmpty()) {
            SnackbarHelper.show(binding.root, getString(R.string.owner_booking_preorder_empty))
            return
        }

        val intent = Intent(this, OwnerPreOrderActivity::class.java).apply {
            putExtra(OwnerPreOrderActivity.EXTRA_BOOKING_ID, bookingId)
        }
        startActivity(intent)
    }

    private fun showRejectConfirmDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.owner_booking_reject_dialog_title))
            .setMessage(getString(R.string.owner_booking_reject_dialog_message))
            .setPositiveButton(getString(R.string.owner_booking_reject_dialog_btn_confirm)) { dialog, _ ->
                dialog.dismiss()
                viewModel.rejectBooking(bookingId) { success ->
                    if (success) {
                        SnackbarHelper.show(binding.root, getString(R.string.owner_booking_reject_success))
                    }
                }
            }
            .setNegativeButton(getString(R.string.owner_booking_reject_dialog_btn_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            if (loading) {
                UiStateHelper.showLoading(binding.layoutStates.root)
            } else {
                UiStateHelper.hideAll(binding.layoutStates.root)
            }
        }

        viewModel.currentBookingDetail.observe(this) { detail ->
            if (detail != null) {
                populateData(detail)
            }
        }

        viewModel.errorMessage.observe(this) { err ->
            if (!err.isNullOrBlank()) {
                val msg = if (err.contains("STATUS_CONFLICT") || err.contains("thay đổi")) {
                    "Đơn đã thay đổi bởi khách hoặc hệ thống."
                } else {
                    err
                }
                SnackbarHelper.show(binding.root, msg)
                viewModel.clearMessages()
            }
        }

        viewModel.actionSuccessMessage.observe(this) { msg ->
            if (msg != null) {
                SnackbarHelper.show(binding.root, msg)
                viewModel.clearMessages()
            }
        }
    }

    private fun populateData(detail: BookingWithDetails) {
        val booking = detail.booking

        binding.tvBookingCode.text = "#BT-${booking.id.takeLast(4)}"

        // Trạng thái & Nút hành động
        renderStatus(detail)

        // Khách hàng
        val name = detail.customerName.ifBlank { "Khách hàng" }
        binding.tvCustomerName.text = name
        val words = name.trim().split("\\s+".toRegex())
        binding.tvCustomerAvatar.text = if (words.size >= 2) {
            "${words.first().take(1)}${words.last().take(1)}".uppercase()
        } else {
            name.take(2).uppercase()
        }
        binding.tvCustomerPhoneType.text = "${detail.customerPhone.ifBlank { "090 123 4567" }} · ${getString(R.string.owner_booking_customer_regular)}"

        // Gọi điện cho khách
        binding.btnCallCustomer.setOnClickListener {
            if (detail.customerPhone.isNotBlank()) {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${detail.customerPhone}"))
                startActivity(dialIntent)
            }
        }

        // Tiền cọc
        val deposit = booking.depositAmount.takeIf { it > 0 } ?: 100_000L
        binding.tvDepositAmount.text = getString(R.string.owner_booking_deposit_paid_format, formatMoney(deposit))

        // Thời gian & bàn
        binding.tvArrivalTime.text = "${booking.time.ifBlank { "18:30" }} · ${booking.date.ifBlank { "Hôm nay" }}"
        binding.tvArrivalDate.text = booking.date.ifBlank { "Hôm nay" }
        binding.tvTableAndGuests.text = "${detail.tableName.ifBlank { "Bàn chung" }} · ${booking.guests} khách"
        binding.tvRoomType.text = detail.roomType

        // Danh sách món đặt trước
        val dishes = detail.preOrderItems
        val container = binding.llDishesContainer
        container.removeAllViews()

        if (dishes.isEmpty()) {
            binding.tvPreOrderCount.text = "0 món"
            binding.tvPreOrderEmpty.visibility = View.VISIBLE
            binding.vPreOrderDivider.visibility = View.GONE
            binding.llSubtotalRow.visibility = View.GONE
        } else {
            binding.tvPreOrderCount.text = getString(R.string.owner_booking_preorder_count_format, dishes.size)
            binding.tvPreOrderEmpty.visibility = View.GONE
            binding.vPreOrderDivider.visibility = View.VISIBLE
            binding.llSubtotalRow.visibility = View.VISIBLE

            var subtotal = 0L
            for (dish in dishes) {
                val itemBinding = ItemConfirmDishBinding.inflate(LayoutInflater.from(this), container, false)
                itemBinding.tvDishNameQty.text = "${dish.qty}×  ${dish.name}"
                val price = dish.qty * dish.unitPrice
                subtotal += price
                itemBinding.tvDishTotalPrice.text = formatMoney(price)
                container.addView(itemBinding.root)
            }
            binding.tvPreOrderSubtotal.text = formatMoney(subtotal)
        }
    }

    private fun renderStatus(detail: BookingWithDetails) {
        val booking = detail.booking
        val status = BookingStatus.from(booking.status)

        val statusText = when (status) {
            BookingStatus.PENDING, BookingStatus.WAITING -> "Chờ duyệt"
            BookingStatus.CONFIRMED -> "Đã xác nhận"
            BookingStatus.CHECKED_IN -> "Đã nhận bàn"
            BookingStatus.SERVING -> "Đang phục vụ"
            BookingStatus.PAID -> "Đã thanh toán"
            BookingStatus.COMPLETED -> "Đã hoàn tất"
            BookingStatus.REJECTED -> "Đã từ chối"
            BookingStatus.EXPIRED -> "Đã hết hạn"
            BookingStatus.CANCELLED -> "Đã hủy"
            BookingStatus.NO_SHOW -> "Không đến (No-show)"
        }
        binding.tvStatusText.text = statusText

        when (status) {
            BookingStatus.PENDING, BookingStatus.WAITING -> {
                binding.llBottomActions.visibility = View.VISIBLE
                binding.btnReject.visibility = View.VISIBLE
                binding.btnConfirm.visibility = View.VISIBLE
                binding.btnConfirm.text = getString(R.string.owner_booking_btn_confirm)
                binding.btnConfirm.setOnClickListener {
                    viewModel.confirmBooking(bookingId) { success ->
                        if (success) {
                            SnackbarHelper.show(binding.root, getString(R.string.owner_booking_confirm_success))
                        }
                    }
                }
                binding.btnReject.setOnClickListener {
                    showRejectConfirmDialog()
                }
            }
            BookingStatus.CONFIRMED -> {
                binding.llBottomActions.visibility = View.VISIBLE
                binding.btnReject.visibility = View.GONE
                binding.btnConfirm.visibility = View.VISIBLE
                binding.btnConfirm.text = "Quét vé / Check-in"
                binding.btnConfirm.setOnClickListener {
                    val intent = Intent(this, com.ute.quanlydatban.ui.owner.scan.OwnerQRScanActivity::class.java)
                    startActivity(intent)
                }
            }
            BookingStatus.CHECKED_IN -> {
                binding.llBottomActions.visibility = View.VISIBLE
                binding.btnReject.visibility = View.GONE
                binding.btnConfirm.visibility = View.VISIBLE
                binding.btnConfirm.text = "Quét vé bắt đầu phục vụ"
                binding.btnConfirm.setOnClickListener {
                    val intent = Intent(this, com.ute.quanlydatban.ui.owner.scan.OwnerQRScanActivity::class.java)
                    startActivity(intent)
                }
            }
            BookingStatus.SERVING -> {
                binding.llBottomActions.visibility = View.VISIBLE
                binding.btnReject.visibility = View.GONE
                binding.btnConfirm.visibility = View.VISIBLE
                binding.btnConfirm.text = "Thanh toán (Checkout)"
                binding.btnConfirm.setOnClickListener {
                    val intent = Intent(this, com.ute.quanlydatban.ui.owner.checkout.OwnerCheckoutActivity::class.java).apply {
                        putExtra(com.ute.quanlydatban.ui.owner.checkout.OwnerCheckoutActivity.EXTRA_BOOKING_ID, bookingId)
                    }
                    startActivity(intent)
                }
            }
            else -> {
                binding.llBottomActions.visibility = View.GONE
            }
        }
    }

    companion object {
        const val EXTRA_BOOKING_ID = "extra_booking_id"
    }
}
