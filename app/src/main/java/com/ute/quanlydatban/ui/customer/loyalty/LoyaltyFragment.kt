package com.ute.quanlydatban.ui.customer.loyalty

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.Voucher
import com.ute.quanlydatban.data.repository.LoyaltyRepository
import com.ute.quanlydatban.databinding.FragmentLoyaltyBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.LoyaltyViewModel

class LoyaltyFragment : Fragment() {

    private var _binding: FragmentLoyaltyBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(this)[LoyaltyViewModel::class.java]
    }

    private lateinit var voucherAdapter: LoyaltyVoucherAdapter
    private lateinit var transactionAdapter: LoyaltyTransactionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoyaltyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerViews()
        setupTabSwitcher()
        setupRetry()
        observeViewModel()

        viewModel.loadData()
    }

    private fun setupToolbar() {
        binding.ivBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.ivInfoPolicy.visibility = View.VISIBLE
        binding.ivInfoPolicy.setImageResource(R.drawable.ic_auth_bell)
        binding.ivInfoPolicy.setOnClickListener {
            showPolicyDialog()
        }
    }

    private fun setupRecyclerViews() {
        voucherAdapter = LoyaltyVoucherAdapter { voucher ->
            showRedeemConfirmDialog(voucher)
        }
        binding.rvLoyaltyVouchers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = voucherAdapter
        }

        transactionAdapter = LoyaltyTransactionAdapter()
        binding.rvLoyaltyTransactions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = transactionAdapter
        }
    }

    private fun setupTabSwitcher() {
        binding.tvTabRewards.setOnClickListener {
            viewModel.selectTab(0)
        }
        binding.tvTabHistory.setOnClickListener {
            viewModel.selectTab(1)
        }
    }

    private fun setupRetry() {
        binding.layoutStates.btnRetry.setOnClickListener {
            viewModel.loadData()
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

        viewModel.errorMessage.observe(viewLifecycleOwner) { err ->
            if (!err.isNullOrBlank()) {
                UiStateHelper.showError(binding.layoutStates.root, err)
            }
        }

        viewModel.user.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                binding.tvPoints.text = getString(R.string.loyalty_points_format, user.points)
            }
        }

        viewModel.rankProgress.observe(viewLifecycleOwner) { progress ->
            if (progress != null) {
                binding.tvRankName.text = getString(R.string.loyalty_rank_badge_format, progress.rank)
                val badgeRes = when (progress.rank) {
                    LoyaltyRepository.RANK_GOLD -> R.drawable.ic_rank_gold
                    LoyaltyRepository.RANK_SILVER -> R.drawable.ic_rank_silver
                    else -> R.drawable.ic_rank_bronze
                }
                binding.ivRankBadge.setImageResource(badgeRes)

                binding.pbNextRank.progress = progress.progressPercent

                if (progress.isMaxRank) {
                    binding.tvNextRankNotice.text = getString(R.string.loyalty_max_rank_reached)
                } else {
                    binding.tvNextRankNotice.text = getString(
                        R.string.loyalty_next_rank_needed_format,
                        progress.pointsNeeded,
                        progress.nextRank
                    )
                }
            }
        }

        viewModel.rewardVouchers.observe(viewLifecycleOwner) { vouchers ->
            voucherAdapter.submitList(vouchers)
            updateTabVisibility(viewModel.selectedTab.value ?: 0)
        }

        viewModel.transactions.observe(viewLifecycleOwner) { transactions ->
            transactionAdapter.submitList(transactions)
            updateTabVisibility(viewModel.selectedTab.value ?: 0)
        }

        viewModel.selectedTab.observe(viewLifecycleOwner) { tab ->
            updateTabVisibility(tab)
        }
    }

    private fun updateTabVisibility(tab: Int) {
        val context = requireContext()
        if (tab == 0) {
            // Rewards Tab Active
            binding.tvTabRewards.setBackgroundResource(R.drawable.bg_menu_add_btn_active)
            binding.tvTabRewards.setTextColor(ContextCompat.getColor(context, R.color.white))
            binding.tvTabHistory.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.tvTabHistory.setTextColor(ContextCompat.getColor(context, R.color.booktable_muted))

            binding.rvLoyaltyTransactions.visibility = View.GONE

            val vouchers = viewModel.rewardVouchers.value.orEmpty()
            if (vouchers.isEmpty() && viewModel.isLoading.value != true) {
                binding.rvLoyaltyVouchers.visibility = View.GONE
                binding.llTabEmpty.visibility = View.VISIBLE
                binding.tvTabEmptyTitle.text = getString(R.string.loyalty_empty_vouchers)
                binding.tvTabEmptyDesc.text = ""
            } else {
                binding.rvLoyaltyVouchers.visibility = View.VISIBLE
                binding.llTabEmpty.visibility = View.GONE
            }
        } else {
            // History Tab Active
            binding.tvTabHistory.setBackgroundResource(R.drawable.bg_menu_add_btn_active)
            binding.tvTabHistory.setTextColor(ContextCompat.getColor(context, R.color.white))
            binding.tvTabRewards.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.tvTabRewards.setTextColor(ContextCompat.getColor(context, R.color.booktable_muted))

            binding.rvLoyaltyVouchers.visibility = View.GONE

            val transactions = viewModel.transactions.value.orEmpty()
            if (transactions.isEmpty() && viewModel.isLoading.value != true) {
                binding.rvLoyaltyTransactions.visibility = View.GONE
                binding.llTabEmpty.visibility = View.VISIBLE
                binding.tvTabEmptyTitle.text = getString(R.string.loyalty_empty_history)
                binding.tvTabEmptyDesc.text = getString(R.string.loyalty_empty_history_desc)
            } else {
                binding.rvLoyaltyTransactions.visibility = View.VISIBLE
                binding.llTabEmpty.visibility = View.GONE
            }
        }
    }

    private fun showRedeemConfirmDialog(voucher: Voucher) {
        val message = getString(
            R.string.loyalty_dialog_redeem_message,
            voucher.pointsCost,
            voucher.code.ifBlank { voucher.id }
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.loyalty_dialog_redeem_title)
            .setMessage(message)
            .setPositiveButton(R.string.loyalty_dialog_redeem_confirm) { _, _ ->
                viewModel.redeemVoucher(voucher) { success, errMsg ->
                    if (success) {
                        SnackbarHelper.show(binding.root, getString(R.string.loyalty_redeem_success))
                    } else {
                        SnackbarHelper.show(binding.root, errMsg ?: getString(R.string.loyalty_redeem_error))
                    }
                }
            }
            .setNegativeButton(R.string.loyalty_dialog_redeem_cancel, null)
            .show()
    }

    private fun showPolicyDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.loyalty_policy_title)
            .setMessage(R.string.loyalty_policy_desc)
            .setPositiveButton(R.string.snackbar_action_close, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
