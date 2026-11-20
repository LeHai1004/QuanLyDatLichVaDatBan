package com.ute.quanlydatban.ui.owner

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.databinding.FragmentOwnerProfileBinding
import com.ute.quanlydatban.ui.auth.LoginActivity
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.OwnerDashboardViewModel

class OwnerProfileFragment : Fragment() {

    private var _binding: FragmentOwnerProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(requireActivity(), OwnerDashboardViewModel.Factory)[OwnerDashboardViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOwnerProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupButtons()
        observeViewModel()
    }

    private fun setupButtons() {
        binding.btnSaveProfile.setOnClickListener {
            viewModel.updateRestaurantProfile(
                name = binding.etRestaurantName.text?.toString().orEmpty(),
                address = binding.etRestaurantAddress.text?.toString().orEmpty(),
                openTime = binding.etOpenTime.text?.toString().orEmpty(),
                closeTime = binding.etCloseTime.text?.toString().orEmpty(),
                depositStr = binding.etDepositAmount.text?.toString().orEmpty(),
                category = binding.etCategory.text?.toString().orEmpty()
            )
        }

        binding.btnManageVouchers.setOnClickListener {
            val resId = viewModel.restaurant.value?.id.orEmpty()
            val bundle = Bundle().apply {
                putString("restaurantId", resId)
            }
            androidx.navigation.fragment.NavHostFragment.findNavController(this)
                .navigate(R.id.ownerVoucherFragment, bundle)
        }

        binding.btnLogout.setOnClickListener {
            viewModel.signOut()
            val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            requireActivity().finish()
        }

        binding.layoutStates.btnRetry.setOnClickListener {
            viewModel.loadRestaurantAndData()
        }
    }

    private fun observeViewModel() {
        viewModel.isDashboardLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) {
                UiStateHelper.showLoading(binding.layoutStates.root)
                binding.llProfileBody.visibility = View.GONE
            }
        }

        viewModel.dashboardErrorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                UiStateHelper.showError(binding.layoutStates.root, error)
                binding.llProfileBody.visibility = View.GONE
                SnackbarHelper.show(binding.root, error) {
                    viewModel.loadRestaurantAndData()
                }
            }
        }

        viewModel.restaurant.observe(viewLifecycleOwner) { res ->
            if (res != null && viewModel.isDashboardLoading.value == false && viewModel.dashboardErrorMessage.value == null) {
                UiStateHelper.hideAll(binding.layoutStates.root)
                binding.llProfileBody.visibility = View.VISIBLE
                bindData(res)
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

        // Quan sát các lỗi nhập liệu
        viewModel.nameError.observe(viewLifecycleOwner) { err ->
            binding.tvNameError.visibility = if (err != null) View.VISIBLE else View.GONE
            binding.tvNameError.text = err
        }

        viewModel.addressError.observe(viewLifecycleOwner) { err ->
            binding.tvAddressError.visibility = if (err != null) View.VISIBLE else View.GONE
            binding.tvAddressError.text = err
        }

        viewModel.timeError.observe(viewLifecycleOwner) { err ->
            binding.tvTimeError.visibility = if (err != null) View.VISIBLE else View.GONE
            binding.tvTimeError.text = err
        }

        viewModel.depositError.observe(viewLifecycleOwner) { err ->
            binding.tvDepositError.visibility = if (err != null) View.VISIBLE else View.GONE
            binding.tvDepositError.text = err
        }

        viewModel.isProfileSaving.observe(viewLifecycleOwner) { saving ->
            binding.btnSaveProfile.isEnabled = !saving
            binding.btnSaveProfile.text = if (saving) getString(R.string.owner_saving) else getString(R.string.owner_btn_save)
        }

        viewModel.profileSaveSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                SnackbarHelper.show(binding.root, getString(R.string.owner_save_success))
            }
        }

        viewModel.profileErrorMessage.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                SnackbarHelper.show(binding.root, err)
            }
        }
    }

    private fun bindData(res: Restaurant) {
        // Chỉ gán khi ô nhập chưa có tương tác hoặc đang rỗng để không đè dữ liệu user đang gõ
        if (binding.etRestaurantName.text.isNullOrEmpty()) {
            binding.etRestaurantName.setText(res.name)
        }
        if (binding.etRestaurantAddress.text.isNullOrEmpty()) {
            binding.etRestaurantAddress.setText(res.address)
        }
        if (binding.etOpenTime.text.isNullOrEmpty()) {
            binding.etOpenTime.setText(res.openTime)
        }
        if (binding.etCloseTime.text.isNullOrEmpty()) {
            binding.etCloseTime.setText(res.closeTime)
        }
        if (binding.etDepositAmount.text.isNullOrEmpty()) {
            binding.etDepositAmount.setText(res.depositAmount.toString())
        }
        if (binding.etCategory.text.isNullOrEmpty()) {
            binding.etCategory.setText(res.category)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
