package com.ute.quanlydatban.ui.customer.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.databinding.ItemHistoryBinding
import com.ute.quanlydatban.util.formatMoney

class HistoryAdapter(
    private val onChangeSchedule: (Booking) -> Unit,
    private val onCancelBooking: (Booking) -> Unit,
    private val onReview: (Booking) -> Unit,
    private val onReorder: (Booking) -> Unit
) : ListAdapter<Booking, HistoryAdapter.HistoryViewHolder>(DiffCallback) {

    private var restaurantMap: Map<String, Restaurant> = emptyMap()

    fun updateRestaurants(restaurants: Map<String, Restaurant>) {
        this.restaurantMap = restaurants
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HistoryViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(booking: Booking) {
            val context = binding.root.context

            // Tên nhà hàng
            val restaurant = restaurantMap[booking.restaurantId]
            binding.tvRestaurantName.text = restaurant?.name ?: context.getString(
                R.string.booking_change_label_restaurant
            )

            // Trạng thái đơn đặt bàn
            val status = BookingStatus.from(booking.status)
            bindStatusBadge(status)

            // Ngày và giờ
            val timeText = if (booking.time.isNotBlank()) booking.time else "--:--"
            val dateText = if (booking.date.isNotBlank()) booking.date else "--/--/----"
            binding.tvBookingDateTime.text = "$dateText · $timeText"

            // Số khách
            binding.tvBookingGuests.text = context.getString(R.string.history_guests_format, booking.guests)

            // Bàn
            val tableText = if (booking.tableId.isNotBlank()) "Bàn ${booking.tableId}" else "Chưa chọn bàn"
            binding.tvBookingTable.text = tableText

            // Tiền cọc
            val depositText = formatMoney(booking.depositAmount)
            binding.tvDepositAmount.text = context.getString(R.string.history_deposit_label, depositText)

            // Cấu hình các nút thao tác theo trạng thái
            when (status) {
                BookingStatus.PENDING,
                BookingStatus.WAITING,
                BookingStatus.CONFIRMED,
                BookingStatus.CHECKED_IN,
                BookingStatus.SERVING -> {
                    binding.llUpcomingActions.visibility = View.VISIBLE
                    binding.btnReview.visibility = View.GONE
                    binding.btnReorder.visibility = View.GONE
                }
                BookingStatus.PAID -> {
                    binding.llUpcomingActions.visibility = View.GONE
                    binding.btnReview.visibility = View.VISIBLE
                    binding.btnReorder.visibility = View.GONE
                }
                BookingStatus.COMPLETED,
                BookingStatus.CANCELLED,
                BookingStatus.REJECTED,
                BookingStatus.EXPIRED,
                BookingStatus.NO_SHOW -> {
                    binding.llUpcomingActions.visibility = View.GONE
                    binding.btnReview.visibility = View.GONE
                    binding.btnReorder.visibility = View.VISIBLE
                }
            }

            // Gán sự kiện click
            binding.btnChangeSchedule.setOnClickListener { onChangeSchedule(booking) }
            binding.btnCancelBooking.setOnClickListener { onCancelBooking(booking) }
            binding.btnReview.setOnClickListener { onReview(booking) }
            binding.btnReorder.setOnClickListener { onReorder(booking) }
        }

        private fun bindStatusBadge(status: BookingStatus) {
            val context = binding.root.context
            when (status) {
                BookingStatus.PENDING -> {
                    binding.tvBookingStatus.text = context.getString(R.string.history_status_pending)
                    binding.tvBookingStatus.setBackgroundResource(R.drawable.bg_history_status_confirmed)
                    binding.tvBookingStatus.setTextColor(ContextCompat.getColor(context, R.color.history_status_confirmed_text))
                }
                BookingStatus.WAITING -> {
                    binding.tvBookingStatus.text = context.getString(R.string.history_status_waiting)
                    binding.tvBookingStatus.setBackgroundResource(R.drawable.bg_history_status_confirmed)
                    binding.tvBookingStatus.setTextColor(ContextCompat.getColor(context, R.color.history_status_confirmed_text))
                }
                BookingStatus.CONFIRMED -> {
                    binding.tvBookingStatus.text = context.getString(R.string.history_status_confirmed)
                    binding.tvBookingStatus.setBackgroundResource(R.drawable.bg_history_status_confirmed)
                    binding.tvBookingStatus.setTextColor(ContextCompat.getColor(context, R.color.history_status_confirmed_text))
                }
                BookingStatus.CHECKED_IN -> {
                    binding.tvBookingStatus.text = context.getString(R.string.history_status_checked_in)
                    binding.tvBookingStatus.setBackgroundResource(R.drawable.bg_history_status_serving)
                    binding.tvBookingStatus.setTextColor(ContextCompat.getColor(context, R.color.history_status_serving_text))
                }
                BookingStatus.SERVING -> {
                    binding.tvBookingStatus.text = context.getString(R.string.history_status_serving)
                    binding.tvBookingStatus.setBackgroundResource(R.drawable.bg_history_status_serving)
                    binding.tvBookingStatus.setTextColor(ContextCompat.getColor(context, R.color.history_status_serving_text))
                }
                BookingStatus.PAID -> {
                    binding.tvBookingStatus.text = context.getString(R.string.history_status_paid)
                    binding.tvBookingStatus.setBackgroundResource(R.drawable.bg_history_status_completed)
                    binding.tvBookingStatus.setTextColor(ContextCompat.getColor(context, R.color.history_status_completed_text))
                }
                BookingStatus.COMPLETED -> {
                    binding.tvBookingStatus.text = context.getString(R.string.history_status_completed)
                    binding.tvBookingStatus.setBackgroundResource(R.drawable.bg_history_status_completed)
                    binding.tvBookingStatus.setTextColor(ContextCompat.getColor(context, R.color.history_status_completed_text))
                }
                BookingStatus.CANCELLED -> {
                    binding.tvBookingStatus.text = context.getString(R.string.history_status_cancelled)
                    binding.tvBookingStatus.setBackgroundResource(R.drawable.bg_history_status_cancelled)
                    binding.tvBookingStatus.setTextColor(ContextCompat.getColor(context, R.color.history_status_cancelled_text))
                }
                BookingStatus.REJECTED -> {
                    binding.tvBookingStatus.text = context.getString(R.string.history_status_rejected)
                    binding.tvBookingStatus.setBackgroundResource(R.drawable.bg_history_status_cancelled)
                    binding.tvBookingStatus.setTextColor(ContextCompat.getColor(context, R.color.history_status_cancelled_text))
                }
                BookingStatus.EXPIRED -> {
                    binding.tvBookingStatus.text = context.getString(R.string.history_status_expired)
                    binding.tvBookingStatus.setBackgroundResource(R.drawable.bg_history_status_cancelled)
                    binding.tvBookingStatus.setTextColor(ContextCompat.getColor(context, R.color.history_status_cancelled_text))
                }
                BookingStatus.NO_SHOW -> {
                    binding.tvBookingStatus.text = context.getString(R.string.history_status_no_show)
                    binding.tvBookingStatus.setBackgroundResource(R.drawable.bg_history_status_cancelled)
                    binding.tvBookingStatus.setTextColor(ContextCompat.getColor(context, R.color.history_status_cancelled_text))
                }
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<Booking>() {
        override fun areItemsTheSame(oldItem: Booking, newItem: Booking): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Booking, newItem: Booking): Boolean =
            oldItem == newItem
    }
}
