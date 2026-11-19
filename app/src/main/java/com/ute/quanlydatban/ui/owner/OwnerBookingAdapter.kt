package com.ute.quanlydatban.ui.owner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.databinding.ItemOwnerBookingBinding
import com.ute.quanlydatban.viewmodel.BookingWithDetails

class OwnerBookingAdapter(
    private val onItemClick: (BookingWithDetails) -> Unit,
    private val onConfirmClick: (BookingWithDetails) -> Unit,
    private val onRejectClick: (BookingWithDetails) -> Unit
) : ListAdapter<BookingWithDetails, OwnerBookingAdapter.BookingViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val binding = ItemOwnerBookingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BookingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BookingViewHolder(private val binding: ItemOwnerBookingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BookingWithDetails) {
            val booking = item.booking

            // Avatar 2 chữ cái đầu
            val name = item.customerName.ifBlank { "Khách" }
            val words = name.trim().split("\\s+".toRegex())
            val initials = if (words.size >= 2) {
                "${words.first().take(1)}${words.last().take(1)}".uppercase()
            } else {
                name.take(2).uppercase()
            }
            binding.tvCustomerAvatar.text = initials
            binding.tvCustomerName.text = name

            // Mã booking & trạng thái
            val shortId = "BT-${booking.id.takeLast(4)}"
            val status = BookingStatus.from(booking.status)
            val statusLabel = when (status) {
                BookingStatus.WAITING, BookingStatus.PENDING -> "Chờ duyệt"
                BookingStatus.CONFIRMED -> "Đã xác nhận"
                BookingStatus.CHECKED_IN, BookingStatus.SERVING -> "Đã đến"
                BookingStatus.CANCELLED, BookingStatus.REJECTED -> "Đã hủy"
                BookingStatus.EXPIRED -> "Hết hạn"
                else -> booking.status
            }
            binding.tvBookingCodeStatus.text = "$shortId · $statusLabel"

            // Thời gian, khách, bàn
            binding.tvBookingTime.text = booking.time.ifBlank { "18:30" }
            binding.tvBookingGuests.text = "${booking.guests} khách"
            binding.tvBookingTable.text = item.tableName.ifBlank { "Bàn chung" }

            // Ẩn/hiện nút Xác nhận / Từ chối (chỉ hiện khi đơn đang chờ duyệt)
            val isWaiting = (status == BookingStatus.WAITING || status == BookingStatus.PENDING)
            binding.llCardActions.visibility = if (isWaiting) View.VISIBLE else View.GONE

            binding.root.setOnClickListener {
                onItemClick(item)
            }

            binding.btnCardConfirm.setOnClickListener {
                onConfirmClick(item)
            }

            binding.btnCardReject.setOnClickListener {
                onRejectClick(item)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<BookingWithDetails>() {
            override fun areItemsTheSame(oldItem: BookingWithDetails, newItem: BookingWithDetails): Boolean {
                return oldItem.booking.id == newItem.booking.id
            }

            override fun areContentsTheSame(oldItem: BookingWithDetails, newItem: BookingWithDetails): Boolean {
                return oldItem == newItem
            }
        }
    }
}
