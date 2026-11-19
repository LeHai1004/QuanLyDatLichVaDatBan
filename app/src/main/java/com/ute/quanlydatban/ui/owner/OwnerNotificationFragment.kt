package com.ute.quanlydatban.ui.owner

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.FragmentOwnerNotificationBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.OwnerNotificationViewModel

class OwnerNotificationFragment : Fragment() {

    private var _binding: FragmentOwnerNotificationBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(this, OwnerNotificationViewModel.Factory)[OwnerNotificationViewModel::class.java]
    }

    private lateinit var notificationAdapter: OwnerNotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOwnerNotificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupFilterChips()
        setupRecyclerView()
        setupRetryButton()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.ivBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.tvMarkAllRead.setOnClickListener {
            viewModel.markAllAsRead()
        }
    }

    private fun setupFilterChips() {
        val chips = listOf(
            binding.tvFilterAll to "all",
            binding.tvFilterUnread to "unread"
        )

        fun selectChip(targetFilter: String) {
            chips.forEach { (chipView, filterKey) ->
                val isSelected = filterKey == targetFilter
                chipView.isSelected = isSelected
                chipView.setTypeface(
                    null,
                    if (isSelected) Typeface.BOLD else Typeface.NORMAL
                )
            }
            viewModel.setFilter(targetFilter)
        }

        selectChip(viewModel.selectedFilter.value ?: "all")

        chips.forEach { (chipView, filterKey) ->
            chipView.setOnClickListener {
                selectChip(filterKey)
            }
        }
    }

    private fun setupRecyclerView() {
        notificationAdapter = OwnerNotificationAdapter { notification ->
            viewModel.onNotificationClicked(notification)
        }

        binding.rvNotifications.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = notificationAdapter
        }
    }

    private fun setupRetryButton() {
        binding.layoutStates.btnRetry.setOnClickListener {
            viewModel.loadNotifications()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) {
                UiStateHelper.showLoading(binding.layoutStates.root)
                binding.rvNotifications.visibility = View.GONE
                binding.tvEmptyNotifications.visibility = View.GONE
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                UiStateHelper.showError(binding.layoutStates.root, error)
                binding.rvNotifications.visibility = View.GONE
                binding.tvEmptyNotifications.visibility = View.GONE
                SnackbarHelper.show(binding.root, error) {
                    viewModel.loadNotifications()
                }
            }
        }

        viewModel.notifications.observe(viewLifecycleOwner) { list ->
            if (viewModel.isLoading.value == false && viewModel.errorMessage.value == null) {
                if (list.isNullOrEmpty()) {
                    UiStateHelper.hideAll(binding.layoutStates.root)
                    binding.rvNotifications.visibility = View.GONE
                    binding.tvEmptyNotifications.visibility = View.VISIBLE
                    binding.tvEmptyNotifications.text = if (viewModel.selectedFilter.value == "unread") {
                        getString(R.string.owner_notification_empty_unread)
                    } else {
                        getString(R.string.owner_notification_empty)
                    }
                    notificationAdapter.submitList(emptyList())
                } else {
                    UiStateHelper.hideAll(binding.layoutStates.root)
                    binding.tvEmptyNotifications.visibility = View.GONE
                    binding.rvNotifications.visibility = View.VISIBLE
                    notificationAdapter.submitList(list)
                }
            }
        }

        // Thông báo trỏ tới đơn đã bị xóa: báo, không văng app
        viewModel.bookingDeletedWarning.observe(viewLifecycleOwner) { warning ->
            if (warning != null) {
                SnackbarHelper.show(binding.root, warning)
                viewModel.clearNavigationEvent()
            }
        }

        viewModel.navigateToBookingId.observe(viewLifecycleOwner) { bookingId ->
            if (bookingId != null) {
                try {
                    findNavController().navigate(R.id.ownerBookingFragment)
                } catch (e: Exception) {
                    SnackbarHelper.show(binding.root, "Chi tiết đơn đặt bàn #$bookingId")
                }
                viewModel.clearNavigationEvent()
            }
        }

        viewModel.actionMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                SnackbarHelper.show(binding.root, msg)
                viewModel.clearNavigationEvent()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
