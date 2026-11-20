package com.ute.quanlydatban.ui.owner.table

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ItemOwnerTableBinding
import com.ute.quanlydatban.viewmodel.OwnerTableItemUiState

class OwnerTableAdapter(
    private val onTableClick: (OwnerTableItemUiState) -> Unit
) : ListAdapter<OwnerTableItemUiState, OwnerTableAdapter.OwnerTableViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OwnerTableViewHolder {
        val binding = ItemOwnerTableBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OwnerTableViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OwnerTableViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class OwnerTableViewHolder(
        private val binding: ItemOwnerTableBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: OwnerTableItemUiState) {
            val context = binding.root.context
            val table = item.table

            binding.tvTableName.text = table.name
            binding.tvCapacity.text = context.getString(R.string.owner_table_capacity_format, table.capacity)

            // Loại không gian: phòng hay bàn
            if (table.type == "room") {
                binding.tvTypeLabel.text = context.getString(R.string.owner_table_type_room)
                binding.ivTypeIcon.setImageResource(R.drawable.ic_owner_room_vip)
                binding.llTypeTag.setBackgroundResource(R.drawable.bg_owner_table_tag_room)
            } else {
                binding.tvTypeLabel.text = context.getString(R.string.owner_table_type_table)
                binding.ivTypeIcon.setImageResource(R.drawable.ic_owner_table_normal)
                binding.llTypeTag.setBackgroundResource(R.drawable.bg_notification_tag)
            }

            // Trạng thái bàn: available, booked, serving
            when (item.currentStatus) {
                "serving" -> {
                    binding.tvTableStatus.text = context.getString(R.string.owner_table_legend_serving)
                    binding.tvTableStatus.setBackgroundResource(R.drawable.bg_history_status_serving)
                    binding.tvTableStatus.setTextColor(ContextCompat.getColor(context, R.color.owner_table_serving))
                    binding.cardTable.strokeColor = ContextCompat.getColor(context, R.color.owner_table_serving)
                    binding.llTableContainer.setBackgroundResource(R.drawable.bg_owner_table_item_serving)
                }
                "booked" -> {
                    binding.tvTableStatus.text = context.getString(R.string.owner_table_legend_booked)
                    binding.tvTableStatus.setBackgroundResource(R.drawable.bg_history_status_confirmed)
                    binding.tvTableStatus.setTextColor(ContextCompat.getColor(context, R.color.owner_table_booked))
                    binding.cardTable.strokeColor = ContextCompat.getColor(context, R.color.owner_table_booked)
                    binding.llTableContainer.setBackgroundResource(R.drawable.bg_owner_table_item_booked)
                }
                else -> {
                    binding.tvTableStatus.text = context.getString(R.string.owner_table_legend_available)
                    binding.tvTableStatus.setBackgroundResource(R.drawable.bg_history_status_completed)
                    binding.tvTableStatus.setTextColor(ContextCompat.getColor(context, R.color.owner_table_available))
                    binding.cardTable.strokeColor = ContextCompat.getColor(context, R.color.owner_table_available)
                    binding.llTableContainer.setBackgroundResource(R.drawable.bg_owner_table_item_available)
                }
            }

            // Ghi chú đơn hoặc tọa độ
            val activeBooking = item.activeBooking
            if (activeBooking != null) {
                binding.tvSubInfo.text = context.getString(
                    R.string.owner_table_booking_today_format,
                    activeBooking.time,
                    "${activeBooking.guests}k"
                )
                binding.tvSubInfo.setTextColor(
                    if (item.currentStatus == "serving")
                        ContextCompat.getColor(context, R.color.owner_table_serving)
                    else
                        ContextCompat.getColor(context, R.color.owner_table_booked)
                )
            } else {
                binding.tvSubInfo.text = "Tọa độ: (${table.mapX.toInt()}, ${table.mapY.toInt()})"
                binding.tvSubInfo.setTextColor(ContextCompat.getColor(context, R.color.booktable_muted))
            }

            binding.root.setOnClickListener {
                onTableClick(item)
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<OwnerTableItemUiState>() {
        override fun areItemsTheSame(oldItem: OwnerTableItemUiState, newItem: OwnerTableItemUiState): Boolean =
            oldItem.table.id == newItem.table.id

        override fun areContentsTheSame(oldItem: OwnerTableItemUiState, newItem: OwnerTableItemUiState): Boolean =
            oldItem == newItem
    }
}
