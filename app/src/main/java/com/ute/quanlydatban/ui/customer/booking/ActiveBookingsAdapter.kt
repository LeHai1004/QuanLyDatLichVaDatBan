package com.ute.quanlydatban.ui.customer.booking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.databinding.ItemActiveBookingBinding
import com.ute.quanlydatban.viewmodel.ActiveBookingDisplay

class ActiveBookingsAdapter(
    private val onCardClick: (ActiveBookingDisplay) -> Unit,
    private val onCheckInClick: (ActiveBookingDisplay) -> Unit,
    private val onViewMapClick: (ActiveBookingDisplay) -> Unit,
    private val onNavigateMapClick: (ActiveBookingDisplay) -> Unit
) : RecyclerView.Adapter<ActiveBookingsAdapter.ActiveBookingViewHolder>() {

    private val items = mutableListOf<ActiveBookingDisplay>()

    fun submitList(list: List<ActiveBookingDisplay>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActiveBookingViewHolder {
        val binding = ItemActiveBookingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ActiveBookingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ActiveBookingViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ActiveBookingViewHolder(
        private val binding: ItemActiveBookingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ActiveBookingDisplay) {
            val context = binding.root.context
            val booking = item.booking

            binding.tvRestaurantName.text = item.restaurantName.ifEmpty { "Nhà hàng" }

            val guestsStr = context.getString(R.string.booking_guest_format, booking.guests)
            val dateTimeStr = listOf(booking.date, booking.time, guestsStr)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            binding.tvDateTimeGuests.text = dateTimeStr

            binding.tvTableName.text = item.tableName.ifEmpty { "Bàn của bạn" }

            val isCheckedIn = (booking.status == BookingStatus.CHECKED_IN.name)

            if (isCheckedIn) {
                binding.tvStatusBadge.text = context.getString(R.string.active_bookings_status_checked_in)
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_badge_open)
                binding.tvStatusBadge.setTextColor(
                    ContextCompat.getColor(context, R.color.booking_success_green)
                )

                binding.llActionsConfirmed.visibility = View.GONE
                binding.llActionsCheckedIn.visibility = View.VISIBLE

                binding.btnNavigateMap.setOnClickListener { onNavigateMapClick(item) }
            } else {
                binding.tvStatusBadge.text = context.getString(R.string.active_bookings_status_confirmed)
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_badge_open)
                binding.tvStatusBadge.setTextColor(
                    ContextCompat.getColor(context, R.color.booktable_orange)
                )

                binding.llActionsConfirmed.visibility = View.VISIBLE
                binding.llActionsCheckedIn.visibility = View.GONE

                binding.btnCheckIn.setOnClickListener { onCheckInClick(item) }
                binding.btnViewMap.setOnClickListener { onViewMapClick(item) }
            }

            binding.cardActiveBooking.setOnClickListener { onCardClick(item) }
        }
    }
}
