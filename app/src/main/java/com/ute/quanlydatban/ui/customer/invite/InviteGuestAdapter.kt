package com.ute.quanlydatban.ui.customer.invite

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ItemGuestBinding

class InviteGuestAdapter : ListAdapter<GuestUiModel, InviteGuestAdapter.GuestViewHolder>(GuestDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GuestViewHolder {
        val binding = ItemGuestBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GuestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GuestViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GuestViewHolder(
        private val binding: ItemGuestBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: GuestUiModel) {
            val context = binding.root.context
            binding.tvGuestName.text = item.name

            // Status visual
            if (item.isHost) {
                binding.llStatusBadge.setBackgroundResource(R.drawable.bg_owner_stat_today)
                binding.ivStatusIcon.setImageResource(R.drawable.ic_nav_home)
                binding.ivStatusIcon.setColorFilter(ContextCompat.getColor(context, R.color.booktable_orange))
                binding.tvStatusText.text = context.getString(R.string.invite_friends_status_host)
                binding.tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.booktable_orange))
            } else {
                when (item.status) {
                    "accepted" -> {
                        binding.llStatusBadge.setBackgroundResource(R.drawable.bg_owner_status_open)
                        binding.ivStatusIcon.setImageResource(R.drawable.ic_guest_accepted)
                        binding.ivStatusIcon.clearColorFilter()
                        binding.tvStatusText.text = context.getString(R.string.invite_friends_status_accepted)
                        binding.tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.booking_success_green))
                    }
                    "declined" -> {
                        binding.llStatusBadge.setBackgroundResource(R.drawable.bg_owner_input_field)
                        binding.ivStatusIcon.setImageResource(R.drawable.ic_guest_declined)
                        binding.ivStatusIcon.clearColorFilter()
                        binding.tvStatusText.text = context.getString(R.string.invite_friends_status_declined)
                        binding.tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.booktable_muted))
                    }
                    else -> { // "invited" or default
                        binding.llStatusBadge.setBackgroundResource(R.drawable.bg_owner_stat_waiting)
                        binding.ivStatusIcon.setImageResource(R.drawable.ic_guest_pending)
                        binding.ivStatusIcon.clearColorFilter()
                        binding.tvStatusText.text = context.getString(R.string.invite_friends_status_invited)
                        binding.tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.owner_stat_waiting))
                    }
                }
            }

            // Dishes text
            if (item.orderedDishesText.isNotBlank()) {
                binding.tvGuestDishes.text = context.getString(R.string.invite_friends_dishes_ordered_format, item.orderedDishesText)
                binding.tvGuestDishes.setTextColor(ContextCompat.getColor(context, R.color.booktable_dark))
            } else {
                binding.tvGuestDishes.text = context.getString(R.string.invite_friends_no_dishes_yet)
                binding.tvGuestDishes.setTextColor(ContextCompat.getColor(context, R.color.booktable_muted))
            }
        }
    }

    class GuestDiffCallback : DiffUtil.ItemCallback<GuestUiModel>() {
        override fun areItemsTheSame(oldItem: GuestUiModel, newItem: GuestUiModel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: GuestUiModel, newItem: GuestUiModel): Boolean {
            return oldItem == newItem
        }
    }
}
