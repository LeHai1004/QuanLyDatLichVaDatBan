package com.ute.quanlydatban.ui.customer.voucher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ute.quanlydatban.databinding.FragmentVoucherBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.VoucherViewModel

class VoucherFragment : Fragment() {

    private var _binding: FragmentVoucherBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(this, VoucherViewModel.Factory())[VoucherViewModel::class.java]
    }

    private lateinit var adapter: VoucherAdapter

    private var restaurantId: String = ""
    private var totalBill: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVoucherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        restaurantId = arguments?.getString(ARG_RESTAURANT_ID).orEmpty()
        totalBill = arguments?.getLong(ARG_TOTAL_BILL) ?: 0L

        setupUI()
        observeViewModel()

        viewModel.loadVouchers(restaurantId)
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            goBack()
        }

        adapter = VoucherAdapter { voucher ->
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    RESULT_VOUCHER_ID to voucher.id,
                    RESULT_VOUCHER_CODE to voucher.code
                )
            )
            goBack()
        }

        binding.rvVouchers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvVouchers.adapter = adapter

        binding.btnApply.setOnClickListener {
            val code = binding.etCode.text?.toString().orEmpty()
            if (code.isBlank()) {
                SnackbarHelper.show(binding.root, "Vui lòng nhập mã ưu đãi.")
                return@setOnClickListener
            }
            viewModel.applyCode(code, totalBill, restaurantId)
        }

        binding.layoutStates.btnRetry.setOnClickListener {
            viewModel.loadVouchers(restaurantId)
        }
    }

    private fun goBack() {
        if (parentFragmentManager.backStackEntryCount > 0) {
            parentFragmentManager.popBackStack()
        } else {
            try {
                findNavController().navigateUp()
            } catch (_: Exception) {
                activity?.finish()
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

        viewModel.vouchers.observe(viewLifecycleOwner) { list ->
            if (list.isEmpty() && viewModel.isLoading.value == false) {
                UiStateHelper.showEmpty(binding.layoutStates.root)
            } else if (viewModel.isLoading.value == false) {
                UiStateHelper.hideAll(binding.layoutStates.root)
            }
            adapter.submitList(list)
        }

        viewModel.appliedVoucher.observe(viewLifecycleOwner) { voucher ->
            if (voucher != null) {
                SnackbarHelper.show(binding.root, "Đã áp dụng mã ${voucher.code}")
                setFragmentResult(
                    REQUEST_KEY,
                    bundleOf(
                        RESULT_VOUCHER_ID to voucher.id,
                        RESULT_VOUCHER_CODE to voucher.code
                    )
                )
                goBack()
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                SnackbarHelper.show(binding.root, err)
                viewModel.clearMessages()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val REQUEST_KEY = "voucher_request_key"
        const val RESULT_VOUCHER_ID = "result_voucher_id"
        const val RESULT_VOUCHER_CODE = "result_voucher_code"
        const val ARG_RESTAURANT_ID = "restaurantId"
        const val ARG_TOTAL_BILL = "totalBill"
    }
}
