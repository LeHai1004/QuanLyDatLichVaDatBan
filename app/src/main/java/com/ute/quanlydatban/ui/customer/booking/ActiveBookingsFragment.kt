package com.ute.quanlydatban.ui.customer.booking

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.FragmentActiveBookingsBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.ActiveBookingsViewModel

class ActiveBookingsFragment : Fragment() {

    private var _binding: FragmentActiveBookingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(this, ActiveBookingsViewModel.Factory())[ActiveBookingsViewModel::class.java]
    }

    private lateinit var adapter: ActiveBookingsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActiveBookingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
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
        adapter = ActiveBookingsAdapter(
            onCardClick = { item ->
                val intent = Intent(requireContext(), BookingQRActivity::class.java).apply {
                    putExtra(BookingQRActivity.EXTRA_BOOKING_ID, item.booking.id)
                    putExtra(BookingQRActivity.EXTRA_RESTAURANT_ID, item.booking.restaurantId)
                }
                startActivity(intent)
            },
            onCheckInClick = { item ->
                val intent = Intent(requireContext(), QRScanActivity::class.java).apply {
                    putExtra(QRScanActivity.EXTRA_BOOKING_ID, item.booking.id)
                    putExtra(QRScanActivity.EXTRA_RESTAURANT_ID, item.booking.restaurantId)
                }
                startActivity(intent)
            },
            onViewMapClick = { item ->
                val intent = Intent(requireContext(), IndoorMapPreviewActivity::class.java).apply {
                    putExtra(IndoorMapPreviewActivity.EXTRA_BOOKING_ID, item.booking.id)
                    putExtra(IndoorMapPreviewActivity.EXTRA_RESTAURANT_ID, item.booking.restaurantId)
                }
                startActivity(intent)
            },
            onNavigateMapClick = { item ->
                val intent = Intent(requireContext(), IndoorMapActivity::class.java).apply {
                    putExtra(IndoorMapActivity.EXTRA_BOOKING_ID, item.booking.id)
                    putExtra(IndoorMapActivity.EXTRA_RESTAURANT_ID, item.booking.restaurantId)
                }
                startActivity(intent)
            },
            onTableOrderClick = { item ->
                val intent = Intent(requireContext(), TableQRScanActivity::class.java).apply {
                    putExtra(TableQRScanActivity.EXTRA_BOOKING_ID, item.booking.id)
                    putExtra(TableQRScanActivity.EXTRA_RESTAURANT_ID, item.booking.restaurantId)
                }
                startActivity(intent)
            },
            onTrackOrderClick = { item ->
                val intent = Intent(requireContext(), OrderTrackingActivity::class.java).apply {
                    putExtra(OrderTrackingActivity.EXTRA_BOOKING_ID, item.booking.id)
                    putExtra(OrderTrackingActivity.EXTRA_RESTAURANT_ID, item.booking.restaurantId)
                }
                startActivity(intent)
            }
        )

        binding.rvActiveBookings.layoutManager = LinearLayoutManager(requireContext())
        binding.rvActiveBookings.adapter = adapter
    }

    private fun setupListeners() {
        // Retry / Empty Action: Chuyển sang Tab Trang chủ
        binding.layoutStates.btnRetry.setOnClickListener {
            if (viewModel.activeBookings.value.isNullOrEmpty()) {
                findNavController().navigate(R.id.customerHomeFragment)
            } else {
                viewModel.loadActiveBookings()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) {
                UiStateHelper.showLoading(binding.layoutStates.root)
            } else {
                UiStateHelper.hideAll(binding.layoutStates.root)
            }
        }

        viewModel.activeBookings.observe(viewLifecycleOwner) { list ->
            if (list.isEmpty() && viewModel.isLoading.value == false) {
                UiStateHelper.showEmpty(binding.layoutStates.root)
                binding.layoutStates.btnRetry.text = getString(R.string.active_bookings_btn_find_restaurant)
            } else if (viewModel.isLoading.value == false) {
                UiStateHelper.hideAll(binding.layoutStates.root)
            }
            adapter.submitList(list)
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { err ->
            if (!err.isNullOrBlank()) {
                SnackbarHelper.show(binding.root, err) {
                    viewModel.loadActiveBookings()
                }
                viewModel.clearError()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
