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
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.FragmentOwnerDashboardBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.OwnerDashboardViewModel

class OwnerDashboardFragment : Fragment() {

    private var _binding: FragmentOwnerDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(requireActivity(), OwnerDashboardViewModel.Factory)[OwnerDashboardViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOwnerDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupQuickActions()
        // Thêm nút thông báo
        binding.ivNotifications.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_ownerOverviewFragment_to_ownerNotificationFragment)
            } catch (e: Exception) {
                SnackbarHelper.show(binding.root, getString(R.string.owner_action_notifications_title))
            }
        }
        setupRetryButton()
        observeViewModel()
    }

    private fun setupQuickActions() {
        // Thẻ "Chờ xác nhận" (thống kê) chuyển sang màn Lượt đặt bàn
        binding.cardWaitingOrders.setOnClickListener {
            try {
                findNavController().navigate(R.id.ownerBookingFragment)
            } catch (e: Exception) {
                SnackbarHelper.show(binding.root, getString(R.string.owner_action_bookings_title))
            }
        }

        // Lối vào danh sách đơn đặt bàn
        binding.cardActionBookings.setOnClickListener {
            try {
                findNavController().navigate(R.id.ownerBookingFragment)
            } catch (e: Exception) {
                SnackbarHelper.show(binding.root, getString(R.string.owner_action_bookings_title))
            }
        }

        // Lối vào Bếp & Điều phối món (T27)
        binding.cardActionKitchen.setOnClickListener {
            try {
                findNavController().navigate(R.id.ownerKitchenFragment)
            } catch (e: Exception) {
                SnackbarHelper.show(binding.root, getString(R.string.owner_kitchen_title))
            }
        }

        // Lối vào Yêu cầu phục vụ (T28)
        binding.cardActionService.setOnClickListener {
            try {
                findNavController().navigate(R.id.ownerServiceRequestFragment)
            } catch (e: Exception) {
                SnackbarHelper.show(binding.root, getString(R.string.owner_service_request_title))
            }
        }

        // Lối vào Báo cáo doanh thu & Thống kê
        binding.cardActionStats.setOnClickListener {
            try {
                findNavController().navigate(R.id.ownerHistoryFragment)
            } catch (e: Exception) {
                SnackbarHelper.show(binding.root, getString(R.string.owner_action_stats_title))
            }
        }

        // Lối vào Thông tin quán / Hồ sơ
        binding.cardActionProfile.setOnClickListener {
            try {
                findNavController().navigate(R.id.ownerProfileFragment)
            } catch (e: Exception) {
                SnackbarHelper.show(binding.root, getString(R.string.owner_action_profile_title))
            }
        }

        // Lối vào Danh sách chờ (T32)
        binding.cardActionWaitlist.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_ownerOverviewFragment_to_ownerWaitlistFragment)
            } catch (e: Exception) {
                SnackbarHelper.show(binding.root, getString(R.string.waitlist_owner_action_title))
            }
        }
    }

    private fun setupRetryButton() {
        binding.layoutStates.btnRetry.setOnClickListener {
            viewModel.checkAuthorizationAndLoad()
        }
    }

    private fun observeViewModel() {
        // Kiểm tra quyền chủ quán
        viewModel.isAuthorized.observe(viewLifecycleOwner) { authorized ->
            updateUiState()
            if (authorized == false) {
                val errorMsg = viewModel.unauthorizedMessage.value ?: getString(R.string.owner_error_unauthorized)
                SnackbarHelper.show(binding.root, errorMsg)
            }
        }

        viewModel.isDashboardLoading.observe(viewLifecycleOwner) {
            updateUiState()
        }

        viewModel.dashboardErrorMessage.observe(viewLifecycleOwner) { error ->
            updateUiState()
            if (error != null) {
                SnackbarHelper.show(binding.root, error) {
                    viewModel.checkAuthorizationAndLoad()
                }
            }
        }

        viewModel.restaurant.observe(viewLifecycleOwner) { restaurant ->
            updateUiState()
            if (restaurant != null) {
                binding.tvRestaurantName.text = restaurant.name
                updateOpenStatusBadge(viewModel.isOpenNow.value ?: true)
            }
        }

        viewModel.isOpenNow.observe(viewLifecycleOwner) { isOpen ->
            updateOpenStatusBadge(isOpen)
        }

        viewModel.dashboardCounts.observe(viewLifecycleOwner) { counts ->
            binding.tvWaitingCount.text = (counts?.waitingCount ?: 0L).toString()
            binding.tvTodayCount.text = (counts?.todayCount ?: 0L).toString()
            binding.tvServingCount.text = (counts?.servingCount ?: 0L).toString()
        }
    }

    private fun updateUiState() {
        val isAuthorized = viewModel.isAuthorized.value
        val isLoading = viewModel.isDashboardLoading.value ?: false
        val error = viewModel.dashboardErrorMessage.value
        val restaurant = viewModel.restaurant.value

        when {
            isAuthorized == false -> {
                val errorMsg = viewModel.unauthorizedMessage.value ?: getString(R.string.owner_error_unauthorized)
                UiStateHelper.showError(binding.layoutStates.root, errorMsg)
                binding.llDashboardBody.visibility = View.GONE
            }
            isLoading -> {
                UiStateHelper.showLoading(binding.layoutStates.root)
                binding.llDashboardBody.visibility = View.GONE
            }
            error != null -> {
                UiStateHelper.showError(binding.layoutStates.root, error)
                binding.llDashboardBody.visibility = View.GONE
            }
            restaurant == null -> {
                UiStateHelper.showEmpty(binding.layoutStates.root)
                binding.llDashboardBody.visibility = View.GONE
            }
            else -> {
                UiStateHelper.hideAll(binding.layoutStates.root)
                binding.llDashboardBody.visibility = View.VISIBLE
                binding.tvRestaurantName.text = restaurant.name
            }
        }
    }

    private fun updateOpenStatusBadge(isOpen: Boolean) {
        val res = viewModel.restaurant.value ?: return
        if (isOpen) {
            binding.tvRestaurantStatus.text = getString(
                R.string.owner_status_open_now,
                res.openTime,
                res.closeTime
            )
            binding.tvRestaurantStatus.setBackgroundResource(R.drawable.bg_owner_status_open)
            binding.tvRestaurantStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.owner_stat_serving)
            )
        } else {
            binding.tvRestaurantStatus.text = getString(
                R.string.owner_status_closed_now,
                res.openTime
            )
            binding.tvRestaurantStatus.setBackgroundResource(R.drawable.bg_owner_status_closed)
            binding.tvRestaurantStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.booktable_error)
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
