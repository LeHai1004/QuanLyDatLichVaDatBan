package com.ute.quanlydatban.ui.customer.split

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.databinding.ItemSplitPersonBinding
import com.ute.quanlydatban.util.formatMoney

class SplitPersonAdapter(
    private val onPayClick: (SplitPersonUiModel) -> Unit
) : ListAdapter<SplitPersonUiModel, SplitPersonAdapter.PersonViewHolder>(PersonDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonViewHolder {
        val binding = ItemSplitPersonBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PersonViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PersonViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PersonViewHolder(
        private val binding: ItemSplitPersonBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SplitPersonUiModel) {
            binding.tvPersonName.text = item.name
            binding.tvPersonShareAmount.text = formatMoney(item.shareAmount)

            if (item.assignedDishNames.isNotEmpty()) {
                binding.tvAssignedItems.visibility = View.VISIBLE
                binding.tvAssignedItems.text = "Món: " + item.assignedDishNames.joinToString(", ")
            } else {
                binding.tvAssignedItems.visibility = View.GONE
            }

            if (item.isPaid) {
                binding.llPaidBadge.visibility = View.VISIBLE
                binding.llPayActionRow.visibility = View.GONE
            } else {
                binding.llPaidBadge.visibility = View.GONE
                binding.llPayActionRow.visibility = View.VISIBLE

                if (item.isProcessing) {
                    binding.pbPaying.visibility = View.VISIBLE
                    binding.btnPayShare.isEnabled = false
                } else {
                    binding.pbPaying.visibility = View.GONE
                    binding.btnPayShare.isEnabled = true
                    binding.btnPayShare.setOnClickListener {
                        onPayClick(item)
                    }
                }
            }
        }
    }

    class PersonDiffCallback : DiffUtil.ItemCallback<SplitPersonUiModel>() {
        override fun areItemsTheSame(oldItem: SplitPersonUiModel, newItem: SplitPersonUiModel): Boolean {
            return oldItem.guestId == newItem.guestId
        }

        override fun areContentsTheSame(oldItem: SplitPersonUiModel, newItem: SplitPersonUiModel): Boolean {
            return oldItem == newItem
        }
    }
}
