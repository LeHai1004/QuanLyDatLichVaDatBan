package com.ute.quanlydatban.ui.customer.menu

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.FragmentCartBinding
import com.ute.quanlydatban.ui.customer.booking.PaymentActivity
import com.ute.quanlydatban.ui.customer.voucher.VoucherFragment
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.formatMoney
import com.ute.quanlydatban.viewmodel.CartViewModel

class CartFragment : Fragment() {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(requireActivity(), CartViewModel.Factory())[CartViewModel::class.java]
    }

    private lateinit var cartAdapter: CartAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader()
        setupRestaurantInfo()
        setupRecyclerView()
        setupVoucherCard()
        setupCheckoutButton()
        setupFragmentResultListener()
        observeViewModel()
    }

    private fun setupFragmentResultListener() {
        setFragmentResultListener(VoucherFragment.REQUEST_KEY) { _, bundle ->
            val voucherCode = bundle.getString(VoucherFragment.RESULT_VOUCHER_CODE).orEmpty()
            if (voucherCode.isNotBlank()) {
                viewModel.applyVoucherCode(voucherCode)
            }
        }
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnCartAddMore.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRestaurantInfo() {
        val res = viewModel.restaurant.value
        if (res != null) {
            binding.tvRestaurantName.text = res.name
        }

        val date = viewModel.bookingDate
        val time = viewModel.bookingTime
        val guests = viewModel.bookingGuests
        val guestsStr = getString(R.string.booking_guest_format, guests)
        val contextStr = listOf(date, time, guestsStr).filter { it.isNotBlank() }.joinToString(" · ")
        binding.tvBookingContext.text = contextStr

        val tableName = viewModel.tableName
        if (tableName.isNotBlank()) {
            binding.tvBookingReminder.text = getString(R.string.cart_booking_ready_format, tableName)
        } else {
            binding.tvBookingReminder.text = getString(R.string.cart_booking_ready_default)
        }
    }

    private fun setupRecyclerView() {
        cartAdapter = CartAdapter(
            onIncrease = { menuItemId ->
                viewModel.menuItems.value?.find { it.id == menuItemId }?.let { item ->
                    viewModel.addItem(item)
                }
            },
            onDecrease = { menuItemId ->
                viewModel.decreaseItem(menuItemId)
            }
        )

        binding.rvCartItems.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = cartAdapter
        }
    }

    private fun setupVoucherCard() {
        binding.llVoucherCard.setOnClickListener {
            val bundle = Bundle().apply {
                putString(VoucherFragment.ARG_RESTAURANT_ID, viewModel.restaurantId)
                putLong(VoucherFragment.ARG_TOTAL_BILL, viewModel.getSubtotal())
            }
            findNavController().navigate(R.id.action_cartFragment_to_voucherFragment, bundle)
        }

        binding.btnRemoveVoucher.setOnClickListener {
            viewModel.removeVoucher()
        }
    }

    private fun setupCheckoutButton() {
        binding.btnContinueDeposit.setOnClickListener {
            if (viewModel.bookingId.isBlank() || viewModel.restaurantId.isBlank()) {
                SnackbarHelper.show(binding.root, "Thiếu thông tin đơn đặt bàn hoặc nhà hàng.")
                return@setOnClickListener
            }

            binding.btnContinueDeposit.isEnabled = false
            viewModel.submitPreOrder { success ->
                binding.btnContinueDeposit.isEnabled = true
                if (success) {
                    val voucherId = viewModel.appliedVoucher.value?.id.orEmpty()
                    val intent = Intent(requireContext(), PaymentActivity::class.java).apply {
                        putExtra(PaymentActivity.EXTRA_BOOKING_ID, viewModel.bookingId)
                        putExtra(PaymentActivity.EXTRA_RESTAURANT_ID, viewModel.restaurantId)
                        putExtra("extra_voucher_id", voucherId)
                    }
                    startActivity(intent)
                } else {
                    val error = viewModel.errorMessage.value ?: getString(R.string.cart_error_save)
                    SnackbarHelper.show(binding.root, error)
                }
            }
        }
    }

    private fun observeViewModel() {
        viewModel.restaurant.observe(viewLifecycleOwner) { res ->
            if (res != null) {
                binding.tvRestaurantName.text = res.name
                val deposit = res.depositAmount
                binding.tvDepositAmount.text = formatMoney(deposit)
                binding.tvBottomHint.text = getString(
                    R.string.cart_bottom_hint_format,
                    formatMoney(deposit)
                )
            }
        }

        viewModel.cartItems.observe(viewLifecycleOwner) { cartMap ->
            val list = cartMap.values.toList()
            cartAdapter.submitList(list)

            if (list.isEmpty()) {
                binding.tvCartEmpty.visibility = View.VISIBLE
                binding.rvCartItems.visibility = View.GONE
            } else {
                binding.tvCartEmpty.visibility = View.GONE
                binding.rvCartItems.visibility = View.VISIBLE
            }

            val totalQty = viewModel.getTotalQuantity()
            val subtotal = viewModel.getSubtotal()
            val deposit = viewModel.getDeposit()

            binding.tvSubtotalLabel.text = getString(R.string.cart_subtotal_format, totalQty)
            binding.tvSubtotalAmount.text = formatMoney(subtotal)
            binding.tvTotalAmount.text = formatMoney(viewModel.getFinalTotal())
            binding.tvDepositAmount.text = formatMoney(deposit)
            binding.tvBottomHint.text = getString(
                R.string.cart_bottom_hint_format,
                formatMoney(deposit)
            )
        }

        viewModel.appliedVoucher.observe(viewLifecycleOwner) { voucher ->
            if (voucher != null) {
                val discount = viewModel.getDiscount()
                binding.tvVoucherTitle.text = getString(R.string.payment_voucher_discount_label)
                binding.tvVoucherSelected.text = "-${formatMoney(discount)}"
                binding.tvVoucherSelected.visibility = View.VISIBLE
                binding.btnRemoveVoucher.visibility = View.VISIBLE
                binding.ivVoucherArrow.visibility = View.GONE

                binding.llDiscountRow.visibility = View.VISIBLE
                binding.tvDiscountAmount.text = "-${formatMoney(discount)}"
            } else {
                binding.tvVoucherTitle.text = getString(R.string.voucher_title)
                binding.tvVoucherSelected.visibility = View.GONE
                binding.btnRemoveVoucher.visibility = View.GONE
                binding.ivVoucherArrow.visibility = View.VISIBLE

                binding.llDiscountRow.visibility = View.GONE
            }
            binding.tvTotalAmount.text = formatMoney(viewModel.getFinalTotal())
        }

        viewModel.discountAmount.observe(viewLifecycleOwner) { discount ->
            if (viewModel.appliedVoucher.value != null) {
                binding.tvDiscountAmount.text = "-${formatMoney(discount)}"
                binding.tvVoucherSelected.text = "-${formatMoney(discount)}"
            }
            binding.tvTotalAmount.text = formatMoney(viewModel.getFinalTotal())
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                SnackbarHelper.show(binding.root, error)
                viewModel.clearMessages()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
