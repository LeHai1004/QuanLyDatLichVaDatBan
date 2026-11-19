package com.ute.quanlydatban.ui.customer.menu

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.MenuItem
import com.ute.quanlydatban.databinding.FragmentMenuBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.util.formatMoney
import com.ute.quanlydatban.viewmodel.CartViewModel

class MenuFragment : Fragment() {

    private var _binding: FragmentMenuBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(requireActivity(), CartViewModel.Factory())[CartViewModel::class.java]
    }

    private lateinit var menuAdapter: MenuAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initArguments()
        setupHeader()
        setupRecyclerView()
        setupStickyCartBar()
        setupRetryButton()
        observeViewModel()
    }

    private fun initArguments() {
        val bookingId = arguments?.getString("bookingId").orEmpty()
        val restaurantId = arguments?.getString("restaurantId").orEmpty()
        val restaurantName = arguments?.getString("restaurantName")
        val tableName = arguments?.getString("tableName")
        val date = arguments?.getString("bookingDate")
        val time = arguments?.getString("bookingTime")
        val guests = arguments?.getLong("bookingGuests", 2L)
        val deposit = arguments?.getLong("depositAmount", 100_000L)

        if (restaurantId.isEmpty()) {
            SnackbarHelper.show(binding.root, getString(R.string.cart_error_missing_data))
            return
        }

        viewModel.initData(
            bId = bookingId,
            rId = restaurantId,
            rName = restaurantName,
            tName = tableName,
            date = date,
            time = time,
            guests = guests,
            deposit = deposit
        )

        // Hiển thị thông tin bàn context
        val contextText = if (!tableName.isNullOrBlank() && !time.isNullOrBlank()) {
            "$tableName · $time"
        } else if (!time.isNullOrBlank()) {
            "Giờ hẹn: $time"
        } else {
            "Đặt bàn trước"
        }
        binding.tvTableContext.text = contextText
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener {
            // Nút quay lại: Menu về TableSelect
            findNavController().navigateUp()
        }

        binding.btnSkip.setOnClickListener {
            // Bỏ qua chọn món: điều hướng tới màn thanh toán cọc T11 (tạm thời là Placeholder)
            val bundle = Bundle().apply {
                putString("bookingId", viewModel.bookingId)
                putString("restaurantId", viewModel.restaurantId)
            }
            findNavController().navigate(
                R.id.action_menuFragment_to_paymentPlaceholderFragment,
                bundle
            )
        }
    }

    private fun setupRecyclerView() {
        menuAdapter = MenuAdapter(
            onAddClick = { item ->
                viewModel.addItem(item)
            },
            onMinusClick = { item ->
                viewModel.decreaseItem(item.id)
            }
        )

        binding.rvMenu.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = menuAdapter
        }
    }

    private fun setupStickyCartBar() {
        binding.llStickyCartBar.setOnClickListener {
            findNavController().navigate(R.id.action_menuFragment_to_cartFragment)
        }
    }

    private fun setupRetryButton() {
        binding.layoutStates.btnRetry.setOnClickListener {
            viewModel.loadMenu(viewModel.restaurantId)
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) {
                UiStateHelper.showLoading(binding.layoutStates.root)
                binding.llMenuContent.visibility = View.GONE
            }
        }

        viewModel.menuItems.observe(viewLifecycleOwner) { items ->
            val loading = viewModel.isLoading.value ?: false
            val error = viewModel.errorMessage.value
            if (!loading && error == null) {
                if (items.isEmpty()) {
                    UiStateHelper.showEmpty(binding.layoutStates.root)
                    binding.llMenuContent.visibility = View.GONE
                } else {
                    UiStateHelper.hideAll(binding.layoutStates.root)
                    binding.llMenuContent.visibility = View.VISIBLE
                    setupCategoryChips(items)
                    filterMenuByCategory(viewModel.selectedCategory.value ?: "Tất cả")
                }
            }
        }

        viewModel.cartItems.observe(viewLifecycleOwner) { cart ->
            menuAdapter.updateCartState(cart)
            updateCartSummary()
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                val items = viewModel.menuItems.value
                if (items.isNullOrEmpty()) {
                    UiStateHelper.showError(binding.layoutStates.root, error)
                    binding.llMenuContent.visibility = View.GONE
                } else {
                    SnackbarHelper.show(binding.root, error)
                }
                viewModel.clearMessages()
            }
        }
    }

    private fun setupCategoryChips(items: List<MenuItem>) {
        val container = binding.llCategories
        container.removeAllViews()

        val categories = mutableListOf("Tất cả")
        categories.addAll(items.map { it.category }.distinct().filter { it.isNotBlank() })

        val chipViews = mutableListOf<Pair<String, TextView>>()

        categories.forEach { cat ->
            val chip = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    resources.getDimensionPixelSize(R.dimen.booking_chip_height)
                ).apply {
                    marginEnd = resources.getDimensionPixelSize(R.dimen.booking_chip_gap)
                }
                text = cat
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(
                    resources.getDimensionPixelSize(R.dimen.booking_chip_padding_h),
                    0,
                    resources.getDimensionPixelSize(R.dimen.booking_chip_padding_h),
                    0
                )
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    viewModel.setCategory(cat)
                }
            }
            container.addView(chip)
            chipViews.add(cat to chip)
        }

        viewModel.selectedCategory.observe(viewLifecycleOwner) { currentCat ->
            chipViews.forEach { (cat, chip) ->
                val isSelected = cat == currentCat
                chip.setBackgroundResource(
                    if (isSelected) R.drawable.bg_booking_chip_selected
                    else R.drawable.bg_booking_chip_normal
                )
                chip.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (isSelected) R.color.white else R.color.booktable_muted
                    )
                )
                chip.paint.isFakeBoldText = isSelected
            }
            filterMenuByCategory(currentCat)
        }
    }

    private fun filterMenuByCategory(category: String) {
        val allItems = viewModel.menuItems.value.orEmpty()
        val filtered = if (category == "Tất cả") {
            allItems
        } else {
            allItems.filter { it.category == category }
        }

        if (filtered.isEmpty()) {
            binding.tvMenuEmpty.visibility = View.VISIBLE
            binding.rvMenu.visibility = View.GONE
        } else {
            binding.tvMenuEmpty.visibility = View.GONE
            binding.rvMenu.visibility = View.VISIBLE
            menuAdapter.submitList(filtered)
        }
    }

    private fun updateCartSummary() {
        val totalQty = viewModel.getTotalQuantity()
        val subtotal = viewModel.getSubtotal()

        binding.tvCartCount.text = getString(R.string.menu_cart_items_format, totalQty)
        binding.tvCartTotal.text = formatMoney(subtotal)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
