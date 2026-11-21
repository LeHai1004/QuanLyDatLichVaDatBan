package com.ute.quanlydatban.ui.customer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.FragmentHomeBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.HomeViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
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
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupCategoryChips()
        setupSearchBar()
        setupNotifications()
        setupRetryButton()
        observeViewModel()
    }

    private fun setupNotifications() {
        binding.ivNotifications.setOnClickListener {
            findNavController().navigate(
                R.id.action_customerHomeFragment_to_customerNotificationFragment
            )
        }
    }

    private fun setupRecyclerView() {
        restaurantAdapter = RestaurantAdapter { restaurant ->
            val intent = Intent(requireContext(), RestaurantDetailActivity::class.java).apply {
                putExtra(RestaurantDetailActivity.EXTRA_RESTAURANT_ID, restaurant.id)
            }
            startActivity(intent)
        }

        binding.rvRestaurants.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = restaurantAdapter
        }
    }

    private fun setupCategoryChips() {
        val chips = listOf(
            binding.tvCategoryAll to "Tất cả",
            binding.tvCategoryVietnamese to "Món Việt",
            binding.tvCategoryCoffee to "Cà phê",
            binding.tvCategoryHotpot to "Lẩu & nướng"
        )

        fun selectChip(targetCategory: String) {
            chips.forEach { (chipView, cat) ->
                val isSelected = cat.equals(targetCategory, ignoreCase = true)
                chipView.isSelected = isSelected
                chipView.setTypeface(
                    null,
                    if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
                )
            }
            viewModel.setSelectedCategory(targetCategory)
        }

        selectChip(viewModel.selectedCategory.value ?: "Tất cả")

        chips.forEach { (chipView, cat) ->
            chipView.setOnClickListener {
                selectChip(cat)
            }
        }
    }

    private fun setupSearchBar() {
        binding.llSearchBar.setOnClickListener {
            // Chuyển sang màn hình Tìm kiếm & Lọc
            try {
                findNavController().navigate(R.id.action_customerHomeFragment_to_searchFilterFragment)
            } catch (e: Exception) {
                findNavController().navigate(R.id.searchFilterFragment)
            }
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
                binding.rvRestaurants.visibility = View.GONE
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                UiStateHelper.showError(binding.layoutStates.root, error)
                binding.layoutStates.root.visibility = View.VISIBLE
                binding.rvRestaurants.visibility = View.GONE
                SnackbarHelper.show(binding.root, error) {
                    viewModel.loadRestaurants()
                }
            }
        }

        viewModel.isEmpty.observe(viewLifecycleOwner) { empty ->
            if (empty && viewModel.isLoading.value == false && viewModel.errorMessage.value == null) {
                UiStateHelper.showEmpty(binding.layoutStates.root)
                binding.layoutStates.root.visibility = View.VISIBLE
                binding.rvRestaurants.visibility = View.GONE
            }
        }

        viewModel.filteredRestaurants.observe(viewLifecycleOwner) { list ->
            if (viewModel.isLoading.value == false && viewModel.errorMessage.value == null) {
                binding.layoutStates.root.visibility = View.GONE
                if (list.isEmpty()) {
                    UiStateHelper.showEmpty(binding.layoutStates.root)
                    binding.layoutStates.root.visibility = View.VISIBLE
                    binding.rvRestaurants.visibility = View.GONE
                } else {
                    UiStateHelper.hideAll(binding.layoutStates.root)
                    binding.layoutStates.root.visibility = View.GONE
                    binding.rvRestaurants.visibility = View.VISIBLE
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
