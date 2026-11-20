package com.ute.quanlydatban.ui.customer.loyalty

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.Voucher
import com.ute.quanlydatban.data.model.VoucherType
import com.ute.quanlydatban.databinding.ItemLoyaltyVoucherBinding
import com.ute.quanlydatban.util.formatMoney
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoyaltyVoucherAdapter(
    private val onRedeemClick: (Voucher) -> Unit
) : ListAdapter<Voucher, LoyaltyVoucherAdapter.LoyaltyVoucherViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LoyaltyVoucherViewHolder {
        val binding = ItemLoyaltyVoucherBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LoyaltyVoucherViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LoyaltyVoucherViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LoyaltyVoucherViewHolder(
        private val binding: ItemLoyaltyVoucherBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(voucher: Voucher) {
            val context = itemView.context
            binding.tvVoucherCode.text = voucher.code

            val desc = when (voucher.type) {
                VoucherType.PERCENT -> {
                    val maxDesc = if (voucher.maxDiscount > 0L) {
                        " (tối đa ${formatMoney(voucher.maxDiscount)})"
                    } else ""
                    "Giảm ${voucher.value}%$maxDesc đơn từ ${formatMoney(voucher.minOrder)}"
                }
                else -> {
                    "Giảm ${formatMoney(voucher.value)} cho đơn từ ${formatMoney(voucher.minOrder)}"
                }
            }
            binding.tvVoucherDesc.text = desc

            if (voucher.expiry > 0L) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.tvVoucherExpiry.text = "HSD: ${sdf.format(Date(voucher.expiry))}"
            } else {
                binding.tvVoucherExpiry.text = "Không giới hạn thời gian"
            }

            binding.tvPointsCost.text = context.getString(R.string.loyalty_points_format, voucher.pointsCost)

            binding.btnRedeem.setOnClickListener {
                onRedeemClick(voucher)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Voucher>() {
        override fun areItemsTheSame(oldItem: Voucher, newItem: Voucher): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Voucher, newItem: Voucher): Boolean =
            oldItem == newItem
    }
}
