package com.ute.quanlydatban.ui.customer

import android.content.Context
import android.graphics.Typeface
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

class NotificationAdapter(
    private val onItemClick: (AppNotification) -> Unit
) : ListAdapter<AppNotification, NotificationAdapter.NotificationViewHolder>(DiffCallback) {

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

            val titleStyle = if (item.read) Typeface.NORMAL else Typeface.BOLD
            binding.tvNotificationTitle.setTypeface(null, titleStyle)
            binding.cardNotification.setBackgroundResource(
                if (item.read) R.drawable.bg_notification_item_read
                else R.drawable.bg_notification_item_unread
            )
            binding.vUnreadDot.visibility = if (item.read) View.GONE else View.VISIBLE

            when (item.type.lowercase()) {
                NotificationRepository.TYPE_BOOKING_NEW,
                TYPE_BOOKING_CONFIRMED,
                TYPE_BOOKING_REJECTED -> {
                    binding.ivNotificationIcon.setImageResource(R.drawable.ic_notification_booking)
                    binding.ivNotificationIcon.setColorFilter(
                        ContextCompat.getColor(context, R.color.booktable_orange)
                    )
                    binding.tvNotificationType.text =
                        context.getString(R.string.customer_notification_type_booking)
                }
                NotificationRepository.TYPE_BOOKING_CANCELLED -> {
                    binding.ivNotificationIcon.setImageResource(R.drawable.ic_notification_cancel)
                    binding.ivNotificationIcon.setColorFilter(
                        ContextCompat.getColor(context, R.color.notification_icon_cancel_tint)
                    )
                    binding.tvNotificationType.text =
                        context.getString(R.string.customer_notification_type_cancel)
                }
                NotificationRepository.TYPE_GUEST_ARRIVED -> {
                    binding.ivNotificationIcon.setImageResource(R.drawable.ic_notification_arrival)
                    binding.ivNotificationIcon.setColorFilter(
                        ContextCompat.getColor(context, R.color.notification_icon_arrival_tint)
                    )
                    binding.tvNotificationType.text =
                        context.getString(R.string.customer_notification_type_arrival)
                }
                else -> {
                    binding.ivNotificationIcon.setImageResource(R.drawable.ic_auth_bell)
                    binding.ivNotificationIcon.setColorFilter(
                        ContextCompat.getColor(context, R.color.booktable_muted)
                    )
                    binding.tvNotificationType.text =
                        context.getString(R.string.customer_notification_type_general)
                }
            }

            binding.root.setOnClickListener { onItemClick(item) }
        }

        private fun formatTimeAgo(createdAt: Long, context: Context): String {
            if (createdAt <= 0L) {
                return context.getString(R.string.owner_notification_time_just_now)
            }
            val diff = System.currentTimeMillis() - createdAt
            val minutes = diff / MINUTE_MS
            val hours = diff / HOUR_MS
            val days = diff / DAY_MS

            return when {
                minutes < 1 -> context.getString(R.string.owner_notification_time_just_now)
                minutes < 60 -> context.getString(R.string.owner_notification_time_minutes_ago, minutes)
                hours < 24 -> context.getString(R.string.owner_notification_time_hours_ago, hours)
                else -> context.getString(R.string.owner_notification_time_days_ago, days)
            }
        }
    }

    companion object {
        private const val MINUTE_MS = 60 * 1000L
        private const val HOUR_MS = 60 * MINUTE_MS
        private const val DAY_MS = 24 * HOUR_MS
        private const val TYPE_BOOKING_CONFIRMED = "booking_confirmed"
        private const val TYPE_BOOKING_REJECTED = "booking_rejected"

        private val DiffCallback = object : DiffUtil.ItemCallback<AppNotification>() {
            override fun areItemsTheSame(
                oldItem: AppNotification,
                newItem: AppNotification
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: AppNotification,
                newItem: AppNotification
            ): Boolean = oldItem == newItem
        }
    }
}
