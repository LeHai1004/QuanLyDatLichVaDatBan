package com.ute.quanlydatban.ui.owner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.OrderItemStatus
import com.ute.quanlydatban.databinding.ItemKitchenBinding
import com.ute.quanlydatban.viewmodel.KitchenItemDisplay

class OwnerKitchenAdapter(
    private val onActionClick: (KitchenItemDisplay, OrderItemStatus) -> Unit
) : RecyclerView.Adapter<OwnerKitchenAdapter.OwnerKitchenViewHolder>() {

    private val items = mutableListOf<KitchenItemDisplay>()

    fun submitList(list: List<KitchenItemDisplay>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OwnerKitchenViewHolder {
        val binding = ItemKitchenBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OwnerKitchenViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OwnerKitchenViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class OwnerKitchenViewHolder(
        private val binding: ItemKitchenBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: KitchenItemDisplay) {
            val context = binding.root.context
            val orderItem = item.orderItem

            binding.tvTableName.text = item.tableName.ifEmpty { "Bàn chung" }
            binding.tvSourceTag.text = item.sourceText.ifEmpty { "Tại bàn" }
            binding.tvDishName.text = orderItem.name
            binding.tvQty.text = "Số lượng: ${orderItem.qty}"

            val currentStatus = OrderItemStatus.from(orderItem.status)

            when (currentStatus) {
                OrderItemStatus.WAITING -> {
                    binding.tvStatusBadge.text = context.getString(R.string.order_tracking_status_waiting)
                    binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.order_status_waiting_bg))
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.order_status_waiting_text))

                    binding.btnAction.visibility = View.VISIBLE
                    binding.btnAction.text = context.getString(R.string.owner_kitchen_btn_start_cooking)
                    binding.btnAction.setOnClickListener {
                        binding.btnAction.isEnabled = false
                        onActionClick(item, OrderItemStatus.COOKING)
                    }
                }
                OrderItemStatus.COOKING -> {
                    binding.tvStatusBadge.text = context.getString(R.string.order_tracking_status_cooking)
                    binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.order_status_cooking_bg))
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.order_status_cooking_text))

                    binding.btnAction.visibility = View.VISIBLE
                    binding.btnAction.text = context.getString(R.string.owner_kitchen_btn_done)
                    binding.btnAction.setOnClickListener {
                        binding.btnAction.isEnabled = false
                        onActionClick(item, OrderItemStatus.DONE)
                    }
                }
                OrderItemStatus.DONE -> {
                    binding.tvStatusBadge.text = context.getString(R.string.order_tracking_status_done)
                    binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.order_status_done_bg))
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.order_status_done_text))

                    binding.btnAction.visibility = View.VISIBLE
                    binding.btnAction.text = context.getString(R.string.owner_kitchen_btn_served)
                    binding.btnAction.setOnClickListener {
                        binding.btnAction.isEnabled = false
                        onActionClick(item, OrderItemStatus.SERVED)
                    }
                }
                OrderItemStatus.SERVED -> {
                    binding.tvStatusBadge.text = context.getString(R.string.order_tracking_status_served)
                    binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.order_status_served_bg))
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.order_status_served_text))

                    binding.btnAction.visibility = View.GONE
                }
            }

            binding.btnAction.isEnabled = true
        }
    }
}
