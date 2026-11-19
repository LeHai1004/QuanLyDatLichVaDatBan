package com.ute.quanlydatban.ui.customer.menu

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.MenuItem
import com.ute.quanlydatban.databinding.ItemMenuBinding
import com.ute.quanlydatban.util.formatMoney
import com.ute.quanlydatban.viewmodel.CartItemUiModel

class MenuAdapter(
    private val onAddClick: (MenuItem) -> Unit,
    private val onMinusClick: (MenuItem) -> Unit
) : ListAdapter<MenuItem, MenuAdapter.MenuViewHolder>(DiffCallback) {

    private var cartQuantities: Map<String, Long> = emptyMap()

    fun updateCartState(cartItems: Map<String, CartItemUiModel>) {
        cartQuantities = cartItems.mapValues { it.value.quantity }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val binding = ItemMenuBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MenuViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MenuViewHolder(
        private val binding: ItemMenuBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MenuItem) {
            val context = itemView.context
            binding.tvDishName.text = item.name
            binding.tvDishCategory.text = if (item.available) {
                item.category
            } else {
                "${item.category} · ${context.getString(R.string.menu_item_unavailable_label)}"
            }

            val qty = cartQuantities[item.id] ?: 0L

            if (!item.available) {
                binding.root.alpha = 0.5f
                binding.tvDishPrice.text = context.getString(R.string.menu_item_unavailable_label)
                binding.btnAddDish.visibility = View.VISIBLE
                binding.btnAddDish.isEnabled = false
                binding.llQuantityControl.visibility = View.GONE
                binding.btnAddDish.setOnClickListener(null)
            } else {
                binding.root.alpha = 1.0f
                binding.tvDishPrice.text = formatMoney(item.price)
                binding.btnAddDish.isEnabled = true

                if (qty > 0) {
                    binding.btnAddDish.visibility = View.GONE
                    binding.llQuantityControl.visibility = View.VISIBLE
                    binding.tvQuantity.text = qty.toString()

                    binding.btnPlus.setOnClickListener { onAddClick(item) }
                    binding.btnMinus.setOnClickListener { onMinusClick(item) }
                } else {
                    binding.btnAddDish.visibility = View.VISIBLE
                    binding.llQuantityControl.visibility = View.GONE
                    binding.btnAddDish.setOnClickListener { onAddClick(item) }
                }
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
