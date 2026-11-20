package com.ute.quanlydatban.ui.owner.voucher

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.Voucher
import com.ute.quanlydatban.data.model.VoucherType
import com.ute.quanlydatban.databinding.DialogOwnerVoucherBinding
import com.ute.quanlydatban.databinding.FragmentOwnerVoucherBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.OwnerVoucherViewModel

class OwnerVoucherFragment : Fragment() {

    private var _binding: FragmentOwnerVoucherBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(this, OwnerVoucherViewModel.Factory())[OwnerVoucherViewModel::class.java]
    }

    private lateinit var adapter: OwnerVoucherAdapter
    private var restaurantId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOwnerVoucherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        restaurantId = arguments?.getString(ARG_RESTAURANT_ID).orEmpty().ifEmpty { "r1" }

        setupUI()
        observeViewModel()

        viewModel.loadOwnerVouchers(restaurantId)
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        adapter = OwnerVoucherAdapter(
            restaurantId,
            onToggleActive = { voucher, active ->
                Log.e("VoucherDebug", "Toggle switch clicked - ID: ${voucher.id}, new value: $active")
                viewModel.toggleActive(voucher.id, active, restaurantId) { success, errorMsg ->
                    if (success) {
                        Log.e("VoucherDebug", "Toggle switch success - ID: ${voucher.id}, value: $active")
                        viewModel.loadOwnerVouchers(restaurantId)
                    } else {
                        Log.e("VoucherDebug", "Toggle switch failed - ID: ${voucher.id}, error: $errorMsg")
                        SnackbarHelper.show(binding.root, errorMsg ?: "Không thể đổi trạng thái voucher.")
                        viewModel.loadOwnerVouchers(restaurantId)
                    }
                }
            },
            onEditClick = { voucher ->
                showAddEditDialog(voucher)
            }
        )

        binding.rvOwnerVouchers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOwnerVouchers.adapter = adapter

        binding.btnAddVoucher.setOnClickListener {
            showAddEditDialog(null)
        }

        binding.layoutStates.btnRetry.setOnClickListener {
            viewModel.loadOwnerVouchers(restaurantId)
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

        viewModel.saveSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                SnackbarHelper.show(binding.root, getString(R.string.owner_voucher_save_success))
                viewModel.clearMessages()
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                UiStateHelper.hideAll(binding.layoutStates.root)
                SnackbarHelper.show(binding.root, err)
                viewModel.clearMessages()
            }
        }
    }

    private fun showAddEditDialog(existingVoucher: Voucher?) {
        if (existingVoucher != null && existingVoucher.restaurantId.isBlank()) {
            SnackbarHelper.show(binding.root, "Voucher dùng chung hệ thống, chỉ xem không thể chỉnh sửa thông tin.")
            return
        }

        val dialogBinding = DialogOwnerVoucherBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        if (existingVoucher != null) {
            dialogBinding.tvDialogTitle.text = getString(R.string.owner_voucher_dialog_edit_title)
            dialogBinding.etCode.setText(existingVoucher.code)
            dialogBinding.etCode.isEnabled = false
            if (existingVoucher.type == VoucherType.PERCENT) {
                dialogBinding.rbPercent.isChecked = true
            } else {
                dialogBinding.rbAmount.isChecked = true
            }
            dialogBinding.etValue.setText(existingVoucher.value.toString())
            dialogBinding.etMinOrder.setText(existingVoucher.minOrder.toString())
            dialogBinding.etMaxDiscount.setText(existingVoucher.maxDiscount.toString())
            dialogBinding.cbActive.isChecked = existingVoucher.active
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSave.setOnClickListener {
            val code = dialogBinding.etCode.text?.toString().orEmpty().trim()
            val valueStr = dialogBinding.etValue.text?.toString().orEmpty().trim()
            val minOrderStr = dialogBinding.etMinOrder.text?.toString().orEmpty().trim()
            val maxDiscountStr = dialogBinding.etMaxDiscount.text?.toString().orEmpty().trim()

            if (code.isBlank()) {
                SnackbarHelper.show(dialogBinding.root, "Vui lòng nhập mã voucher.")
                return@setOnClickListener
            }

            val value = valueStr.toLongOrNull() ?: 0L
            if (value <= 0) {
                SnackbarHelper.show(dialogBinding.root, "Vui lòng nhập mức giảm hợp lệ.")
                return@setOnClickListener
            }

            val type = if (dialogBinding.rbPercent.isChecked) VoucherType.PERCENT else VoucherType.AMOUNT
            val minOrder = minOrderStr.toLongOrNull() ?: 0L
            val maxDiscount = maxDiscountStr.toLongOrNull() ?: 0L
            val active = dialogBinding.cbActive.isChecked

            val voucherToSave = Voucher(
                id = existingVoucher?.id.orEmpty(),
                restaurantId = restaurantId,
                code = code,
                type = type,
                value = value,
                minOrder = minOrder,
                maxDiscount = maxDiscount,
                expiry = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000, // 30 days
                active = active
            )

            viewModel.saveVoucher(voucherToSave, restaurantId)
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_RESTAURANT_ID = "restaurantId"
    }
}
