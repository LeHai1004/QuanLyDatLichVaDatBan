package com.ute.quanlydatban.ui.owner.menu

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.MenuItem
import com.ute.quanlydatban.databinding.FragmentOwnerMenuBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.OwnerMenuViewModel

class OwnerMenuFragment : Fragment() {

    private var _binding: FragmentOwnerMenuBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(this)[OwnerMenuViewModel::class.java]
    }

    private lateinit var menuAdapter: OwnerMenuAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOwnerMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadData()
    }

    private fun setupRecyclerView() {
        menuAdapter = OwnerMenuAdapter(
            onEditClick = { item ->
                openEditActivity(item)
            },
            onToggleAvailability = { item, isAvailable ->
                viewModel.toggleAvailability(item, isAvailable) { success ->
                    if (!success) {
                        SnackbarHelper.show(binding.root, getString(R.string.owner_menu_error_status_update))
                    }
                }
            }
        )

        binding.rvOwnerMenu.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = menuAdapter
        }
    }

    private fun setupListeners() {
        binding.btnAddDish.setOnClickListener {
            val intent = Intent(requireContext(), OwnerMenuEditActivity::class.java).apply {
                putExtra(OwnerMenuEditActivity.EXTRA_RESTAURANT_ID, viewModel.restaurant.value?.id.orEmpty())
            }
            startActivity(intent)
        }

        binding.layoutStates.btnRetry.setOnClickListener {
            viewModel.loadData()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) {
                UiStateHelper.showLoading(binding.layoutStates.root)
                binding.rvOwnerMenu.visibility = View.GONE
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrBlank()) {
                UiStateHelper.showError(binding.layoutStates.root, error)
                binding.rvOwnerMenu.visibility = View.GONE
            }
        }

        viewModel.categories.observe(viewLifecycleOwner) { categories ->
            renderCategoryChips(categories)
        }

        viewModel.displayItems.observe(viewLifecycleOwner) { items ->
            if (viewModel.isLoading.value == true) return@observe

            if (items.isEmpty()) {
                UiStateHelper.showEmpty(binding.layoutStates.root)
                binding.rvOwnerMenu.visibility = View.GONE
            } else {
                UiStateHelper.hideAll(binding.layoutStates.root)
                binding.rvOwnerMenu.visibility = View.VISIBLE
                menuAdapter.submitList(items)
            }
        }
    }

    private fun renderCategoryChips(categories: List<String>) {
        val context = requireContext()
        val currentSelected = viewModel.selectedCategory.value ?: "Tất cả"

        binding.cgCategories.removeAllViews()
        for (category in categories) {
            val chip = Chip(context).apply {
                text = category
                isCheckable = true
                isChecked = (category == currentSelected)
                chipCornerRadius = 24f
                chipStrokeWidth = 1f
                chipStrokeColor = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.booktable_border)
                )

                if (isChecked) {
                    chipBackgroundColor = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.owner_menu_category_bg_selected)
                    )
                    setTextColor(ContextCompat.getColor(context, R.color.booktable_orange))
                } else {
                    chipBackgroundColor = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.white)
                    )
                    setTextColor(ContextCompat.getColor(context, R.color.booktable_muted))
                }

                setOnClickListener {
                    viewModel.selectCategory(category)
                }
            }
            binding.cgCategories.addView(chip)
        }
    }

    private fun openEditActivity(item: MenuItem) {
        val intent = Intent(requireContext(), OwnerMenuEditActivity::class.java).apply {
            putExtra(OwnerMenuEditActivity.EXTRA_RESTAURANT_ID, item.restaurantId.ifBlank { viewModel.restaurant.value?.id.orEmpty() })
            putExtra(OwnerMenuEditActivity.EXTRA_MENU_ITEM_ID, item.id)
            putExtra(OwnerMenuEditActivity.EXTRA_MENU_ITEM_NAME, item.name)
            putExtra(OwnerMenuEditActivity.EXTRA_MENU_ITEM_PRICE, item.price)
            putExtra(OwnerMenuEditActivity.EXTRA_MENU_ITEM_CATEGORY, item.category)
            putExtra(OwnerMenuEditActivity.EXTRA_MENU_ITEM_IMAGE_URL, item.imageUrl)
            putExtra(OwnerMenuEditActivity.EXTRA_MENU_ITEM_AVAILABLE, item.available)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
