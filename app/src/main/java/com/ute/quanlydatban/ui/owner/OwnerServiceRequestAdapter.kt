package com.ute.quanlydatban.ui.owner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.ServiceRequestStatus
import com.ute.quanlydatban.data.model.ServiceRequestType
import com.ute.quanlydatban.databinding.ItemServiceRequestBinding
import com.ute.quanlydatban.viewmodel.OwnerServiceRequestDisplay

class OwnerServiceRequestAdapter(
    private val onHandledClick: (OwnerServiceRequestDisplay) -> Unit
) : RecyclerView.Adapter<OwnerServiceRequestAdapter.OwnerServiceRequestViewHolder>() {

    private val items = mutableListOf<OwnerServiceRequestDisplay>()

    fun submitList(list: List<OwnerServiceRequestDisplay>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OwnerServiceRequestViewHolder {
        val binding = ItemServiceRequestBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OwnerServiceRequestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OwnerServiceRequestViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class OwnerServiceRequestViewHolder(
        private val binding: ItemServiceRequestBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: OwnerServiceRequestDisplay) {
            val context = binding.root.context
            val req = item.request

            binding.tvTableName.text = item.tableName.ifEmpty { "Bàn chung" }
            binding.tvWaitingTime.text = context.getString(R.string.owner_service_request_waiting_format, item.waitingMinutes)

            binding.tvType.text = when (req.type) {
                ServiceRequestType.WATER -> context.getString(R.string.service_request_btn_water)
                ServiceRequestType.BILL -> context.getString(R.string.service_request_btn_bill)
                else -> context.getString(R.string.service_request_btn_call_staff)
            }

            if (req.status == ServiceRequestStatus.HANDLED) {
                binding.tvStatus.text = context.getString(R.string.service_request_status_handled)
                binding.tvStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.order_status_served_bg))
                binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.order_status_served_text))
                binding.btnHandled.visibility = View.GONE
            } else {
                binding.tvStatus.text = context.getString(R.string.service_request_status_pending)
                binding.tvStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.order_status_cooking_bg))
                binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.order_status_cooking_text))

                binding.btnHandled.visibility = View.VISIBLE
                binding.btnHandled.isEnabled = true
                binding.btnHandled.setOnClickListener {
                    binding.btnHandled.isEnabled = false
                    onHandledClick(item)
                }
            }
        }
    }
}
