package com.ute.quanlydatban.ui.customer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.MenuItem
import com.ute.quanlydatban.databinding.ItemMenuPreviewBinding
import com.ute.quanlydatban.util.formatMoney

class MenuItemPreviewAdapter :
    ListAdapter<MenuItem, MenuItemPreviewAdapter.MenuItemViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuItemViewHolder {
        val binding = ItemMenuPreviewBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MenuItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MenuItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MenuItemViewHolder(
        private val binding: ItemMenuPreviewBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MenuItem) {
            val context = itemView.context

            binding.tvMenuItemName.text = item.name
            binding.tvMenuItemCategory.text = item.category
            binding.tvMenuItemPrice.text = formatMoney(item.price)

            if (item.available) {
                binding.tvMenuItemStatus.text = context.getString(R.string.menu_item_available)
                binding.tvMenuItemStatus.setBackgroundResource(R.drawable.bg_status_badge_open)
                binding.tvMenuItemStatus.setTextColor(
                    ContextCompat.getColor(context, R.color.auth_success)
                )
            } else {
                binding.tvMenuItemStatus.text = context.getString(R.string.menu_item_unavailable)
                binding.tvMenuItemStatus.setBackgroundResource(R.drawable.bg_status_badge_closed)
                binding.tvMenuItemStatus.setTextColor(
                    ContextCompat.getColor(context, R.color.booktable_error)
                )
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
