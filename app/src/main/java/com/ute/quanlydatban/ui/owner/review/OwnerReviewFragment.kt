package com.ute.quanlydatban.ui.owner.review

import android.graphics.Typeface
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
import com.ute.quanlydatban.databinding.FragmentOwnerReviewBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.viewmodel.OwnerReviewViewModel
import java.util.Locale

class OwnerReviewFragment : Fragment() {

    private var _binding: FragmentOwnerReviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(this, OwnerReviewViewModel.Factory)[OwnerReviewViewModel::class.java]
    }

    private lateinit var reviewAdapter: OwnerReviewAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOwnerReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val restaurantId = arguments?.getString("restaurantId")

        setupToolbar()
        setupRecyclerView()
        setupFilterChips()
        setupRetryButton()
        observeViewModel()

        viewModel.initialize(restaurantId)
    }

    private fun setupToolbar() {
        binding.ivBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        reviewAdapter = OwnerReviewAdapter()
        binding.rvReviews.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = reviewAdapter
        }
    }

    private fun setupFilterChips() {
        val filterMap = listOf(
            binding.tvFilterAll to null,
            binding.tvFilter5Star to 5,
            binding.tvFilter4Star to 4,
            binding.tvFilter3Star to 3,
            binding.tvFilter2Star to 2,
            binding.tvFilter1Star to 1
        )

        fun updateChipSelection(selectedStar: Int?) {
            filterMap.forEach { (chip, star) ->
                val isSelected = (star == selectedStar)
                chip.isSelected = isSelected
                chip.setTypeface(
                    null,
                    if (isSelected) Typeface.BOLD else Typeface.NORMAL
                )
            }
        }

        updateChipSelection(null)

        filterMap.forEach { (chip, star) ->
            chip.setOnClickListener {
                updateChipSelection(star)
                viewModel.setStarFilter(star)
            }
        }
    }

    private fun setupRetryButton() {
        binding.btnRetry.setOnClickListener {
            viewModel.retry()
        }
    }

    private fun observeViewModel() {
        viewModel.displayedReviews.observe(viewLifecycleOwner) { list ->
            reviewAdapter.submitList(list)
        }

        viewModel.averageRating.observe(viewLifecycleOwner) { avg ->
            binding.tvAverageRating.text = String.format(Locale.getDefault(), "%.1f", avg)
            updateAverageStarVisuals(avg)
        }

        viewModel.totalReviewCount.observe(viewLifecycleOwner) { total ->
            binding.tvTotalReviews.text = if (total > 0) {
                getString(R.string.owner_review_based_on_format, total)
            } else {
                getString(R.string.owner_review_empty_title)
            }
        }

        viewModel.starCounts.observe(viewLifecycleOwner) { counts ->
            val total = viewModel.totalReviewCount.value ?: 0
            binding.tvFilterAll.text = getString(R.string.owner_review_filter_all, total)
            binding.tvFilter5Star.text = getString(R.string.owner_review_filter_star_format, 5, counts[5] ?: 0)
            binding.tvFilter4Star.text = getString(R.string.owner_review_filter_star_format, 4, counts[4] ?: 0)
            binding.tvFilter3Star.text = getString(R.string.owner_review_filter_star_format, 3, counts[3] ?: 0)
            binding.tvFilter2Star.text = getString(R.string.owner_review_filter_star_format, 2, counts[2] ?: 0)
            binding.tvFilter1Star.text = getString(R.string.owner_review_filter_star_format, 1, counts[1] ?: 0)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.llLoadingState.visibility = if (loading) View.VISIBLE else View.GONE
            if (loading) {
                binding.llErrorState.visibility = View.GONE
                binding.llEmptyState.visibility = View.GONE
                binding.rvReviews.visibility = View.GONE
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrBlank()) {
                binding.llErrorState.visibility = View.VISIBLE
                binding.tvErrorMessage.text = error
                binding.rvReviews.visibility = View.GONE
                binding.llEmptyState.visibility = View.GONE
                SnackbarHelper.show(binding.root, error)
            } else {
                binding.llErrorState.visibility = View.GONE
            }
        }

        viewModel.isEmpty.observe(viewLifecycleOwner) { empty ->
            if (empty && viewModel.isLoading.value == false && viewModel.errorMessage.value == null) {
                binding.llEmptyState.visibility = View.VISIBLE
                binding.rvReviews.visibility = View.GONE
                val isFilter = viewModel.isEmptyDueToFilter.value == true
                binding.tvEmptyMessage.text = if (isFilter) {
                    getString(R.string.owner_review_empty_filter_message)
                } else {
                    getString(R.string.owner_review_empty_message)
                }
            } else if (!empty) {
                binding.llEmptyState.visibility = View.GONE
                binding.rvReviews.visibility = View.VISIBLE
            }
        }
    }

    private fun updateAverageStarVisuals(avg: Double) {
        val stars = listOf(
            binding.ivAvgStar1,
            binding.ivAvgStar2,
            binding.ivAvgStar3,
            binding.ivAvgStar4,
            binding.ivAvgStar5
        )

        stars.forEachIndexed { index, starView ->
            val starIndex = index + 1
            if (avg >= starIndex) {
                starView.setImageResource(R.drawable.ic_star)
            } else {
                starView.setImageResource(R.drawable.ic_star_outline)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
