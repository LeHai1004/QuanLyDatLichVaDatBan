package com.ute.quanlydatban.ui.customer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.databinding.ItemRestaurantBinding
import com.ute.quanlydatban.util.formatMoney
import java.util.Calendar
import java.util.Locale

class RestaurantAdapter(
    private val onItemClick: (Restaurant) -> Unit
) : ListAdapter<Restaurant, RestaurantAdapter.RestaurantViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RestaurantViewHolder {
        val binding = ItemRestaurantBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RestaurantViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RestaurantViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RestaurantViewHolder(
        private val binding: ItemRestaurantBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(restaurant: Restaurant) {
            val context = itemView.context

            binding.tvRestaurantName.text = restaurant.name
            binding.tvRestaurantCategory.text = restaurant.category
            binding.tvRestaurantRating.text = String.format(Locale.US, "%.1f", restaurant.rating)
            binding.tvRestaurantHours.text = context.getString(
                R.string.restaurant_open_hours_format,
                restaurant.openTime,
                restaurant.closeTime
            )
            binding.tvRestaurantAddress.text = restaurant.address
            binding.tvRestaurantDeposit.text = context.getString(
                R.string.restaurant_deposit_format,
                formatMoney(restaurant.depositAmount)
            )

            // Kiểm tra trạng thái mở cửa
            val isOpen = isCurrentlyOpen(restaurant.openTime, restaurant.closeTime)
            if (isOpen) {
                binding.tvRestaurantStatus.text = context.getString(R.string.restaurant_status_open)
                binding.tvRestaurantStatus.setBackgroundResource(R.drawable.bg_status_badge_open)
                binding.tvRestaurantStatus.setTextColor(ContextCompat.getColor(context, R.color.auth_success))
            } else {
                binding.tvRestaurantStatus.text = context.getString(R.string.restaurant_status_closed)
                binding.tvRestaurantStatus.setBackgroundResource(R.drawable.bg_status_badge_closed)
                binding.tvRestaurantStatus.setTextColor(ContextCompat.getColor(context, R.color.booktable_error))
            }

            binding.root.setOnClickListener {
                onItemClick(restaurant)
            }
        }

        private fun isCurrentlyOpen(openTime: String, closeTime: String): Boolean {
            if (openTime.isBlank() || closeTime.isBlank()) return true
            return try {
                val now = Calendar.getInstance()
                val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

                val openParts = openTime.split(":")
                val closeParts = closeTime.split(":")
                val openMinutes = openParts[0].toInt() * 60 + openParts[1].toInt()
                val closeMinutes = closeParts[0].toInt() * 60 + closeParts[1].toInt()

                if (openMinutes <= closeMinutes) {
                    currentMinutes in openMinutes..closeMinutes
                } else {
                    currentMinutes >= openMinutes || currentMinutes <= closeMinutes
                }
            } catch (e: Exception) {
                true
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
