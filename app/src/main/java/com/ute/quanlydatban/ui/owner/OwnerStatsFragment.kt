package com.ute.quanlydatban.ui.owner

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.FragmentOwnerStatsBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.util.formatMoney
import com.ute.quanlydatban.viewmodel.OwnerDashboardViewModel

class OwnerStatsFragment : Fragment() {

    private var _binding: FragmentOwnerStatsBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(requireActivity(), OwnerDashboardViewModel.Factory)[OwnerDashboardViewModel::class.java]
    }

    private lateinit var bookingAdapter: OwnerBookingStatAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOwnerStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPeriodChips()
        setupRecyclerView()
        setupRetryButton()
        observeViewModel()

        viewModel.loadStats()
    }

    private fun setupPeriodChips() {
        val chips = listOf(
            binding.tvPeriodToday to "today",
            binding.tvPeriodWeek to "week",
            binding.tvPeriodAll to "all"
        )

        fun selectChip(targetPeriod: String) {
            chips.forEach { (chipView, period) ->
                val isSelected = period == targetPeriod
                chipView.isSelected = isSelected
                chipView.setTypeface(
                    null,
                    if (isSelected) Typeface.BOLD else Typeface.NORMAL
                )
            }
            viewModel.setStatsPeriod(targetPeriod)
        }

        selectChip(viewModel.selectedPeriod.value ?: "today")

        chips.forEach { (chipView, period) ->
            chipView.setOnClickListener {
                selectChip(period)
            }
        }
    }

    private fun setupRecyclerView() {
        bookingAdapter = OwnerBookingStatAdapter()
        binding.rvRecentBookings.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = bookingAdapter
        }
    }

    private fun setupRetryButton() {
        binding.layoutStates.btnRetry.setOnClickListener {
            viewModel.loadStats()
        }
    }

    private fun observeViewModel() {
        viewModel.isStatsLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) {
                UiStateHelper.showLoading(binding.layoutStates.root)
                binding.llStatsBody.visibility = View.GONE
            }
        }

        viewModel.statsErrorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                UiStateHelper.showError(binding.layoutStates.root, error)
                binding.llStatsBody.visibility = View.GONE
                SnackbarHelper.show(binding.root, error) {
                    viewModel.loadStats()
                }
            }
        }

        viewModel.totalRevenue.observe(viewLifecycleOwner) { revenue ->
            if (viewModel.isStatsLoading.value == false && viewModel.statsErrorMessage.value == null) {
                UiStateHelper.hideAll(binding.layoutStates.root)
                binding.llStatsBody.visibility = View.VISIBLE
                binding.tvRevenueValue.text = formatMoney(revenue)
            }
        }

        viewModel.totalOrdersCount.observe(viewLifecycleOwner) { count ->
            binding.tvTotalOrdersValue.text = getString(R.string.owner_unit_orders, count)
        }

        viewModel.completedOrdersCount.observe(viewLifecycleOwner) { count ->
            binding.tvCompletedOrdersValue.text = getString(R.string.owner_unit_orders, count)
        }

        viewModel.cancelledOrdersCount.observe(viewLifecycleOwner) { count ->
            binding.tvCancelledOrdersValue.text = getString(R.string.owner_unit_orders, count)
        }

        viewModel.filteredBookings.observe(viewLifecycleOwner) { bookings ->
            if (bookings.isNullOrEmpty()) {
                binding.tvEmptyBookings.visibility = View.VISIBLE
                binding.rvRecentBookings.visibility = View.GONE
                bookingAdapter.submitList(emptyList())
            } else {
                binding.tvEmptyBookings.visibility = View.GONE
                binding.rvRecentBookings.visibility = View.VISIBLE
                bookingAdapter.submitList(bookings)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
