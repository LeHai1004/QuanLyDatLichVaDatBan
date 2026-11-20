package com.ute.quanlydatban.ui.owner.waitlist

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.WaitlistEntry
import com.ute.quanlydatban.data.repository.OwnerRepository
import com.ute.quanlydatban.databinding.FragmentOwnerWaitlistBinding
import com.ute.quanlydatban.ui.customer.waitlist.WaitlistAdapter
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.WaitlistViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class OwnerWaitlistFragment : Fragment() {

    private var _binding: FragmentOwnerWaitlistBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(this, WaitlistViewModel.Factory())[WaitlistViewModel::class.java]
    }

    private lateinit var waitlistAdapter: WaitlistAdapter
    private var restaurantId: String = ""
    private var selectedDate: String = ""
    private var currentFilter: String = "all"
    private var fullEntries: List<WaitlistEntry> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOwnerWaitlistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        selectedDate = sdf.format(cal.time)

        setupDateView()
        setupFilterChips()
        setupRecyclerView()
        setupRetryButton()
        observeViewModel()

        loadRestaurantAndWaitlist()
    }

    private fun setupDateView() {
        binding.tvOwnerDate.text = "Ngày: $selectedDate"
        binding.btnOwnerChangeDate.setOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        val parts = selectedDate.split("/")
        if (parts.size == 3) {
            parts[0].toIntOrNull()?.let { cal.set(Calendar.DAY_OF_MONTH, it) }
            parts[1].toIntOrNull()?.let { cal.set(Calendar.MONTH, it - 1) }
            parts[2].toIntOrNull()?.let { cal.set(Calendar.YEAR, it) }
        }

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                selectedDate = String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month + 1, year)
                binding.tvOwnerDate.text = "Ngày: $selectedDate"
                if (restaurantId.isNotBlank()) {
                    viewModel.listenOwnerWaitlist(restaurantId, selectedDate)
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun setupFilterChips() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when {
                checkedIds.contains(R.id.chipWaiting) -> "waiting"
                checkedIds.contains(R.id.chipNotified) -> "notified"
                checkedIds.contains(R.id.chipHandled) -> "handled"
                else -> "all"
            }
            applyFilter()
        }
    }

    private fun applyFilter() {
        val filtered = when (currentFilter) {
            "waiting" -> fullEntries.filter { it.status == "waiting" && !it.handled }
            "notified" -> fullEntries.filter { it.status == "notified" && !it.handled }
            "handled" -> fullEntries.filter { it.handled }
            else -> fullEntries
        }

        waitlistAdapter.submitList(filtered)
        if (filtered.isEmpty()) {
            UiStateHelper.showEmpty(binding.layoutStates.root)
            binding.rvOwnerWaitlist.visibility = View.GONE
        } else {
            UiStateHelper.hideAll(binding.layoutStates.root)
            binding.rvOwnerWaitlist.visibility = View.VISIBLE
        }
    }

    private fun setupRecyclerView() {
        waitlistAdapter = WaitlistAdapter(
            isOwnerMode = true,
            onCallClicked = { phone ->
                try {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                    startActivity(dialIntent)
                } catch (_: Exception) {
                    SnackbarHelper.show(binding.root, "Không thể mở ứng dụng cuộc gọi")
                }
            },
            onMarkHandledClicked = { entry ->
                viewModel.markHandled(entry.id) { success ->
                    if (success) {
                        SnackbarHelper.show(binding.root, getString(R.string.waitlist_owner_marked_success))
                    }
                }
            }
        )

        binding.rvOwnerWaitlist.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = waitlistAdapter
        }
    }

    private fun setupRetryButton() {
        binding.layoutStates.btnRetry.setOnClickListener {
            loadRestaurantAndWaitlist()
        }
    }

    private fun loadRestaurantAndWaitlist() {
        val argRestaurantId = arguments?.getString("restaurantId").orEmpty()
        if (argRestaurantId.isNotBlank()) {
            restaurantId = argRestaurantId
            viewModel.listenOwnerWaitlist(restaurantId, selectedDate)
        } else {
            UiStateHelper.showLoading(binding.layoutStates.root)
            OwnerRepository().getOwnerRestaurant { result ->
                result.fold(
                    onSuccess = { restaurant ->
                        restaurantId = restaurant.id
                        viewModel.listenOwnerWaitlist(restaurantId, selectedDate)
                    },
                    onFailure = { err ->
                        UiStateHelper.showError(binding.layoutStates.root, err.message ?: getString(R.string.state_error))
                    }
                )
            }
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading && fullEntries.isEmpty()) {
                UiStateHelper.showLoading(binding.layoutStates.root)
                binding.rvOwnerWaitlist.visibility = View.GONE
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { err ->
            if (err != null && fullEntries.isEmpty()) {
                UiStateHelper.showError(binding.layoutStates.root, err)
                binding.rvOwnerWaitlist.visibility = View.GONE
            }
        }

        viewModel.ownerWaitlist.observe(viewLifecycleOwner) { entries ->
            fullEntries = entries
            applyFilter()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
