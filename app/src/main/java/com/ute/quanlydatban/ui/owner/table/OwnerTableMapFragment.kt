package com.ute.quanlydatban.ui.owner.table

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.FragmentOwnerTableMapBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.OwnerTableItemUiState
import com.ute.quanlydatban.viewmodel.OwnerTableViewModel

class OwnerTableMapFragment : Fragment() {

    private var _binding: FragmentOwnerTableMapBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OwnerTableViewModel by viewModels()
    private lateinit var adapter: OwnerTableAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOwnerTableMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFloorTabs()
        setupListeners()
        observeViewModel()

        viewModel.loadData()
    }

    private fun setupRecyclerView() {
        adapter = OwnerTableAdapter { item ->
            showTableDetailDialog(item)
        }
        binding.rvOwnerTables.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvOwnerTables.adapter = adapter
    }

    private fun setupFloorTabs() {
        binding.tabFloor1.setOnClickListener {
            viewModel.selectFloor(1L)
        }
        binding.tabFloor2.setOnClickListener {
            viewModel.selectFloor(2L)
        }
    }

    private fun setupListeners() {
        binding.btnAddTable.setOnClickListener {
            openTableManageScreen(null)
        }

        binding.btnEmptyAdd.setOnClickListener {
            openTableManageScreen(null)
        }

        binding.layoutStates.btnRetry.setOnClickListener {
            viewModel.loadData()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading && adapter.currentList.isEmpty()) {
                UiStateHelper.showLoading(binding.layoutStates.root)
            } else if (!loading && viewModel.errorMessage.value == null) {
                UiStateHelper.hideAll(binding.layoutStates.root)
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrBlank()) {
                if (adapter.currentList.isEmpty()) {
                    UiStateHelper.showError(binding.layoutStates.root, error)
                } else {
                    SnackbarHelper.show(binding.root, error)
                }
            }
        }

        viewModel.selectedFloor.observe(viewLifecycleOwner) { floor ->
            updateFloorTabStyles(floor)
        }

        viewModel.displayTables.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)

            if (list.isEmpty()) {
                binding.llEmptyContainer.visibility = View.VISIBLE
                binding.rvOwnerTables.visibility = View.GONE
            } else {
                binding.llEmptyContainer.visibility = View.GONE
                binding.rvOwnerTables.visibility = View.VISIBLE
            }
        }
    }

    private fun updateFloorTabStyles(selectedFloor: Long) {
        val activeBg = R.drawable.bg_button_orange
        val activeTextColor = ContextCompat.getColor(requireContext(), R.color.white)
        val inactiveTextColor = ContextCompat.getColor(requireContext(), R.color.history_tab_text_unselected)

        if (selectedFloor == 1L) {
            binding.tabFloor1.setBackgroundResource(activeBg)
            binding.tabFloor1.setTextColor(activeTextColor)
            binding.tabFloor2.setBackgroundResource(android.R.color.transparent)
            binding.tabFloor2.setTextColor(inactiveTextColor)
        } else {
            binding.tabFloor1.setBackgroundResource(android.R.color.transparent)
            binding.tabFloor1.setTextColor(inactiveTextColor)
            binding.tabFloor2.setBackgroundResource(activeBg)
            binding.tabFloor2.setTextColor(activeTextColor)
        }
    }

    private fun showTableDetailDialog(item: OwnerTableItemUiState) {
        val table = item.table
        val booking = item.activeBooking

        val statusText = when (item.currentStatus) {
            "serving" -> getString(R.string.owner_table_legend_serving)
            "booked" -> getString(R.string.owner_table_legend_booked)
            else -> getString(R.string.owner_table_legend_available)
        }

        val messageBuilder = StringBuilder()
        messageBuilder.append(getString(R.string.owner_table_dialog_status_label, statusText))
        messageBuilder.append("\n${getString(R.string.owner_table_capacity_format, table.capacity)}")
        messageBuilder.append("\nLoại: ${if (table.type == "room") "Phòng riêng/VIP" else "Bàn thường"}")
        messageBuilder.append("\nTọa độ bản đồ: (${table.mapX}, ${table.mapY})")

        if (booking != null) {
            messageBuilder.append("\n\n")
            messageBuilder.append(getString(
                R.string.owner_table_dialog_booking_info,
                booking.time,
                booking.status,
                booking.guests
            ))
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.owner_table_dialog_title, table.name))
            .setMessage(messageBuilder.toString())
            .setPositiveButton(R.string.owner_table_dialog_btn_edit) { _, _ ->
                openTableManageScreen(table.id)
            }
            .setNegativeButton(R.string.owner_table_dialog_btn_close, null)
            .show()
    }

    private fun openTableManageScreen(tableId: String?) {
        val restaurantId = viewModel.restaurant.value?.id.orEmpty()
        if (restaurantId.isBlank()) {
            SnackbarHelper.show(binding.root, "Đang tải thông tin quán, vui lòng thử lại sau giây lát.")
            return
        }

        val intent = Intent(requireContext(), OwnerTableManageActivity::class.java).apply {
            putExtra(OwnerTableManageActivity.EXTRA_RESTAURANT_ID, restaurantId)
            putExtra(OwnerTableManageActivity.EXTRA_TABLE_ID, tableId)
            putExtra(OwnerTableManageActivity.EXTRA_DEFAULT_FLOOR, viewModel.selectedFloor.value ?: 1L)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
