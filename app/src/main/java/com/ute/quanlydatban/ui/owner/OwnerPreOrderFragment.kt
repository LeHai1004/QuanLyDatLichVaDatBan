package com.ute.quanlydatban.ui.owner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.FragmentOwnerPreOrderBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.OwnerBookingViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OwnerPreOrderFragment : Fragment() {

    private var _binding: FragmentOwnerPreOrderBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(requireActivity(), OwnerBookingViewModel.Factory())[OwnerBookingViewModel::class.java]
    }

    private lateinit var preOrderAdapter: OwnerPreOrderAdapter

    private var bookingId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookingId = arguments?.getString(OwnerPreOrderActivity.ARG_BOOKING_ID).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOwnerPreOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTopBar()
        setupDateFilter()
        setupRecyclerView()
        setupRetryButton()
        observeViewModel()

        if (bookingId.isNotBlank()) {
            // Khi vào từ màn Chi tiết (có bookingId): chỉ hiện món của đơn đó, ưu tiên theo bookingId, bỏ qua lọc ngày
            binding.llDateFilter.visibility = View.GONE
            viewModel.loadPreOrdersForDate(targetBookingId = bookingId)
        } else {
            binding.llDateFilter.visibility = View.VISIBLE
            viewModel.loadRestaurantAndBookings()
        }
    }

    private fun setupTopBar() {
        binding.btnBack.setOnClickListener {
            // Nút quay lại: nếu có host Activity thì finish(), nếu trong Navigation graph thì navigateUp()
            val hostActivity = activity
            if (hostActivity is OwnerPreOrderActivity) {
                hostActivity.finish()
            } else {
                try {
                    findNavController().navigateUp()
                } catch (e: Exception) {
                    hostActivity?.finish()
                }
            }
        }

        binding.btnFilterOptions.setOnClickListener {
            SnackbarHelper.show(binding.root, getString(R.string.feature_coming_soon))
        }
    }

    private fun setupDateFilter() {
        binding.llDateFilter.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Chọn ngày xem món đặt trước")
                .build()
            picker.addOnPositiveButtonClickListener { selection ->
                val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(selection))
                viewModel.selectDate(dateStr)
            }
            picker.show(childFragmentManager, "DATE_PICKER_PREORDER")
        }
    }

    private fun setupRecyclerView() {
        preOrderAdapter = OwnerPreOrderAdapter(
            onDonePreparingClick = { item ->
                SnackbarHelper.show(binding.root, "Đã cập nhật trạng thái món ăn cho đơn #${item.booking.id.takeLast(4)}")
            }
        )

        binding.rvPreOrders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = preOrderAdapter
        }
    }

    private fun setupRetryButton() {
        binding.layoutStates.btnRetry.setOnClickListener {
            if (bookingId.isNotBlank()) {
                viewModel.loadPreOrdersForDate(targetBookingId = bookingId)
            } else {
                viewModel.loadRestaurantAndBookings()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.selectedDate.observe(viewLifecycleOwner) { dateStr ->
            binding.tvSelectedDate.text = getString(R.string.owner_booking_today_format, dateStr)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) {
                UiStateHelper.showLoading(binding.layoutStates.root)
                binding.rvPreOrders.visibility = View.GONE
            }
        }

        viewModel.preOrderBookings.observe(viewLifecycleOwner) { bookingsWithPreOrder ->
            val loading = viewModel.isLoading.value ?: false
            if (!loading) {
                if (bookingsWithPreOrder.isNullOrEmpty()) {
                    UiStateHelper.showEmpty(binding.layoutStates.root)
                    binding.rvPreOrders.visibility = View.GONE
                    binding.tvPreOrderSummary.text = getString(
                        R.string.owner_preorder_summary_format,
                        0,
                        0
                    )
                } else {
                    UiStateHelper.hideAll(binding.layoutStates.root)
                    binding.rvPreOrders.visibility = View.VISIBLE
                    preOrderAdapter.submitList(bookingsWithPreOrder)

                    val totalDishesCount = bookingsWithPreOrder.sumOf { b -> b.preOrderItems.sumOf { it.qty } }
                    binding.tvPreOrderSummary.text = getString(
                        R.string.owner_preorder_summary_format,
                        bookingsWithPreOrder.size,
                        totalDishesCount
                    )
                }
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                UiStateHelper.showError(binding.layoutStates.root, err)
                binding.rvPreOrders.visibility = View.GONE
                viewModel.clearMessages()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
