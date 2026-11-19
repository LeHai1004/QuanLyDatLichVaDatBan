package com.ute.quanlydatban.ui.owner

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
        setupRetryButton()
        observeViewModel()
    }

    private fun setupQuickActions() {
        // Lối vào danh sách đơn đặt bàn
        binding.cardActionBookings.setOnClickListener {
            try {
                findNavController().navigate(R.id.ownerBookingFragment)
            } catch (e: Exception) {
                SnackbarHelper.show(binding.root, getString(R.string.owner_action_bookings_title))
            }
        }

        // Lối vào Bếp (thẻ sau)
        binding.cardActionKitchen.setOnClickListener {
            SnackbarHelper.show(binding.root, getString(R.string.owner_feature_coming_soon))
        }

        // Lối vào Yêu cầu phục vụ (thẻ sau)
        binding.cardActionService.setOnClickListener {
            SnackbarHelper.show(binding.root, getString(R.string.owner_feature_coming_soon))
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
    }

    private fun setupRetryButton() {
        binding.layoutStates.btnRetry.setOnClickListener {
            viewModel.checkAuthorizationAndLoad()
        }
    }

    private fun observeViewModel() {
        // Kiểm tra quyền chủ quán
        viewModel.isAuthorized.observe(viewLifecycleOwner) { authorized ->
            if (authorized == false) {
                val errorMsg = viewModel.unauthorizedMessage.value ?: getString(R.string.owner_error_unauthorized)
                UiStateHelper.showError(binding.layoutStates.root, errorMsg)
                binding.llDashboardBody.visibility = View.GONE
                SnackbarHelper.show(binding.root, errorMsg)
            }
        }

        viewModel.isDashboardLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) {
                UiStateHelper.showLoading(binding.layoutStates.root)
                binding.llDashboardBody.visibility = View.GONE
            }
        }

        viewModel.dashboardErrorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                UiStateHelper.showError(binding.layoutStates.root, error)
                binding.llDashboardBody.visibility = View.GONE
                SnackbarHelper.show(binding.root, error) {
                    viewModel.checkAuthorizationAndLoad()
                }
            }
        }

        viewModel.restaurant.observe(viewLifecycleOwner) { restaurant ->
            if (restaurant != null && viewModel.isDashboardLoading.value == false && viewModel.dashboardErrorMessage.value == null) {
                UiStateHelper.hideAll(binding.layoutStates.root)
                binding.llDashboardBody.visibility = View.VISIBLE
                binding.tvRestaurantName.text = restaurant.name
            }
        }

        viewModel.isOpenNow.observe(viewLifecycleOwner) { isOpen ->
            val res = viewModel.restaurant.value ?: return@observe
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

        viewModel.dashboardCounts.observe(viewLifecycleOwner) { counts ->
            binding.tvWaitingCount.text = counts.waitingCount.toString()
            binding.tvTodayCount.text = counts.todayCount.toString()
            binding.tvServingCount.text = counts.servingCount.toString()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
