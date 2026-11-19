package com.ute.quanlydatban.ui.owner

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.databinding.ItemOwnerBookingStatBinding
import com.ute.quanlydatban.util.formatMoney

class OwnerBookingStatAdapter :
    ListAdapter<Booking, OwnerBookingStatAdapter.BookingViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val binding = ItemOwnerBookingStatBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BookingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BookingViewHolder(
        private val binding: ItemOwnerBookingStatBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(booking: Booking) {
            val context = itemView.context

            val displayId = if (booking.id.length > 6) booking.id.takeLast(6) else booking.id
            binding.tvBookingCustomer.text = "Đơn đặt #" + displayId.ifEmpty { "BK" }
            binding.tvBookingDateTime.text = context.getString(
                R.string.owner_booking_date_time,
                booking.date.ifEmpty { "--/--" },
                booking.time.ifEmpty { "--:--" }
            )
            binding.tvBookingGuests.text = context.getString(R.string.owner_guest_count_format, booking.guests)
            binding.tvBookingAmount.text = formatMoney(booking.depositAmount)

            val status = booking.status.uppercase()
            when {
                status == BookingStatus.WAITING.name || status == BookingStatus.PENDING.name -> {
                    binding.tvBookingStatus.text = context.getString(R.string.owner_stat_waiting_title)
                    binding.tvBookingStatus.setBackgroundResource(R.drawable.bg_owner_stat_waiting)
                    binding.tvBookingStatus.setTextColor(ContextCompat.getColor(context, R.color.owner_stat_waiting))
                }
                status == BookingStatus.SERVING.name || status == BookingStatus.CHECKED_IN.name -> {
                    binding.tvBookingStatus.text = context.getString(R.string.owner_stat_serving_title)
                    binding.tvBookingStatus.setBackgroundResource(R.drawable.bg_owner_status_open)
                    binding.tvBookingStatus.setTextColor(ContextCompat.getColor(context, R.color.owner_stat_serving))
                }
                status == BookingStatus.COMPLETED.name || status == BookingStatus.PAID.name -> {
                    binding.tvBookingStatus.text = "Hoàn tất"
                    binding.tvBookingStatus.setBackgroundResource(R.drawable.bg_owner_status_open)
                    binding.tvBookingStatus.setTextColor(ContextCompat.getColor(context, R.color.owner_stat_serving))
                }
                else -> {
                    binding.tvBookingStatus.text = "Đã hủy"
                    binding.tvBookingStatus.setBackgroundResource(R.drawable.bg_owner_status_closed)
                    binding.tvBookingStatus.setTextColor(ContextCompat.getColor(context, R.color.booktable_error))
                }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Booking>() {
        override fun areItemsTheSame(oldItem: Booking, newItem: Booking): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Booking, newItem: Booking): Boolean =
            oldItem == newItem
    }
}
