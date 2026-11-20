package com.ute.quanlydatban.ui.customer.booking

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.OrderItem
import com.ute.quanlydatban.data.model.OrderItemStatus
import com.ute.quanlydatban.data.repository.OrderRepository
import com.ute.quanlydatban.databinding.ItemOrderTrackingBinding
import com.ute.quanlydatban.util.formatMoney

class OrderTrackingAdapter : RecyclerView.Adapter<OrderTrackingAdapter.OrderTrackingViewHolder>() {

    private val items = mutableListOf<OrderItem>()

    fun submitList(list: List<OrderItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderTrackingViewHolder {
        val binding = ItemOrderTrackingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OrderTrackingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderTrackingViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class OrderTrackingViewHolder(
        private val binding: ItemOrderTrackingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: OrderItem) {
            val context = binding.root.context
            binding.tvDishName.text = item.name

            val totalPrice = item.unitPrice * item.qty
            binding.tvQtyPrice.text = "x${item.qty} · ${formatMoney(totalPrice)}"

            binding.tvSourceTag.text = if (item.source == OrderRepository.SOURCE_TABLE) "Tại bàn" else "Đặt trước"

            val status = OrderItemStatus.from(item.status)
            when (status) {
                OrderItemStatus.WAITING -> {
                    binding.tvStatusBadge.text = context.getString(R.string.order_tracking_status_waiting)
                    binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.order_status_waiting_bg))
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.order_status_waiting_text))
                }
                OrderItemStatus.COOKING -> {
                    binding.tvStatusBadge.text = context.getString(R.string.order_tracking_status_cooking)
                    binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.order_status_cooking_bg))
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.order_status_cooking_text))
                }
                OrderItemStatus.DONE -> {
                    binding.tvStatusBadge.text = context.getString(R.string.order_tracking_status_done)
                    binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.order_status_done_bg))
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.order_status_done_text))
                }
                OrderItemStatus.SERVED -> {
                    binding.tvStatusBadge.text = context.getString(R.string.order_tracking_status_served)
                    binding.tvStatusBadge.setBackgroundColor(ContextCompat.getColor(context, R.color.order_status_served_bg))
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.order_status_served_text))
                }
            }
        }
    }
}
