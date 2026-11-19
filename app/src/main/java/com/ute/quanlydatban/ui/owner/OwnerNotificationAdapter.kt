package com.ute.quanlydatban.ui.owner

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.AppNotification
import com.ute.quanlydatban.data.repository.NotificationRepository
import com.ute.quanlydatban.databinding.ItemNotificationBinding

class OwnerNotificationAdapter(
    private val onItemClick: (AppNotification) -> Unit
) : ListAdapter<AppNotification, OwnerNotificationAdapter.NotificationViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NotificationViewHolder(
        private val binding: ItemNotificationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppNotification) {
            val context = itemView.context

            binding.tvNotificationTitle.text = item.title
            binding.tvNotificationBody.text = item.body
            binding.tvNotificationTime.text = formatTimeAgo(item.createdAt, context)

            // Phân biệt rõ thông báo chưa đọc và đã đọc
            if (!item.read) {
                binding.cardNotification.setBackgroundResource(R.drawable.bg_notification_item_unread)
                binding.vUnreadDot.visibility = View.VISIBLE
            } else {
                binding.cardNotification.setBackgroundResource(R.drawable.bg_notification_item_read)
                binding.vUnreadDot.visibility = View.GONE
            }

            // Gán icon và nhãn theo loại thông báo
            when (item.type) {
                NotificationRepository.TYPE_BOOKING_NEW -> {
                    binding.ivNotificationIcon.setImageResource(R.drawable.ic_notification_booking)
                    binding.ivNotificationIcon.setColorFilter(
                        ContextCompat.getColor(context, R.color.booktable_orange)
                    )
                    binding.tvNotificationType.text = context.getString(R.string.owner_notification_type_booking)
                }
                NotificationRepository.TYPE_BOOKING_CANCELLED -> {
                    binding.ivNotificationIcon.setImageResource(R.drawable.ic_notification_cancel)
                    binding.ivNotificationIcon.setColorFilter(
                        ContextCompat.getColor(context, R.color.notification_icon_cancel_tint)
                    )
                    binding.tvNotificationType.text = context.getString(R.string.owner_notification_type_cancel)
                }
                NotificationRepository.TYPE_GUEST_ARRIVED -> {
                    binding.ivNotificationIcon.setImageResource(R.drawable.ic_notification_arrival)
                    binding.ivNotificationIcon.setColorFilter(
                        ContextCompat.getColor(context, R.color.notification_icon_arrival_tint)
                    )
                    binding.tvNotificationType.text = context.getString(R.string.owner_notification_type_arrival)
                }
                else -> {
                    binding.ivNotificationIcon.setImageResource(R.drawable.ic_auth_bell)
                    binding.ivNotificationIcon.setColorFilter(
                        ContextCompat.getColor(context, R.color.booktable_muted)
                    )
                    binding.tvNotificationType.text = context.getString(R.string.owner_notification_type_general)
                }
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }

        private fun formatTimeAgo(createdAt: Long, context: Context): String {
            if (createdAt <= 0L) return context.getString(R.string.owner_notification_time_just_now)
            val diff = System.currentTimeMillis() - createdAt
            val minutes = diff / (60 * 1000)
            val hours = diff / (60 * 60 * 1000)
            val days = diff / (24 * 60 * 60 * 1000)

            return when {
                minutes < 1 -> context.getString(R.string.owner_notification_time_just_now)
                minutes < 60 -> context.getString(R.string.owner_notification_time_minutes_ago, minutes)
                hours < 24 -> context.getString(R.string.owner_notification_time_hours_ago, hours)
                else -> context.getString(R.string.owner_notification_time_days_ago, days)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<AppNotification>() {
        override fun areItemsTheSame(oldItem: AppNotification, newItem: AppNotification): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: AppNotification, newItem: AppNotification): Boolean =
            oldItem == newItem
    }
}
