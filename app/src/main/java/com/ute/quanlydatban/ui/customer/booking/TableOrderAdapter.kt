package com.ute.quanlydatban.ui.customer.booking

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.data.model.MenuItem
import com.ute.quanlydatban.databinding.ItemTableOrderBinding
import com.ute.quanlydatban.util.formatMoney

class TableOrderAdapter(
    private val onQuantityChanged: (MenuItem, Long) -> Unit
) : RecyclerView.Adapter<TableOrderAdapter.TableOrderViewHolder>() {

    private val menuItems = mutableListOf<MenuItem>()
    private val quantities = mutableMapOf<String, Long>()

    fun submitList(list: List<MenuItem>, qtyMap: Map<String, Long>) {
        menuItems.clear()
        menuItems.addAll(list)
        quantities.clear()
        quantities.putAll(qtyMap)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TableOrderViewHolder {
        val binding = ItemTableOrderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TableOrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TableOrderViewHolder, position: Int) {
        holder.bind(menuItems[position])
    }

    override fun getItemCount(): Int = menuItems.size

    inner class TableOrderViewHolder(
        private val binding: ItemTableOrderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MenuItem) {
            binding.tvItemName.text = item.name
            binding.tvItemCategory.text = item.category.ifEmpty { "Món ăn" }
            binding.tvItemPrice.text = formatMoney(item.price)

            val qty = quantities[item.id] ?: 0L
            binding.tvQty.text = qty.toString()

            if (!item.available) {
                binding.root.alpha = 0.5f
                binding.btnMinus.isEnabled = false
                binding.btnPlus.isEnabled = false
            } else {
                binding.root.alpha = 1.0f
                binding.btnMinus.isEnabled = true
                binding.btnPlus.isEnabled = true
            }

            binding.btnPlus.setOnClickListener {
                if (item.available) {
                    onQuantityChanged(item, 1L)
                }
            }

            binding.btnMinus.setOnClickListener {
                if (item.available) {
                    onQuantityChanged(item, -1L)
                }
            }
        }
    }
}
