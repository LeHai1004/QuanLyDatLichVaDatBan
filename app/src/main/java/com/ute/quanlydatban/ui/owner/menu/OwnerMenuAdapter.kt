package com.ute.quanlydatban.ui.owner.menu

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.MenuItem
import com.ute.quanlydatban.databinding.ItemOwnerMenuBinding
import com.ute.quanlydatban.util.formatMoney
import java.net.URL
import java.util.concurrent.Executors

class OwnerMenuAdapter(
    private val onEditClick: (MenuItem) -> Unit,
    private val onToggleAvailability: (MenuItem, Boolean) -> Unit
) : ListAdapter<MenuItem, OwnerMenuAdapter.OwnerMenuViewHolder>(DiffCallback) {

    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OwnerMenuViewHolder {
        val binding = ItemOwnerMenuBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OwnerMenuViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OwnerMenuViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class OwnerMenuViewHolder(
        private val binding: ItemOwnerMenuBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MenuItem) {
            val context = itemView.context
            binding.tvDishName.text = item.name
            binding.tvDishCategory.text = item.category
            binding.tvDishPrice.text = formatMoney(item.price)

            updateAvailabilityUi(item.available)

            binding.swAvailable.setOnCheckedChangeListener(null)
            binding.swAvailable.isChecked = item.available
            binding.swAvailable.setOnCheckedChangeListener { _, isChecked ->
                updateAvailabilityUi(isChecked)
                onToggleAvailability(item, isChecked)
            }

            binding.btnEditDish.setOnClickListener {
                onEditClick(item)
            }
            binding.cardOwnerMenuItem.setOnClickListener {
                onEditClick(item)
            }

            // Load dish image
            binding.ivDishImage.setImageResource(R.drawable.ic_menu_image_placeholder)
            if (item.imageUrl.isNotBlank() && item.imageUrl.startsWith("http")) {
                val tag = item.imageUrl
                binding.ivDishImage.tag = tag
                executor.execute {
                    try {
                        val stream = URL(item.imageUrl).openStream()
                        val bitmap = BitmapFactory.decodeStream(stream)
                        mainHandler.post {
                            if (binding.ivDishImage.tag == tag && bitmap != null) {
                                binding.ivDishImage.setImageBitmap(bitmap)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }

        private fun updateAvailabilityUi(isAvailable: Boolean) {
            val context = itemView.context
            if (isAvailable) {
                binding.tvStatusBadge.text = context.getString(R.string.owner_menu_status_available)
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_owner_menu_status_available)
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.owner_menu_status_available))
                binding.swAvailable.text = context.getString(R.string.owner_menu_status_available)
                binding.cardOwnerMenuItem.alpha = 1.0f
            } else {
                binding.tvStatusBadge.text = context.getString(R.string.owner_menu_status_unavailable)
                binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_owner_menu_status_unavailable)
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.owner_menu_status_unavailable))
                binding.swAvailable.text = context.getString(R.string.owner_menu_status_unavailable)
                binding.cardOwnerMenuItem.alpha = 0.65f
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<MenuItem>() {
        override fun areItemsTheSame(oldItem: MenuItem, newItem: MenuItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: MenuItem, newItem: MenuItem): Boolean =
            oldItem == newItem
    }
}
