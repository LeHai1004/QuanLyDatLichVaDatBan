package com.ute.quanlydatban.ui.owner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ute.quanlydatban.data.repository.OwnerRepository
import com.ute.quanlydatban.databinding.FragmentOwnerServiceRequestBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.OwnerServiceRequestViewModel

class OwnerServiceRequestFragment : Fragment() {

    private var _binding: FragmentOwnerServiceRequestBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(requireActivity(), OwnerServiceRequestViewModel.Factory())[OwnerServiceRequestViewModel::class.java]
    }

    private lateinit var adapter: OwnerServiceRequestAdapter
    private var restaurantId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOwnerServiceRequestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        observeViewModel()
        loadRestaurantId()
    }

    private fun loadRestaurantId() {
        OwnerRepository().getOwnerRestaurant { result ->
            result.onSuccess { res ->
                restaurantId = res.id
                if (restaurantId.isNotBlank()) {
                    viewModel.startListening(restaurantId)
                }
            }.onFailure {
                UiStateHelper.showError(binding.layoutStates.root, "Không thể xác thực thông tin quán.")
            }
        }
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        adapter = OwnerServiceRequestAdapter { item ->
            viewModel.markHandled(item.request.id) { success, errorMsg ->
                if (!success && !errorMsg.isNullOrBlank()) {
                    SnackbarHelper.show(binding.root, errorMsg)
                }
            }
        }

        binding.rvRequests.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRequests.adapter = adapter

        binding.layoutStates.btnRetry.setOnClickListener {
            if (restaurantId.isNotBlank()) {
                viewModel.startListening(restaurantId)
            } else {
                loadRestaurantId()
            }
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

        viewModel.requests.observe(viewLifecycleOwner) { list ->
            if (list.isEmpty() && viewModel.isLoading.value == false) {
                UiStateHelper.showEmpty(binding.layoutStates.root)
            } else if (viewModel.isLoading.value == false) {
                UiStateHelper.hideAll(binding.layoutStates.root)
            }
            adapter.submitList(list)
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { err ->
            if (!err.isNullOrBlank()) {
                SnackbarHelper.show(binding.root, err) {
                    if (restaurantId.isNotBlank()) viewModel.startListening(restaurantId)
                }
                viewModel.clearError()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (restaurantId.isNotBlank()) {
            viewModel.startListening(restaurantId)
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopListening()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
