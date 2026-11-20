package com.ute.quanlydatban.ui.customer.waitlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.WaitlistEntry
import com.ute.quanlydatban.databinding.ItemWaitlistBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WaitlistAdapter(
    private val isOwnerMode: Boolean = false,
    private val onCallClicked: ((String) -> Unit)? = null,
    private val onMarkHandledClicked: ((WaitlistEntry) -> Unit)? = null
) : ListAdapter<WaitlistEntry, WaitlistAdapter.WaitlistViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WaitlistViewHolder {
        val binding = ItemWaitlistBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WaitlistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WaitlistViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class WaitlistViewHolder(
        private val binding: ItemWaitlistBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: WaitlistEntry) {
            val context = itemView.context

            // Title & Contact Info
            if (isOwnerMode) {
                binding.tvTitle.text = entry.customerName.ifBlank {
                    "Khách chờ #${entry.id.takeLast(4)}"
                }
                binding.llPhoneRow.visibility = View.VISIBLE
                binding.tvPhone.text = entry.customerPhone.ifBlank {
                    "Chưa cập nhật SĐT"
                }

                // Call button
                if (entry.customerPhone.isNotBlank()) {
                    binding.btnCall.visibility = View.VISIBLE
                    binding.btnCall.setOnClickListener {
                        onCallClicked?.invoke(entry.customerPhone)
                    }
                } else {
                    binding.btnCall.visibility = View.GONE
                }

                // Mark Handled button
                if (entry.handled) {
                    binding.btnMarkHandled.visibility = View.GONE
                } else {
                    binding.btnMarkHandled.visibility = View.VISIBLE
                    binding.btnMarkHandled.setOnClickListener {
                        onMarkHandledClicked?.invoke(entry)
                    }
                }
            } else {
                binding.tvTitle.text = entry.restaurantName.ifBlank {
                    context.getString(R.string.waitlist_field_restaurant)
                }
                binding.llPhoneRow.visibility = View.GONE
                binding.btnCall.visibility = View.GONE
                binding.btnMarkHandled.visibility = View.GONE
            }

            // Time & Guests
            binding.tvDateTimeGuests.text = context.getString(
                R.string.waitlist_entry_info_format,
                entry.time,
                entry.date,
                entry.guests
            )

            // Created At
            if (entry.createdAt > 0) {
                val sdf = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())
                val formattedCreated = sdf.format(Date(entry.createdAt))
                binding.tvCreatedAt.text = context.getString(R.string.waitlist_entry_created_format, formattedCreated)
                binding.tvCreatedAt.visibility = View.VISIBLE
            } else {
                binding.tvCreatedAt.visibility = View.GONE
            }

            // Status Badge
            if (entry.handled) {
                binding.tvStatusBadge.text = context.getString(R.string.waitlist_status_handled)
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_waitlist_badge_handled)
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.waitlist_badge_handled_text))
            } else {
                when (entry.status) {
                    "notified" -> {
                        binding.tvStatusBadge.text = context.getString(R.string.waitlist_status_notified)
                        binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_waitlist_badge_notified)
                        binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.waitlist_badge_notified_text))
                    }
                    "expired" -> {
                        binding.tvStatusBadge.text = context.getString(R.string.waitlist_status_expired)
                        binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_waitlist_badge_expired)
                        binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.waitlist_badge_expired_text))
                    }
                    else -> {
                        binding.tvStatusBadge.text = context.getString(R.string.waitlist_status_waiting)
                        binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_waitlist_badge_waiting)
                        binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.waitlist_badge_waiting_text))
                    }
                }
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<WaitlistEntry>() {
            override fun areItemsTheSame(oldItem: WaitlistEntry, newItem: WaitlistEntry): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: WaitlistEntry, newItem: WaitlistEntry): Boolean {
                return oldItem == newItem
            }
        }
    }
}
