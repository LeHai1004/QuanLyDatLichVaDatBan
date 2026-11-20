package com.ute.quanlydatban.ui.customer.loyalty

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.LoyaltyTransaction
import com.ute.quanlydatban.databinding.ItemLoyaltyTransactionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoyaltyTransactionAdapter :
    ListAdapter<LoyaltyTransaction, LoyaltyTransactionAdapter.LoyaltyTxViewHolder>(DiffCallback) {

    private val sdf = SimpleDateFormat("HH:mm · dd/MM/yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LoyaltyTxViewHolder {
        val binding = ItemLoyaltyTransactionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LoyaltyTxViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LoyaltyTxViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LoyaltyTxViewHolder(
        private val binding: ItemLoyaltyTransactionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LoyaltyTransaction) {
            val context = itemView.context
            binding.tvTxReason.text = item.reason.ifBlank {
                if (item.points >= 0) context.getString(R.string.loyalty_tx_earned_default)
                else context.getString(R.string.loyalty_tx_redeem_default)
            }

            if (item.createdAt > 0L) {
                binding.tvTxTime.text = sdf.format(Date(item.createdAt))
            } else {
                binding.tvTxTime.text = ""
            }

            if (item.points >= 0) {
                binding.tvTxPoints.text = "+${item.points} điểm"
                binding.tvTxPoints.setTextColor(ContextCompat.getColor(context, R.color.loyalty_tx_plus))
                binding.ivTxIcon.setImageResource(R.drawable.ic_point_plus)
                binding.flTxIconBg.setBackgroundResource(R.drawable.bg_status_badge_open)
            } else {
                binding.tvTxPoints.text = "${item.points} điểm"
                binding.tvTxPoints.setTextColor(ContextCompat.getColor(context, R.color.loyalty_tx_minus))
                binding.ivTxIcon.setImageResource(R.drawable.ic_point_minus)
                binding.flTxIconBg.setBackgroundResource(R.drawable.bg_status_badge_closed)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<LoyaltyTransaction>() {
        override fun areItemsTheSame(oldItem: LoyaltyTransaction, newItem: LoyaltyTransaction): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: LoyaltyTransaction, newItem: LoyaltyTransaction): Boolean =
            oldItem == newItem
    }
}
