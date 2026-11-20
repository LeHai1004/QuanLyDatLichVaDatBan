package com.ute.quanlydatban.ui.customer.favorite

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.databinding.FragmentFavoriteBinding
import com.ute.quanlydatban.ui.customer.RestaurantDetailActivity
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.FavoriteViewModel

class FavoriteFragment : Fragment() {

    private var _binding: FragmentFavoriteBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(this)[FavoriteViewModel::class.java]
    }

    private lateinit var favoriteAdapter: FavoriteAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.loadFavorites()
    }

    private fun setupToolbar() {
        binding.ivBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        favoriteAdapter = FavoriteAdapter(
            onItemClick = { restaurant ->
                openRestaurantDetail(restaurant)
            },
            onHeartClick = { restaurant ->
                viewModel.removeFavorite(restaurant.id) { success ->
                    if (success) {
                        SnackbarHelper.show(binding.root, getString(R.string.favorite_removed_success))
                    }
                }
            }
        )

        binding.rvFavorites.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = favoriteAdapter
        }
    }

    private fun setupListeners() {
        binding.layoutStates.btnRetry.setOnClickListener {
            viewModel.loadFavorites()
        }

        binding.btnFindRestaurants.setOnClickListener {
            findNavController().navigate(R.id.customerHomeFragment)
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) {
                UiStateHelper.showLoading(binding.layoutStates.root)
                binding.rvFavorites.visibility = View.GONE
                binding.llEmptyState.visibility = View.GONE
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrBlank()) {
                UiStateHelper.showError(binding.layoutStates.root, error)
                binding.rvFavorites.visibility = View.GONE
                binding.llEmptyState.visibility = View.GONE
            }
        }

        viewModel.favorites.observe(viewLifecycleOwner) { list ->
            if (viewModel.isLoading.value == true) return@observe

            if (list.isEmpty()) {
                UiStateHelper.hideAll(binding.layoutStates.root)
                binding.rvFavorites.visibility = View.GONE
                binding.llEmptyState.visibility = View.VISIBLE
            } else {
                UiStateHelper.hideAll(binding.layoutStates.root)
                binding.rvFavorites.visibility = View.VISIBLE
                binding.llEmptyState.visibility = View.GONE
                favoriteAdapter.submitList(list)
            }
        }
    }

    private fun openRestaurantDetail(restaurant: Restaurant) {
        val intent = Intent(requireContext(), RestaurantDetailActivity::class.java).apply {
            putExtra(RestaurantDetailActivity.EXTRA_RESTAURANT_ID, restaurant.id)
            putExtra("restaurant_id", restaurant.id)
            putExtra("id", restaurant.id)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadFavorites()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
