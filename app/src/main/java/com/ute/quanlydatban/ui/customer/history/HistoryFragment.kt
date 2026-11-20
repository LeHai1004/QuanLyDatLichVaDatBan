package com.ute.quanlydatban.ui.customer.history

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.databinding.FragmentHistoryBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupTabs()
        setupListeners()
        observeViewModel()

        viewModel.loadUserBookings()
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            onChangeSchedule = { booking -> handleActionChangeSchedule(booking) },
            onCancelBooking = { booking -> handleActionCancelBooking(booking) },
            onReview = { booking -> handleActionReview(booking) },
            onReorder = { booking -> handleActionReorder(booking) }
        )
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter
    }

    private fun setupTabs() {
        binding.tabUpcoming.setOnClickListener {
            viewModel.selectTab(HistoryViewModel.TAB_UPCOMING)
        }
        binding.tabCompleted.setOnClickListener {
            viewModel.selectTab(HistoryViewModel.TAB_COMPLETED)
        }
        binding.tabCancelled.setOnClickListener {
            viewModel.selectTab(HistoryViewModel.TAB_CANCELLED)
        }
    }

    private fun setupListeners() {
        binding.layoutStates.btnRetry.setOnClickListener {
            viewModel.loadUserBookings()
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

        viewModel.restaurantsCache.observe(viewLifecycleOwner) { cache ->
            adapter.updateRestaurants(cache)
        }

        viewModel.currentTab.observe(viewLifecycleOwner) { tab ->
            updateTabStyles(tab)
            displayCurrentTabList(tab)
        }

        viewModel.upcomingBookings.observe(viewLifecycleOwner) {
            if (viewModel.currentTab.value == HistoryViewModel.TAB_UPCOMING) {
                displayCurrentTabList(HistoryViewModel.TAB_UPCOMING)
            }
        }

        viewModel.completedBookings.observe(viewLifecycleOwner) {
            if (viewModel.currentTab.value == HistoryViewModel.TAB_COMPLETED) {
                displayCurrentTabList(HistoryViewModel.TAB_COMPLETED)
            }
        }

        viewModel.cancelledBookings.observe(viewLifecycleOwner) {
            if (viewModel.currentTab.value == HistoryViewModel.TAB_CANCELLED) {
                displayCurrentTabList(HistoryViewModel.TAB_CANCELLED)
            }
        }
    }

    private fun updateTabStyles(selectedTab: Int) {
        val activeBg = R.drawable.bg_button_orange
        val activeTextColor = ContextCompat.getColor(requireContext(), R.color.white)
        val inactiveTextColor = ContextCompat.getColor(requireContext(), R.color.history_tab_text_unselected)

        binding.tabUpcoming.setBackgroundResource(if (selectedTab == HistoryViewModel.TAB_UPCOMING) activeBg else android.R.color.transparent)
        binding.tabUpcoming.setTextColor(if (selectedTab == HistoryViewModel.TAB_UPCOMING) activeTextColor else inactiveTextColor)

        binding.tabCompleted.setBackgroundResource(if (selectedTab == HistoryViewModel.TAB_COMPLETED) activeBg else android.R.color.transparent)
        binding.tabCompleted.setTextColor(if (selectedTab == HistoryViewModel.TAB_COMPLETED) activeTextColor else inactiveTextColor)

        binding.tabCancelled.setBackgroundResource(if (selectedTab == HistoryViewModel.TAB_CANCELLED) activeBg else android.R.color.transparent)
        binding.tabCancelled.setTextColor(if (selectedTab == HistoryViewModel.TAB_CANCELLED) activeTextColor else inactiveTextColor)
    }

    private fun displayCurrentTabList(tab: Int) {
        val list = when (tab) {
            HistoryViewModel.TAB_UPCOMING -> viewModel.upcomingBookings.value.orEmpty()
            HistoryViewModel.TAB_COMPLETED -> viewModel.completedBookings.value.orEmpty()
            HistoryViewModel.TAB_CANCELLED -> viewModel.cancelledBookings.value.orEmpty()
            else -> emptyList()
        }

        adapter.submitList(list)

        if (list.isEmpty()) {
            binding.llEmptyContainer.visibility = View.VISIBLE
            binding.rvHistory.visibility = View.GONE
            when (tab) {
                HistoryViewModel.TAB_UPCOMING -> {
                    binding.tvEmptyDesc.text = getString(R.string.history_empty_upcoming_desc)
                }
                HistoryViewModel.TAB_COMPLETED -> {
                    binding.tvEmptyDesc.text = getString(R.string.history_empty_completed_desc)
                }
                HistoryViewModel.TAB_CANCELLED -> {
                    binding.tvEmptyDesc.text = getString(R.string.history_empty_cancelled_desc)
                }
            }
        } else {
            binding.llEmptyContainer.visibility = View.GONE
            binding.rvHistory.visibility = View.VISIBLE
        }
    }

    private fun handleActionChangeSchedule(booking: Booking) {
        val status = BookingStatus.from(booking.status)
        if (status == BookingStatus.CHECKED_IN || status == BookingStatus.SERVING) {
            SnackbarHelper.show(binding.root, getString(R.string.history_cannot_modify_checked_in))
            return
        }

        if (isPastTime(booking.date, booking.time)) {
            SnackbarHelper.show(binding.root, getString(R.string.history_cannot_modify_past_time))
            return
        }

        val intent = Intent(requireContext(), BookingChangeActivity::class.java).apply {
            putExtra(BookingChangeActivity.EXTRA_BOOKING_ID, booking.id)
        }
        startActivity(intent)
    }

    private fun handleActionCancelBooking(booking: Booking) {
        val status = BookingStatus.from(booking.status)
        if (status == BookingStatus.CHECKED_IN || status == BookingStatus.SERVING) {
            SnackbarHelper.show(binding.root, getString(R.string.history_cannot_modify_checked_in))
            return
        }

        if (isPastTime(booking.date, booking.time)) {
            SnackbarHelper.show(binding.root, getString(R.string.history_cannot_modify_past_time))
            return
        }

        // Mở màn hình BookingChangeActivity (có tùy chọn hủy kèm xác nhận) hoặc mở dialog hủy trực tiếp
        val intent = Intent(requireContext(), BookingChangeActivity::class.java).apply {
            putExtra(BookingChangeActivity.EXTRA_BOOKING_ID, booking.id)
            putExtra(BookingChangeActivity.EXTRA_SHOW_CANCEL_DIRECT, true)
        }
        startActivity(intent)
    }

    private fun handleActionReview(booking: Booking) {
        val intent = Intent(requireContext(), ReviewActivity::class.java).apply {
            putExtra(ReviewActivity.EXTRA_BOOKING_ID, booking.id)
            putExtra(ReviewActivity.EXTRA_RESTAURANT_ID, booking.restaurantId)
            putExtra(ReviewActivity.EXTRA_BOOKING_DATE, booking.date)
            putExtra(ReviewActivity.EXTRA_BOOKING_TIME, booking.time)
            putExtra(ReviewActivity.EXTRA_TABLE_ID, booking.tableId)
        }
        startActivity(intent)
    }

    private val favoriteRepository by lazy { com.ute.quanlydatban.data.repository.FavoriteRepository() }

    private fun handleActionReorder(booking: Booking) {
        SnackbarHelper.show(binding.root, getString(R.string.history_rebook_loading))
        favoriteRepository.checkRebookEligibility(booking.id) { result ->
            result.onSuccess { rebook ->
                if (!rebook.isRestaurantValid || rebook.restaurant == null) {
                    val errMsg = rebook.statusMessage ?: getString(R.string.history_rebook_restaurant_closed)
                    SnackbarHelper.show(binding.root, errMsg)
                    return@onSuccess
                }

                if (rebook.droppedItemCount > 0 || rebook.hasPriceChanges) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        getString(R.string.history_rebook_items_changed),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }

                val res = rebook.restaurant
                val intent = Intent(requireContext(), com.ute.quanlydatban.ui.customer.booking.BookingActivity::class.java).apply {
                    putExtra(com.ute.quanlydatban.ui.customer.booking.BookingActivity.EXTRA_RESTAURANT_ID, res.id)
                    putExtra(com.ute.quanlydatban.ui.customer.booking.BookingActivity.EXTRA_RESTAURANT_NAME, res.name)
                    putExtra(com.ute.quanlydatban.ui.customer.booking.BookingActivity.EXTRA_RESTAURANT_ADDRESS, res.address)
                    putExtra(com.ute.quanlydatban.ui.customer.booking.BookingActivity.EXTRA_RESTAURANT_OPEN_TIME, res.openTime)
                    putExtra(com.ute.quanlydatban.ui.customer.booking.BookingActivity.EXTRA_RESTAURANT_CLOSE_TIME, res.closeTime)
                    putExtra(com.ute.quanlydatban.ui.customer.booking.BookingActivity.EXTRA_DEPOSIT_AMOUNT, res.depositAmount)
                    putExtra("guests", rebook.guests)
                }
                startActivity(intent)
            }.onFailure { err ->
                SnackbarHelper.show(binding.root, err.message ?: getString(R.string.history_rebook_error))
            }
        }
    }

    private fun isPastTime(dateStr: String, timeStr: String): Boolean {
        if (dateStr.isBlank() || timeStr.isBlank()) return false
        val formats = listOf("dd/MM/yyyy HH:mm", "yyyy-MM-dd HH:mm", "d/M/yyyy H:m")
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                val parsed = sdf.parse("$dateStr $timeStr")
                if (parsed != null) {
                    return System.currentTimeMillis() > parsed.time
                }
            } catch (_: Exception) {}
        }
        return false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
