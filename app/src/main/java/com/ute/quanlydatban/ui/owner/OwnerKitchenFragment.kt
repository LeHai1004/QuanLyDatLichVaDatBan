package com.ute.quanlydatban.ui.owner

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ute.quanlydatban.data.repository.OwnerRepository
import com.ute.quanlydatban.databinding.FragmentOwnerKitchenBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.OwnerKitchenViewModel

class OwnerKitchenFragment : Fragment() {

    private var _binding: FragmentOwnerKitchenBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(requireActivity(), OwnerKitchenViewModel.Factory())[OwnerKitchenViewModel::class.java]
    }

    private lateinit var adapter: OwnerKitchenAdapter
    private var restaurantId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOwnerKitchenBinding.inflate(inflater, container, false)
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

        adapter = OwnerKitchenAdapter { item, nextStatus ->
            viewModel.transitionStatus(item, nextStatus) { success, errorMsg ->
                if (!success && !errorMsg.isNullOrBlank()) {
                    SnackbarHelper.show(binding.root, errorMsg)
                }
            }
        }

        binding.rvKitchen.layoutManager = LinearLayoutManager(requireContext())
        binding.rvKitchen.adapter = adapter

        binding.layoutStates.btnRetry.setOnClickListener {
            if (restaurantId.isNotBlank()) {
                viewModel.startListening(restaurantId)
            } else {
                loadRestaurantId()
            }
        }
    }

    private fun checkNetworkStatus() {
        val hasNetwork = isNetworkAvailable()
        binding.tvOfflineBanner.visibility = if (hasNetwork) View.GONE else View.VISIBLE
    }

    private fun isNetworkAvailable(): Boolean {
        val ctx = context ?: return true
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) {
                UiStateHelper.showLoading(binding.layoutStates.root)
            } else {
                UiStateHelper.hideAll(binding.layoutStates.root)
            }
        }

        viewModel.kitchenItems.observe(viewLifecycleOwner) { items ->
            checkNetworkStatus()
            if (items.isEmpty() && viewModel.isLoading.value == false) {
                UiStateHelper.showEmpty(binding.layoutStates.root)
            } else if (viewModel.isLoading.value == false) {
                UiStateHelper.hideAll(binding.layoutStates.root)
            }
            adapter.submitList(items)
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { err ->
            if (!err.isNullOrBlank()) {
                checkNetworkStatus()
                SnackbarHelper.show(binding.root, err) {
                    if (restaurantId.isNotBlank()) viewModel.startListening(restaurantId)
                }
                viewModel.clearError()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        checkNetworkStatus()
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
