package com.ute.quanlydatban.ui.owner.voucher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.Voucher
import com.ute.quanlydatban.data.model.VoucherType
import com.ute.quanlydatban.databinding.ItemOwnerVoucherBinding
import com.ute.quanlydatban.util.formatMoney
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OwnerVoucherAdapter(
    private val currentRestaurantId: String,
    private val onToggleActive: (Voucher, Boolean) -> Unit,
    private val onEditClick: (Voucher) -> Unit
) : RecyclerView.Adapter<OwnerVoucherAdapter.OwnerVoucherViewHolder>() {

    private val items = mutableListOf<Voucher>()

    fun submitList(list: List<Voucher>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OwnerVoucherViewHolder {
        val binding = ItemOwnerVoucherBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OwnerVoucherViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OwnerVoucherViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class OwnerVoucherViewHolder(
        private val binding: ItemOwnerVoucherBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(voucher: Voucher) {
            val context = binding.root.context
            val isOtherRestaurant = voucher.restaurantId.isNotBlank() && voucher.restaurantId != currentRestaurantId

            binding.tvCode.text = voucher.code

            binding.tvValue.text = if (voucher.type == VoucherType.PERCENT) {
                "Giảm ${voucher.value}%"
            } else {
                "Giảm ${formatMoney(voucher.value)}"
            }

            val minOrderStr = if (voucher.minOrder > 0) {
                context.getString(R.string.voucher_min_order_format, formatMoney(voucher.minOrder))
            } else {
                "Mọi đơn hàng"
            }
            val maxDiscountStr = if (voucher.type == VoucherType.PERCENT && voucher.maxDiscount > 0) {
                " · " + context.getString(R.string.voucher_max_discount_format, formatMoney(voucher.maxDiscount))
            } else ""

            binding.tvCondition.text = "$minOrderStr$maxDiscountStr"

            val dateStr = if (voucher.expiry > 0) {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(voucher.expiry))
            } else "31/12/2026"
            binding.tvExpiry.text = context.getString(R.string.voucher_expiry_format, dateStr)

            binding.switchActive.setOnCheckedChangeListener(null)
            binding.switchActive.isChecked = voucher.active

            if (isOtherRestaurant) {
                binding.switchActive.isEnabled = false
                binding.switchActive.alpha = 0.5f
                binding.btnEditVoucher.visibility = View.GONE
            } else {
                binding.switchActive.isEnabled = true
                binding.switchActive.alpha = 1.0f
                binding.btnEditVoucher.visibility = View.VISIBLE
                binding.switchActive.setOnCheckedChangeListener { _, isChecked ->
                    onToggleActive(voucher, isChecked)
                }
                binding.btnEditVoucher.setOnClickListener {
                    onEditClick(voucher)
                }
            }
        }
    }
}
