package com.ute.quanlydatban.ui.customer.booking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.ServiceRequestStatus
import com.ute.quanlydatban.data.model.ServiceRequestType
import com.ute.quanlydatban.data.repository.BookingRepository
import com.ute.quanlydatban.databinding.DialogServiceRequestBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.viewmodel.CustomerServiceRequestViewModel

class CustomerServiceRequestBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogServiceRequestBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(this, CustomerServiceRequestViewModel.Factory())[CustomerServiceRequestViewModel::class.java]
    }

    private lateinit var adapter: CustomerServiceRequestAdapter

    private var bookingId: String = ""
    private var restaurantId: String = ""
    private var currentBooking: Booking? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogServiceRequestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initArgs()
        setupUI()
        observeViewModel()
        loadBookingDetails()
    }

    private fun initArgs() {
        bookingId = arguments?.getString(ARG_BOOKING_ID).orEmpty()
        restaurantId = arguments?.getString(ARG_RESTAURANT_ID).orEmpty()
    }

    private fun loadBookingDetails() {
        if (bookingId.isBlank()) return
        BookingRepository().get(bookingId) { result ->
            result.onSuccess { b ->
                currentBooking = b
            }
        }
    }

    private fun setupUI() {
        adapter = CustomerServiceRequestAdapter()
        binding.rvCustomerRequests.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCustomerRequests.adapter = adapter

        binding.btnRequestCall.setOnClickListener {
            sendRequest(ServiceRequestType.SERVICE)
        }

        binding.btnRequestWater.setOnClickListener {
            sendRequest(ServiceRequestType.WATER)
        }

        binding.btnRequestBill.setOnClickListener {
            sendRequest(ServiceRequestType.BILL)
        }
    }

    private fun sendRequest(type: String) {
        val b = currentBooking ?: Booking(id = bookingId, restaurantId = restaurantId)
        viewModel.sendRequest(b, type)
    }

    private fun observeViewModel() {
        viewModel.requests.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)

            val billReq = list.find { it.type == ServiceRequestType.BILL }
            val isBillRequested = billReq != null

            if (isBillRequested) {
                // Đã yêu cầu tính tiền (dù pending hay handled): Khóa cả 3 nút!
                binding.btnRequestCall.isEnabled = false
                binding.btnRequestCall.text = getString(R.string.service_request_btn_call_staff)

                binding.btnRequestWater.isEnabled = false
                binding.btnRequestWater.text = getString(R.string.service_request_btn_water)

                binding.btnRequestBill.isEnabled = false
                if (billReq.status == ServiceRequestStatus.PENDING) {
                    binding.btnRequestBill.text = getString(R.string.service_request_pending_waiting)
                } else {
                    binding.btnRequestBill.text = getString(R.string.service_request_bill_handled)
                }
            } else {
                val pendingTypes = list.filter { it.status == ServiceRequestStatus.PENDING }.map { it.type }

                updateButtonState(binding.btnRequestCall, ServiceRequestType.SERVICE, pendingTypes.contains(ServiceRequestType.SERVICE))
                updateButtonState(binding.btnRequestWater, ServiceRequestType.WATER, pendingTypes.contains(ServiceRequestType.WATER))
                updateButtonState(binding.btnRequestBill, ServiceRequestType.BILL, pendingTypes.contains(ServiceRequestType.BILL))
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { err ->
            if (!err.isNullOrBlank()) {
                SnackbarHelper.show(binding.root, err)
                viewModel.clearMessages()
            }
        }

        viewModel.successMessage.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrBlank()) {
                SnackbarHelper.show(binding.root, msg)
                viewModel.clearMessages()
            }
        }
    }

    private fun updateButtonState(button: com.google.android.material.button.MaterialButton, defaultTextRes: String, isPending: Boolean) {
        if (isPending) {
            button.isEnabled = false
            button.text = getString(R.string.service_request_pending_waiting)
        } else {
            button.isEnabled = true
            button.text = when (defaultTextRes) {
                ServiceRequestType.WATER -> getString(R.string.service_request_btn_water)
                ServiceRequestType.BILL -> getString(R.string.service_request_btn_bill)
                else -> getString(R.string.service_request_btn_call_staff)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (bookingId.isNotBlank()) {
            viewModel.startListening(bookingId)
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopListening()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_BOOKING_ID = "arg_booking_id"
        private const val ARG_RESTAURANT_ID = "arg_restaurant_id"

        fun newInstance(bookingId: String, restaurantId: String): CustomerServiceRequestBottomSheet {
            return CustomerServiceRequestBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_BOOKING_ID, bookingId)
                    putString(ARG_RESTAURANT_ID, restaurantId)
                }
            }
        }
    }
}
