package com.ute.quanlydatban.ui.customer.menu

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.FragmentCartBinding
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
        setupCheckoutButton()
        observeViewModel()
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener {
            // Nút quay lại: Cart về Menu
            findNavController().navigateUp()
        }

        binding.btnCartAddMore.setOnClickListener {
            // Thêm món: quay lại Menu
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

    private fun setupCheckoutButton() {
        binding.btnContinueDeposit.setOnClickListener {
            binding.btnContinueDeposit.isEnabled = false
            viewModel.submitPreOrder { success ->
                binding.btnContinueDeposit.isEnabled = true
                if (success) {
                    // Chuyển sang màn hình Payment T11 (hiện tại là destination tạm/Placeholder)
                    val bundle = Bundle().apply {
                        putString("bookingId", viewModel.bookingId)
                        putString("restaurantId", viewModel.restaurantId)
                        putLong("depositAmount", viewModel.getDeposit())
                    }
                    findNavController().navigate(
                        R.id.action_cartFragment_to_paymentPlaceholderFragment,
                        bundle
                    )
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
            binding.tvTotalAmount.text = formatMoney(subtotal)
            binding.tvDepositAmount.text = formatMoney(deposit)
            binding.tvBottomHint.text = getString(
                R.string.cart_bottom_hint_format,
                formatMoney(deposit)
            )
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
