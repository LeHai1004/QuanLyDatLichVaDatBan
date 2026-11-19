package com.ute.quanlydatban.ui.customer.booking

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.DiningTable
import com.ute.quanlydatban.databinding.ItemTableBinding

class TableAdapter(
    private val onTableSelected: (DiningTable) -> Unit
) : ListAdapter<DiningTable, TableAdapter.TableViewHolder>(DiffCallback) {

    private var selectedTableId: String? = null

    fun setSelectedTableId(tableId: String?) {
        val oldId = selectedTableId
        selectedTableId = tableId
        if (oldId != tableId) {
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TableViewHolder {
        val binding = ItemTableBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TableViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TableViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TableViewHolder(
        private val binding: ItemTableBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(table: DiningTable) {
            val context = itemView.context
            val isSelected = table.id == selectedTableId
            val isBooked = table.status == "booked"

            binding.tvTableName.text = table.name

            when {
                isSelected -> {
                    binding.root.setBackgroundResource(R.drawable.bg_table_selected)
                    binding.root.alpha = 1.0f
                    binding.tvTableName.setTextColor(
                        ContextCompat.getColor(context, R.color.booktable_active)
                    )
                    binding.tvTableCapacity.setTextColor(
                        ContextCompat.getColor(context, R.color.booktable_active)
                    )
                    binding.tvTableCapacity.text = context.getString(R.string.booking_guest_format, table.capacity)
                }
                isBooked -> {
                    binding.root.setBackgroundResource(R.drawable.bg_table_booked)
                    binding.root.alpha = 0.5f
                    binding.tvTableName.setTextColor(
                        ContextCompat.getColor(context, R.color.booktable_muted)
                    )
                    binding.tvTableCapacity.setTextColor(
                        ContextCompat.getColor(context, R.color.booktable_muted)
                    )
                    binding.tvTableCapacity.text = context.getString(R.string.table_select_legend_booked)
                }
                else -> {
                    binding.root.setBackgroundResource(R.drawable.bg_table_available)
                    binding.root.alpha = 1.0f
                    binding.tvTableName.setTextColor(
                        ContextCompat.getColor(context, R.color.booktable_dark)
                    )
                    binding.tvTableCapacity.setTextColor(
                        ContextCompat.getColor(context, R.color.booktable_muted)
                    )
                    binding.tvTableCapacity.text = context.getString(R.string.booking_guest_format, table.capacity)
                }
            }

            binding.root.setOnClickListener {
                onTableSelected(table)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DiningTable>() {
        override fun areItemsTheSame(oldItem: DiningTable, newItem: DiningTable): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: DiningTable, newItem: DiningTable): Boolean =
            oldItem == newItem
    }
}
