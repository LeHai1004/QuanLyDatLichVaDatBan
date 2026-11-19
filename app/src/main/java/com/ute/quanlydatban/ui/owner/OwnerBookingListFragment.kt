package com.ute.quanlydatban.ui.owner

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.FragmentOwnerBookingListBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.BookingWithDetails
import com.ute.quanlydatban.viewmodel.OwnerBookingViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OwnerBookingListFragment : Fragment() {

    private var _binding: FragmentOwnerBookingListBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(requireActivity(), OwnerBookingViewModel.Factory())[OwnerBookingViewModel::class.java]
    }

    private lateinit var bookingAdapter: OwnerBookingAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOwnerBookingListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTopBar()
        setupDateFilter()
        setupStatusTabs()
        setupRecyclerView()
        setupRetryButton()
        observeViewModel()

        viewModel.loadRestaurantAndBookings()
    }

    private fun setupTopBar() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnFilterOptions.setOnClickListener {
            // Mở màn PreOrder xem danh sách món đặt trước theo ngày
            findNavController().navigate(R.id.action_ownerBookingListFragment_to_ownerPreOrderFragment)
        }
    }

    private fun setupDateFilter() {
        binding.llDateFilter.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Chọn ngày xem đơn đặt bàn")
                .build()
            picker.addOnPositiveButtonClickListener { selection ->
                val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(selection))
                viewModel.selectDate(dateStr)
            }
            picker.show(childFragmentManager, "DATE_PICKER")
        }
    }

    private fun setupStatusTabs() {
        binding.tabWaiting.setOnClickListener {
            viewModel.selectTab(OwnerBookingViewModel.TAB_WAITING)
        }
        binding.tabConfirmed.setOnClickListener {
            viewModel.selectTab(OwnerBookingViewModel.TAB_CONFIRMED)
        }
        binding.tabArrived.setOnClickListener {
            viewModel.selectTab(OwnerBookingViewModel.TAB_ARRIVED)
        }
        binding.tabCancelled.setOnClickListener {
            viewModel.selectTab(OwnerBookingViewModel.TAB_CANCELLED)
        }
    }

    private fun setupRecyclerView() {
        bookingAdapter = OwnerBookingAdapter(
            onItemClick = { item ->
                val intent = Intent(requireContext(), OwnerBookingDetailActivity::class.java).apply {
                    putExtra(OwnerBookingDetailActivity.EXTRA_BOOKING_ID, item.booking.id)
                }
                startActivity(intent)
            },
            onConfirmClick = { item ->
                viewModel.confirmBooking(item.booking.id) { success ->
                    if (success) {
                        SnackbarHelper.show(binding.root, getString(R.string.owner_booking_confirm_success))
                    }
                }
            },
            onRejectClick = { item ->
                showRejectConfirmDialog(item)
            }
        )

        binding.rvBookings.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = bookingAdapter
        }
    }

    private fun showRejectConfirmDialog(item: BookingWithDetails) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.owner_booking_reject_dialog_title))
            .setMessage(getString(R.string.owner_booking_reject_dialog_message))
            .setPositiveButton(getString(R.string.owner_booking_reject_dialog_btn_confirm)) { dialog, _ ->
                dialog.dismiss()
                viewModel.rejectBooking(item.booking.id) { success ->
                    if (success) {
                        SnackbarHelper.show(binding.root, getString(R.string.owner_booking_reject_success))
                    }
                }
            }
            .setNegativeButton(getString(R.string.owner_booking_reject_dialog_btn_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun setupRetryButton() {
        binding.layoutStates.btnRetry.setOnClickListener {
            viewModel.loadRestaurantAndBookings()
        }
    }

    private fun observeViewModel() {
        viewModel.selectedTab.observe(viewLifecycleOwner) { tab ->
            updateTabUi(tab)
        }

        viewModel.selectedDate.observe(viewLifecycleOwner) { dateStr ->
            binding.tvSelectedDate.text = getString(R.string.owner_booking_today_format, dateStr)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) {
                UiStateHelper.showLoading(binding.layoutStates.root)
                binding.rvBookings.visibility = View.GONE
            }
        }

        viewModel.filteredBookings.observe(viewLifecycleOwner) { list ->
            val loading = viewModel.isLoading.value ?: false
            if (!loading) {
                if (list.isEmpty()) {
                    UiStateHelper.showEmpty(binding.layoutStates.root)
                    binding.rvBookings.visibility = View.GONE
                } else {
                    UiStateHelper.hideAll(binding.layoutStates.root)
                    binding.rvBookings.visibility = View.VISIBLE
                    bookingAdapter.submitList(list)
                }

                // Cập nhật dòng tóm tắt số lượng
                val tab = viewModel.selectedTab.value ?: OwnerBookingViewModel.TAB_WAITING
                val tabName = when (tab) {
                    OwnerBookingViewModel.TAB_WAITING -> getString(R.string.owner_booking_tab_waiting).lowercase()
                    OwnerBookingViewModel.TAB_CONFIRMED -> getString(R.string.owner_booking_tab_confirmed).lowercase()
                    OwnerBookingViewModel.TAB_ARRIVED -> getString(R.string.owner_booking_tab_arrived).lowercase()
                    else -> getString(R.string.owner_booking_tab_cancelled).lowercase()
                }
                binding.tvQueueSummary.text = getString(R.string.owner_booking_count_format, list.size, tabName)
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                UiStateHelper.showError(binding.layoutStates.root, err)
                binding.rvBookings.visibility = View.GONE
                viewModel.clearMessages()
            }
        }

        viewModel.actionSuccessMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                SnackbarHelper.show(binding.root, msg)
                viewModel.clearMessages()
            }
        }
    }

    private fun updateTabUi(selectedTab: Int) {
        val activeColor = ContextCompat.getColor(requireContext(), R.color.booktable_active)
        val mutedColor = ContextCompat.getColor(requireContext(), R.color.booktable_muted)
        val orangeUnderline = ContextCompat.getColor(requireContext(), R.color.booktable_orange)
        val unselectedUnderline = ContextCompat.getColor(requireContext(), R.color.owner_booking_tab_unselected)

        // Reset all tabs
        binding.tvTabWaiting.setTextColor(if (selectedTab == OwnerBookingViewModel.TAB_WAITING) activeColor else mutedColor)
        binding.vTabUnderlineWaiting.setBackgroundColor(if (selectedTab == OwnerBookingViewModel.TAB_WAITING) orangeUnderline else unselectedUnderline)

        binding.tvTabConfirmed.setTextColor(if (selectedTab == OwnerBookingViewModel.TAB_CONFIRMED) activeColor else mutedColor)
        binding.vTabUnderlineConfirmed.setBackgroundColor(if (selectedTab == OwnerBookingViewModel.TAB_CONFIRMED) orangeUnderline else unselectedUnderline)

        binding.tvTabArrived.setTextColor(if (selectedTab == OwnerBookingViewModel.TAB_ARRIVED) activeColor else mutedColor)
        binding.vTabUnderlineArrived.setBackgroundColor(if (selectedTab == OwnerBookingViewModel.TAB_ARRIVED) orangeUnderline else unselectedUnderline)

        binding.tvTabCancelled.setTextColor(if (selectedTab == OwnerBookingViewModel.TAB_CANCELLED) activeColor else mutedColor)
        binding.vTabUnderlineCancelled.setBackgroundColor(if (selectedTab == OwnerBookingViewModel.TAB_CANCELLED) orangeUnderline else unselectedUnderline)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
