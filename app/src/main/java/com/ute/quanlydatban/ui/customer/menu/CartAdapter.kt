package com.ute.quanlydatban.ui.customer.menu

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ItemCartBinding
import com.ute.quanlydatban.util.formatMoney
import com.ute.quanlydatban.viewmodel.CartItemUiModel

class CartAdapter(
    private val onIncrease: (String) -> Unit,
    private val onDecrease: (String) -> Unit
) : ListAdapter<CartItemUiModel, CartAdapter.CartViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CartViewHolder(
        private val binding: ItemCartBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CartItemUiModel) {
            val context = itemView.context
            binding.tvDishName.text = item.menuItem.name
            binding.tvUnitPrice.text = context.getString(
                R.string.cart_item_unit_price_format,
                formatMoney(item.unitPrice)
            )
            binding.tvQuantity.text = item.quantity.toString()
            binding.tvItemTotal.text = formatMoney(item.totalPrice)

            binding.btnPlus.setOnClickListener {
                onIncrease(item.menuItem.id)
            }

            binding.btnMinus.setOnClickListener {
                onDecrease(item.menuItem.id)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CartItemUiModel>() {
        override fun areItemsTheSame(oldItem: CartItemUiModel, newItem: CartItemUiModel): Boolean =
            oldItem.menuItem.id == newItem.menuItem.id

        override fun areContentsTheSame(oldItem: CartItemUiModel, newItem: CartItemUiModel): Boolean =
            oldItem.quantity == newItem.quantity && oldItem.unitPrice == newItem.unitPrice
    }
}
