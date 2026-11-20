package com.ute.quanlydatban.ui.customer.favorite

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.databinding.ItemFavoriteBinding
import com.ute.quanlydatban.util.formatMoney
import java.util.Calendar
import java.util.Locale

class FavoriteAdapter(
    private val onItemClick: (Restaurant) -> Unit,
    private val onHeartClick: (Restaurant) -> Unit
) : ListAdapter<Restaurant, FavoriteAdapter.FavoriteViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val binding = ItemFavoriteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FavoriteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FavoriteViewHolder(
        private val binding: ItemFavoriteBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Restaurant) {
            val context = itemView.context

            binding.tvRestaurantName.text = item.name
            binding.tvRestaurantCategory.text = item.category
            binding.tvRestaurantRating.text = String.format(Locale.US, "%.1f", item.rating)
            binding.tvRestaurantHours.text = context.getString(
                R.string.restaurant_open_hours_format,
                item.openTime.ifBlank { "08:00" },
                item.closeTime.ifBlank { "22:00" }
            )
            binding.tvRestaurantAddress.text = item.address
            binding.tvRestaurantDeposit.text = formatMoney(item.depositAmount)

            val isOpen = isCurrentlyOpen(item.openTime, item.closeTime)
            if (isOpen) {
                binding.tvRestaurantStatus.text = context.getString(R.string.restaurant_status_open)
                binding.tvRestaurantStatus.setBackgroundResource(R.drawable.bg_status_badge_open)
                binding.tvRestaurantStatus.setTextColor(ContextCompat.getColor(context, R.color.auth_success))
            } else {
                binding.tvRestaurantStatus.text = context.getString(R.string.restaurant_status_closed)
                binding.tvRestaurantStatus.setBackgroundResource(R.drawable.bg_status_badge_closed)
                binding.tvRestaurantStatus.setTextColor(ContextCompat.getColor(context, R.color.booktable_error))
            }

            binding.ivHeart.setImageResource(R.drawable.ic_heart_filled)
            binding.ivHeart.setOnClickListener {
                onHeartClick(item)
            }

            binding.cardFavorite.setOnClickListener {
                onItemClick(item)
            }
            binding.btnBookNow.setOnClickListener {
                onItemClick(item)
            }
        }

        private fun isCurrentlyOpen(openTime: String, closeTime: String): Boolean {
            if (openTime.isBlank() || closeTime.isBlank()) return true
            try {
                val now = Calendar.getInstance()
                val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

                val openParts = openTime.split(":")
                val closeParts = closeTime.split(":")
                if (openParts.size < 2 || closeParts.size < 2) return true

                val openMinutes = openParts[0].trim().toInt() * 60 + openParts[1].trim().toInt()
                val closeMinutes = closeParts[0].trim().toInt() * 60 + closeParts[1].trim().toInt()

                return if (closeMinutes > openMinutes) {
                    currentMinutes in openMinutes..closeMinutes
                } else {
                    currentMinutes >= openMinutes || currentMinutes <= closeMinutes
                }
            } catch (_: Exception) {
                return true
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Restaurant>() {
        override fun areItemsTheSame(oldItem: Restaurant, newItem: Restaurant): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Restaurant, newItem: Restaurant): Boolean =
            oldItem == newItem
    }
}
