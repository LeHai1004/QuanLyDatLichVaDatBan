package com.ute.quanlydatban.ui.customer

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.FragmentSearchFilterBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.HomeViewModel

class SearchFilterFragment : Fragment() {

    private var _binding: FragmentSearchFilterBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(requireActivity(), HomeViewModel.Factory)[HomeViewModel::class.java]
    }

    private lateinit var restaurantAdapter: RestaurantAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupSearchInput()
        setupFilterChips()
        setupRatingChips()
        setupRecyclerView()
        setupRetryButton()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.ivBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupSearchInput() {
        binding.etSearch.setText(viewModel.searchQuery.value)
        binding.ivClearSearch.visibility =
            if (binding.etSearch.text.isNullOrEmpty()) View.GONE else View.VISIBLE

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString().orEmpty()
                binding.ivClearSearch.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
                viewModel.setSearchQuery(text)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.applySearchAndFilter()
                true
            } else {
                false
            }
        }

        binding.ivClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }
    }

    private fun setupFilterChips() {
        val categoryChips = listOf(
            binding.tvCategoryAll to "Tất cả",
            binding.tvCategoryVietnamese to "Món Việt",
            binding.tvCategoryCoffee to "Cà phê",
            binding.tvCategoryHotpot to "Lẩu & nướng"
        )

        fun updateCategoryUi(selected: String) {
            categoryChips.forEach { (view, cat) ->
                val isSelected = cat.equals(selected, ignoreCase = true)
                view.isSelected = isSelected
                view.setTypeface(
                    null,
                    if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
                )
            }
        }

        updateCategoryUi(viewModel.selectedCategory.value ?: "Tất cả")

        categoryChips.forEach { (view, cat) ->
            view.setOnClickListener {
                updateCategoryUi(cat)
                viewModel.setSelectedCategory(cat)
            }
        }
    }

    private fun setupRatingChips() {
        val ratingChips = listOf(
            binding.tvRatingAll to 0.0,
            binding.tvRating40 to 4.0,
            binding.tvRating45 to 4.5
        )

        fun updateRatingUi(selectedRating: Double) {
            ratingChips.forEach { (view, rating) ->
                val isSelected = rating == selectedRating
                view.isSelected = isSelected
                view.setTypeface(
                    null,
                    if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
                )
            }
        }

        updateRatingUi(viewModel.minRating.value ?: 0.0)

        ratingChips.forEach { (view, rating) ->
            view.setOnClickListener {
                updateRatingUi(rating)
                viewModel.setMinRating(rating)
            }
        }
    }

    private fun setupRecyclerView() {
        restaurantAdapter = RestaurantAdapter { restaurant ->
            val intent = Intent(requireContext(), RestaurantDetailActivity::class.java).apply {
                putExtra(RestaurantDetailActivity.EXTRA_RESTAURANT_ID, restaurant.id)
            }
            startActivity(intent)
        }

        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = restaurantAdapter
        }
    }

    private fun setupRetryButton() {
        binding.layoutStates.btnRetry.setOnClickListener {
            viewModel.loadRestaurants()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) {
                UiStateHelper.showLoading(binding.layoutStates.root)
                binding.layoutStates.root.visibility = View.VISIBLE
                binding.rvSearchResults.visibility = View.GONE
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                UiStateHelper.showError(binding.layoutStates.root, error)
                binding.layoutStates.root.visibility = View.VISIBLE
                binding.rvSearchResults.visibility = View.GONE
                SnackbarHelper.show(binding.root, error) {
                    viewModel.loadRestaurants()
                }
            }
        }

        viewModel.filteredRestaurants.observe(viewLifecycleOwner) { list ->
            if (viewModel.isLoading.value == false && viewModel.errorMessage.value == null) {
                binding.tvResultCount.text = getString(R.string.search_result_count_format, list.size)
                binding.layoutStates.root.visibility = View.GONE
                if (list.isEmpty()) {
                    UiStateHelper.showEmpty(binding.layoutStates.root)
                    binding.layoutStates.root.visibility = View.VISIBLE
                    binding.rvSearchResults.visibility = View.GONE
                } else {
                    UiStateHelper.hideAll(binding.layoutStates.root)
                    binding.layoutStates.root.visibility = View.GONE
                    binding.rvSearchResults.visibility = View.VISIBLE
                    restaurantAdapter.submitList(list)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
