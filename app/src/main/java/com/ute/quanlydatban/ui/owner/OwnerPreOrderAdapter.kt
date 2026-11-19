package com.ute.quanlydatban.ui.owner

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.OrderItem
import com.ute.quanlydatban.data.model.OrderItemStatus
import com.ute.quanlydatban.databinding.ItemOwnerPreorderDishBinding
import com.ute.quanlydatban.databinding.ItemOwnerPreorderGroupBinding
import com.ute.quanlydatban.util.formatMoney
import com.ute.quanlydatban.viewmodel.BookingWithDetails

class OwnerPreOrderAdapter(
    private val onDonePreparingClick: (BookingWithDetails) -> Unit
) : ListAdapter<BookingWithDetails, OwnerPreOrderAdapter.PreOrderViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreOrderViewHolder {
        val binding = ItemOwnerPreorderGroupBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PreOrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PreOrderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PreOrderViewHolder(private val binding: ItemOwnerPreorderGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BookingWithDetails) {
            val booking = item.booking
            binding.tvBookingCode.text = "#BT-${booking.id.takeLast(4)}"
            binding.tvTimeTable.text = "${booking.time.ifBlank { "18:30" }} · ${item.tableName.ifBlank { "Bàn chung" }}"
            binding.tvCustomerSummary.text = "${item.customerName} · ${booking.guests} khách"

            // Populate dishes
            val container = binding.llDishesList
            container.removeAllViews()

            var subtotal = 0L
            val context = binding.root.context

            for (dish in item.preOrderItems) {
                val dishBinding = ItemOwnerPreorderDishBinding.inflate(
                    LayoutInflater.from(context),
                    container,
                    false
                )
                dishBinding.tvDishQty.text = "${dish.qty}×"
                dishBinding.tvDishName.text = dish.name
                val price = dish.qty * dish.unitPrice
                subtotal += price
                dishBinding.tvDishPrice.text = formatMoney(price)

                val status = OrderItemStatus.from(dish.status)
                when (status) {
                    OrderItemStatus.DONE, OrderItemStatus.SERVED -> {
                        dishBinding.vStatusDot.setBackgroundResource(R.drawable.bg_status_dot_done)
                        dishBinding.tvStatusText.text = context.getString(R.string.owner_preorder_status_done)
                        dishBinding.tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.owner_stat_serving))
                    }
                    OrderItemStatus.COOKING -> {
                        dishBinding.vStatusDot.setBackgroundResource(R.drawable.bg_status_dot_cooking)
                        dishBinding.tvStatusText.text = context.getString(R.string.owner_preorder_status_cooking)
                        dishBinding.tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.booktable_orange))
                    }
                    else -> {
                        dishBinding.vStatusDot.setBackgroundResource(R.drawable.bg_status_dot_waiting)
                        dishBinding.tvStatusText.text = context.getString(R.string.owner_preorder_status_waiting)
                        dishBinding.tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.booktable_muted))
                    }
                }

                container.addView(dishBinding.root)
            }

            binding.tvSubtotalPrice.text = formatMoney(subtotal)

            binding.btnDonePreparing.setOnClickListener {
                onDonePreparingClick(item)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<BookingWithDetails>() {
            override fun areItemsTheSame(oldItem: BookingWithDetails, newItem: BookingWithDetails): Boolean {
                return oldItem.booking.id == newItem.booking.id
            }

            override fun areContentsTheSame(oldItem: BookingWithDetails, newItem: BookingWithDetails): Boolean {
                return oldItem == newItem
            }
        }
    }
}
