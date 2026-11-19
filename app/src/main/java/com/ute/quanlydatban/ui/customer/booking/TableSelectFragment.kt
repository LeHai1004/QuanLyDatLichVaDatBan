package com.ute.quanlydatban.ui.customer.booking

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.DiningTable
import com.ute.quanlydatban.databinding.FragmentTableSelectBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.BookingViewModel

class TableSelectFragment : Fragment() {

    private var _binding: FragmentTableSelectBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(requireActivity(), BookingViewModel.Factory())[BookingViewModel::class.java]
    }

    private lateinit var tableAdapter: TableAdapter
    private var restaurantId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTableSelectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        restaurantId = arguments?.getString("restaurantId")
            ?: viewModel.restaurant.value?.id.orEmpty()

        setupHeader()
        setupContextBanner()
        setupRecyclerView()
        setupConfirmButton()
        setupRetryButton()
        observeViewModel()

        viewModel.loadTables(restaurantId)
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupContextBanner() {
        val date = viewModel.selectedDate.value.orEmpty()
        val time = viewModel.selectedTime.value.orEmpty()
        val guests = viewModel.selectedGuests.value ?: 2L
        val guestsStr = getString(R.string.booking_guest_format, guests)

        binding.tvContextInfo.text = getString(
            R.string.table_select_context_format,
            date,
            time,
            guestsStr
        )
    }

    private fun setupRecyclerView() {
        tableAdapter = TableAdapter { table ->
            val success = viewModel.selectTable(table)
            if (success) {
                tableAdapter.setSelectedTableId(table.id)
            }
        }

        binding.rvTables.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = tableAdapter
        }
    }

    private fun setupFloorTabs(tables: List<DiningTable>) {
        val container = binding.llFloorTabs
        container.removeAllViews()

        val floors = tables.map { it.floor }.distinct().sorted()
        if (floors.isEmpty()) return

        val currentFloor = viewModel.selectedFloor.value ?: floors.first()
        val tabViews = mutableListOf<Pair<Long, TextView>>()

        floors.forEach { floor ->
            val label = if (floor > 1) "Tầng $floor" else "Tầng 1"
            val tab = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    resources.getDimensionPixelSize(R.dimen.booking_chip_height)
                ).apply {
                    marginEnd = resources.getDimensionPixelSize(R.dimen.table_floor_tabs_gap)
                }
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(
                    resources.getDimensionPixelSize(R.dimen.booking_chip_padding_h),
                    0,
                    resources.getDimensionPixelSize(R.dimen.booking_chip_padding_h),
                    0
                )
                setBackgroundResource(
                    if (floor == currentFloor) R.drawable.bg_booking_chip_selected
                    else R.drawable.bg_booking_chip_normal
                )
                setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (floor == currentFloor) R.color.white else R.color.booktable_muted
                    )
                )
                paint.isFakeBoldText = floor == currentFloor
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    viewModel.setFloor(floor)
                }
            }

            container.addView(tab)
            tabViews.add(floor to tab)
        }

        viewModel.selectedFloor.observe(viewLifecycleOwner) { selectedFloor ->
            tabViews.forEach { (floor, tab) ->
                val isSelected = floor == selectedFloor
                tab.setBackgroundResource(
                    if (isSelected) R.drawable.bg_booking_chip_selected
                    else R.drawable.bg_booking_chip_normal
                )
                tab.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (isSelected) R.color.white else R.color.booktable_muted
                    )
                )
                tab.paint.isFakeBoldText = isSelected
            }

            binding.tvFloorMapLabel.text = getString(
                R.string.table_select_map_label_format,
                if (selectedFloor > 1) "Tầng $selectedFloor" else "Tầng 1"
            )

            filterTablesByFloor(selectedFloor)
        }
    }

    private fun filterTablesByFloor(floor: Long) {
        val allTables = viewModel.tables.value.orEmpty()
        val filtered = allTables.filter { it.floor == floor }
        tableAdapter.submitList(filtered)
    }

    private fun setupConfirmButton() {
        binding.btnConfirmTable.setOnClickListener {
            val selectedTable = viewModel.selectedTable.value
            if (selectedTable == null) {
                SnackbarHelper.show(binding.root, getString(R.string.booking_no_selection))
                return@setOnClickListener
            }

            viewModel.confirmBooking { booking ->
                // Hiện dialog Đặt bàn thành công, không cho đóng bằng bấm ra ngoài (setCancelable(false))
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.booking_success_dialog_title))
                    .setMessage(getString(R.string.booking_success_dialog_message))
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.booking_success_dialog_btn_confirm)) { dialog, _ ->
                        dialog.dismiss()
                        val bundle = Bundle().apply {
                            putString("bookingId", booking.id)
                            putString("restaurantId", booking.restaurantId)
                            putString("restaurantName", viewModel.restaurant.value?.name)
                            putString("tableName", selectedTable.name)
                            putString("bookingDate", booking.date)
                            putString("bookingTime", booking.time)
                            putLong("bookingGuests", booking.guests)
                            putLong("depositAmount", booking.depositAmount)
                        }
                        findNavController().navigate(
                            R.id.action_tableSelectFragment_to_menuFragment,
                            bundle
                        )
                    }
                    .show()
            }
        }
    }

    private fun setupRetryButton() {
        binding.layoutStates.btnRetry.setOnClickListener {
            viewModel.loadTables(restaurantId)
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) {
                UiStateHelper.showLoading(binding.layoutStates.root)
                binding.nsvTableContent.visibility = View.GONE
            }
        }

        viewModel.tables.observe(viewLifecycleOwner) { list ->
            val loading = viewModel.isLoading.value ?: false
            val error = viewModel.errorMessage.value
            if (!loading && error == null) {
                if (list.isEmpty()) {
                    UiStateHelper.showEmpty(binding.layoutStates.root)
                    binding.nsvTableContent.visibility = View.GONE
                } else {
                    UiStateHelper.hideAll(binding.layoutStates.root)
                    binding.nsvTableContent.visibility = View.VISIBLE
                    setupFloorTabs(list)
                    filterTablesByFloor(viewModel.selectedFloor.value ?: 1L)
                }
            }
        }

        viewModel.selectedTable.observe(viewLifecycleOwner) { table ->
            tableAdapter.setSelectedTableId(table?.id)
            if (table != null) {
                binding.llSelectedTableCard.visibility = View.VISIBLE
                binding.tvSelectedTableName.text = table.name
                val typeName = if (table.type == "room") {
                    getString(R.string.table_type_room)
                } else {
                    getString(R.string.table_type_table)
                }
                binding.tvSelectedTableDetail.text = getString(
                    R.string.table_select_detail_format,
                    "Tầng ${table.floor}",
                    table.capacity.toString(),
                    typeName
                )
                binding.tvBottomHint.text = getString(R.string.table_select_bottom_hint)
            } else {
                binding.llSelectedTableCard.visibility = View.GONE
                binding.tvBottomHint.text = getString(R.string.table_select_no_table_selected_hint)
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                val tables = viewModel.tables.value
                if (tables.isNullOrEmpty()) {
                    UiStateHelper.showError(binding.layoutStates.root, error)
                    binding.nsvTableContent.visibility = View.GONE
                } else {
                    SnackbarHelper.show(binding.root, error)
                }
                viewModel.clearMessages()
            }
        }

        viewModel.conflictMessage.observe(viewLifecycleOwner) { conflict ->
            if (conflict != null) {
                SnackbarHelper.show(binding.root, conflict)
                viewModel.clearMessages()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
