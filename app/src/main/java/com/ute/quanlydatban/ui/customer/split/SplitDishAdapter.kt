package com.ute.quanlydatban.ui.customer.split

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.data.model.OrderItem
import com.ute.quanlydatban.databinding.ItemSplitDishBinding
import com.ute.quanlydatban.util.formatMoney

class SplitDishAdapter(
    private val onAssignChanged: (String, Int) -> Unit
) : RecyclerView.Adapter<SplitDishAdapter.DishViewHolder>() {

    private var items: List<OrderItem> = emptyList()
    private var personNames: List<String> = emptyList()
    private var assignments: Map<String, Int> = emptyMap()

    fun submitData(
        newItems: List<OrderItem>,
        newPersonNames: List<String>,
        newAssignments: Map<String, Int>
    ) {
        items = newItems
        personNames = newPersonNames
        assignments = newAssignments
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DishViewHolder {
        val binding = ItemSplitDishBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DishViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DishViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class DishViewHolder(
        private val binding: ItemSplitDishBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: OrderItem) {
            binding.tvDishName.text = item.name
            binding.tvDishQtyPrice.text = "${item.qty} x ${formatMoney(item.unitPrice)}"
            val total = item.qty * item.unitPrice
            binding.tvDishTotal.text = formatMoney(total)

            val context = binding.root.context
            val spinnerAdapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                personNames
            )
            binding.spAssignPerson.adapter = spinnerAdapter

            val selectedIndex = assignments[item.id] ?: 0
            if (selectedIndex in personNames.indices) {
                binding.spAssignPerson.setSelection(selectedIndex)
            }

            binding.spAssignPerson.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (assignments[item.id] != position) {
                        onAssignChanged(item.id, position)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }
}
