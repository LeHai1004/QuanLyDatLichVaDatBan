package com.ute.quanlydatban.ui.customer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.FragmentNotificationBinding
import com.ute.quanlydatban.ui.customer.booking.BookingQRActivity
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.NotificationViewModel

class NotificationFragment : Fragment() {

    private var _binding: FragmentNotificationBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(
            this,
            NotificationViewModel.factory(requireContext())
        )[NotificationViewModel::class.java]
    }

    private lateinit var notificationAdapter: NotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        binding.tvMarkAllRead.setOnClickListener { viewModel.markAllAsRead() }
        binding.layoutStates.btnRetry.setOnClickListener { viewModel.loadNotifications() }
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        viewModel.startListening()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopListening()
    }

    private fun setupRecyclerView() {
        notificationAdapter = NotificationAdapter { notification ->
            viewModel.markAsRead(notification)
        }
        binding.rvNotifications.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = notificationAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) {
                UiStateHelper.showLoading(binding.layoutStates.root)
                binding.rvNotifications.visibility = View.GONE
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                UiStateHelper.showError(binding.layoutStates.root, error)
                binding.rvNotifications.visibility = View.GONE
                Snackbar.make(binding.root, error, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.state_retry) { viewModel.loadNotifications() }
                    .show()
            }
        }

        viewModel.notifications.observe(viewLifecycleOwner) { notifications ->
            if (viewModel.isLoading.value == false && viewModel.errorMessage.value == null) {
                if (notifications.isNullOrEmpty()) {
                    UiStateHelper.showEmpty(binding.layoutStates.root)
                    binding.rvNotifications.visibility = View.GONE
                    notificationAdapter.submitList(emptyList())
                } else {
                    UiStateHelper.hideAll(binding.layoutStates.root)
                    binding.rvNotifications.visibility = View.VISIBLE
                    notificationAdapter.submitList(notifications)
                }
            }
        }

        viewModel.actionMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                val snackbar = Snackbar.make(binding.root, message.text, Snackbar.LENGTH_LONG)
                message.retry?.let { retry ->
                    snackbar.setAction(R.string.state_retry) { retry() }
                }
                snackbar.show()
                viewModel.clearActionMessage()
            }
        }

        viewModel.navigationRequest.observe(viewLifecycleOwner) { request ->
            if (request != null) {
                when (request.destination) {
                    NotificationViewModel.NavigationDestination.BOOKING_HISTORY ->
                        findNavController().navigate(R.id.customerHistoryFragment)
                    NotificationViewModel.NavigationDestination.QR_TICKET -> {
                        val intent = Intent(requireContext(), BookingQRActivity::class.java).apply {
                            putExtra(BookingQRActivity.EXTRA_BOOKING_ID, request.bookingId)
                        }
                        startActivity(intent)
                    }
                }
                viewModel.clearNavigationRequest()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
