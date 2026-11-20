package com.ute.quanlydatban.ui.customer.booking

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.ServiceRequest
import com.ute.quanlydatban.data.model.ServiceRequestStatus
import com.ute.quanlydatban.data.model.ServiceRequestType
import com.ute.quanlydatban.databinding.ItemCustomerServiceRequestBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CustomerServiceRequestAdapter : RecyclerView.Adapter<CustomerServiceRequestAdapter.CustomerServiceRequestViewHolder>() {

    private val items = mutableListOf<ServiceRequest>()

    fun submitList(list: List<ServiceRequest>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomerServiceRequestViewHolder {
        val binding = ItemCustomerServiceRequestBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CustomerServiceRequestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CustomerServiceRequestViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CustomerServiceRequestViewHolder(
        private val binding: ItemCustomerServiceRequestBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ServiceRequest) {
            val context = binding.root.context

            binding.tvType.text = when (item.type) {
                ServiceRequestType.WATER -> context.getString(R.string.service_request_btn_water)
                ServiceRequestType.BILL -> context.getString(R.string.service_request_btn_bill)
                else -> context.getString(R.string.service_request_btn_call_staff)
            }

            val timeStr = if (item.createdAt > 0) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.createdAt))
            } else "Vừa xong"
            binding.tvTime.text = timeStr

            if (item.status == ServiceRequestStatus.HANDLED) {
                binding.tvStatus.text = context.getString(R.string.service_request_status_handled)
                binding.tvStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.order_status_served_bg))
                binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.order_status_served_text))
            } else {
                binding.tvStatus.text = context.getString(R.string.service_request_pending_waiting)
                binding.tvStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.order_status_cooking_bg))
                binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.order_status_cooking_text))
            }
        }
    }
}
