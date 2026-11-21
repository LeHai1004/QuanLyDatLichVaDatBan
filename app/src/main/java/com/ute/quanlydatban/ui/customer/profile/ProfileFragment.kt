package com.ute.quanlydatban.ui.customer.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.ute.quanlydatban.R
import com.ute.quanlydatban.BuildConfig
import com.ute.quanlydatban.databinding.FragmentProfileBinding
import com.ute.quanlydatban.ui.auth.LoginActivity
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.ProfileViewModel

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(this, ProfileViewModel.Factory())[ProfileViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check if logged in
        if (FirebaseAuth.getInstance().currentUser == null) {
            navigateToLogin()
            return
        }

        setupListeners()
        setupReminderDemoSwitch()
        observeViewModel()
    }

    private fun setupReminderDemoSwitch() {
        if (BuildConfig.DEBUG) {
            binding.llReminderDemoRow.visibility = View.VISIBLE
            binding.vReminderDemoDivider.visibility = View.VISIBLE

            val prefs = requireContext().getSharedPreferences(
                com.ute.quanlydatban.work.ReminderScheduler.PREF_REMINDER_DEMO,
                android.content.Context.MODE_PRIVATE
            )
            binding.swReminderDemo.isChecked = prefs.getBoolean(
                com.ute.quanlydatban.work.ReminderScheduler.KEY_REMINDER_DEMO_ENABLED,
                false
            )

            binding.swReminderDemo.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean(
                    com.ute.quanlydatban.work.ReminderScheduler.KEY_REMINDER_DEMO_ENABLED,
                    isChecked
                ).apply()
            }
        } else {
            binding.llReminderDemoRow.visibility = View.GONE
            binding.vReminderDemoDivider.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadProfile()
    }

    private fun setupListeners() {
        binding.llMenuInfo.setOnClickListener {
            startActivity(Intent(requireContext(), EditProfileActivity::class.java))
        }

        binding.llMenuHistory.setOnClickListener {
            findNavController().navigate(R.id.customerHistoryFragment)
        }

        binding.llMenuVouchers.setOnClickListener {
            findNavController().navigate(R.id.voucherFragment)
        }

        binding.llMenuLoyalty.setOnClickListener {
            findNavController().navigate(R.id.loyaltyPlaceholderFragment)
        }

        binding.llMenuFavorites.setOnClickListener {
            findNavController().navigate(R.id.favoritePlaceholderFragment)
        }

        binding.llMenuSettings.setOnClickListener {
            SnackbarHelper.show(binding.root, "Tính năng đang phát triển.")
        }

        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            navigateToLogin()
        }

        binding.layoutStates.btnRetry.setOnClickListener {
            viewModel.loadProfile()
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

        viewModel.user.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                binding.tvName.text = user.name.ifEmpty { "Khách hàng" }
                binding.tvPhone.text = user.phone.ifEmpty { "Chưa cập nhật SĐT" }
                binding.tvMemberSince.text = getString(R.string.profile_member_since_format, "11/2026")
                binding.tvRankBadge.text = getString(
                    R.string.profile_rank_format,
                    user.rank.ifEmpty { "Đồng" }
                ) + " · " + getString(R.string.profile_points_format, user.points)

                if (user.avatarUrl.isNotBlank()) {
                    try {
                        binding.ivAvatar.setImageURI(Uri.parse(user.avatarUrl))
                        binding.ivAvatar.visibility = View.VISIBLE
                        binding.tvAvatarInitial.visibility = View.GONE
                    } catch (e: Exception) {
                        binding.ivAvatar.visibility = View.GONE
                        binding.tvAvatarInitial.visibility = View.VISIBLE
                        binding.tvAvatarInitial.text = user.name.firstOrNull()?.toString()?.uppercase() ?: "U"
                    }
                } else {
                    binding.ivAvatar.visibility = View.GONE
                    binding.tvAvatarInitial.visibility = View.VISIBLE
                    binding.tvAvatarInitial.text = user.name.firstOrNull()?.toString()?.uppercase() ?: "U"
                }
            } else if (viewModel.isLoading.value == false) {
                navigateToLogin()
            }
        }

        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            binding.tvBookingCount.text = stats.bookingCount.toString()
            binding.tvFavoriteCount.text = stats.favoriteCount.toString()
            binding.tvReviewCount.text = stats.reviewCount.toString()
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                SnackbarHelper.show(binding.root, err)
                viewModel.clearMessages()
            }
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
