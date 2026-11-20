package com.ute.quanlydatban.ui.customer.voucher

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.UserVoucher
import com.ute.quanlydatban.data.model.Voucher
import com.ute.quanlydatban.data.model.VoucherType
import com.ute.quanlydatban.databinding.ItemVoucherBinding
import com.ute.quanlydatban.util.formatMoney
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoucherAdapter(
    private val onUseClick: (Voucher) -> Unit
) : RecyclerView.Adapter<VoucherAdapter.VoucherViewHolder>() {

    private val items = mutableListOf<Pair<Voucher, UserVoucher?>>()

    fun submitList(list: List<Pair<Voucher, UserVoucher?>>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoucherViewHolder {
        val binding = ItemVoucherBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VoucherViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VoucherViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VoucherViewHolder(
        private val binding: ItemVoucherBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(pair: Pair<Voucher, UserVoucher?>) {
            val (voucher, userVoucher) = pair
            val context = binding.root.context

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

            val now = System.currentTimeMillis()
            val isExpired = voucher.expiry in 1..<now
            val isUsed = userVoucher?.used == true

            when {
                isUsed -> {
                    binding.btnUseVoucher.isEnabled = false
                    binding.btnUseVoucher.text = context.getString(R.string.voucher_status_used)
                    binding.btnUseVoucher.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.voucher_disabled_bg)
                    )
                    binding.btnUseVoucher.setTextColor(
                        ContextCompat.getColor(context, R.color.voucher_disabled_text)
                    )
                }
                isExpired -> {
                    binding.btnUseVoucher.isEnabled = false
                    binding.btnUseVoucher.text = context.getString(R.string.voucher_status_expired)
                    binding.btnUseVoucher.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.voucher_disabled_bg)
                    )
                    binding.btnUseVoucher.setTextColor(
                        ContextCompat.getColor(context, R.color.voucher_disabled_text)
                    )
                }
                else -> {
                    binding.btnUseVoucher.isEnabled = true
                    binding.btnUseVoucher.text = context.getString(R.string.voucher_btn_use_now)
                    binding.btnUseVoucher.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.booktable_orange)
                    )
                    binding.btnUseVoucher.setTextColor(
                        ContextCompat.getColor(context, R.color.booktable_surface)
                    )
                    binding.btnUseVoucher.setOnClickListener { onUseClick(voucher) }
                }
            }
        }
    }
}
