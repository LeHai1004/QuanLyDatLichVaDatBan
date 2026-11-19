package com.ute.quanlydatban.ui.customer.booking

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.FragmentBookingBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.viewmodel.BookingViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class BookingFragment : Fragment() {

    private var _binding: FragmentBookingBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(requireActivity(), BookingViewModel.Factory())[BookingViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initDataFromArgs()
        setupHeader()
        setupDateOptions()
        setupTimeOptions()
        setupGuestOptions()
        setupNoteField()
        setupContinueButton()
        observeViewModel()
    }

    private fun initDataFromArgs() {
        val restaurantId = arguments?.getString(BookingActivity.EXTRA_RESTAURANT_ID)
            ?: activity?.intent?.getStringExtra(BookingActivity.EXTRA_RESTAURANT_ID).orEmpty()

        if (restaurantId.isEmpty()) {
            SnackbarHelper.show(binding.root, getString(R.string.restaurant_not_found))
            activity?.finish()
            return
        }

        val name = arguments?.getString(BookingActivity.EXTRA_RESTAURANT_NAME)
            ?: activity?.intent?.getStringExtra(BookingActivity.EXTRA_RESTAURANT_NAME)
        val address = arguments?.getString(BookingActivity.EXTRA_RESTAURANT_ADDRESS)
            ?: activity?.intent?.getStringExtra(BookingActivity.EXTRA_RESTAURANT_ADDRESS)
        val openTime = arguments?.getString(BookingActivity.EXTRA_RESTAURANT_OPEN_TIME)
            ?: activity?.intent?.getStringExtra(BookingActivity.EXTRA_RESTAURANT_OPEN_TIME)
        val closeTime = arguments?.getString(BookingActivity.EXTRA_RESTAURANT_CLOSE_TIME)
            ?: activity?.intent?.getStringExtra(BookingActivity.EXTRA_RESTAURANT_CLOSE_TIME)
        val deposit = arguments?.getLong(BookingActivity.EXTRA_DEPOSIT_AMOUNT, 100_000L)
            ?: activity?.intent?.getLongExtra(BookingActivity.EXTRA_DEPOSIT_AMOUNT, 100_000L)

        viewModel.initRestaurant(restaurantId, name, address, openTime, closeTime, deposit)
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener {
            // Nút quay lại ở BookingFragment phải về RestaurantDetail
            activity?.finish()
        }
    }

    private fun setupDateOptions() {
        val container = binding.llDateOptions
        container.removeAllViews()

        val calendar = Calendar.getInstance()
        val fullDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dayNumFormat = SimpleDateFormat("dd", Locale.getDefault())
        val monthYearFormat = SimpleDateFormat("'Tháng' MM, yyyy", Locale("vi", "VN"))

        binding.tvMonthYear.text = monthYearFormat.format(calendar.time)

        val daysOfWeekVi = mapOf(
            Calendar.MONDAY to "T2",
            Calendar.TUESDAY to "T3",
            Calendar.WEDNESDAY to "T4",
            Calendar.THURSDAY to "T5",
            Calendar.FRIDAY to "T6",
            Calendar.SATURDAY to "T7",
            Calendar.SUNDAY to "CN"
        )

        val dateViews = mutableListOf<Pair<String, View>>()

        for (i in 0 until 7) {
            val dateStr = fullDateFormat.format(calendar.time)
            val dayNum = dayNumFormat.format(calendar.time)
            val dow = daysOfWeekVi[calendar.get(Calendar.DAY_OF_WEEK)] ?: "T2"

            val cell = LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, resources.getDimensionPixelSize(R.dimen.booking_date_cell_height), 1f).apply {
                    setMargins(3, 0, 3, 0)
                }
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_booking_date_normal)
                isClickable = true
                isFocusable = true
            }

            val tvDow = TextView(requireContext()).apply {
                text = dow
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(requireContext(), R.color.booktable_muted))
            }

            val tvDay = TextView(requireContext()).apply {
                text = dayNum
                textSize = 16f
                gravity = Gravity.CENTER
                paint.isFakeBoldText = true
                setTextColor(ContextCompat.getColor(requireContext(), R.color.booktable_dark))
            }

            cell.addView(tvDow)
            cell.addView(tvDay)

            cell.setOnClickListener {
                viewModel.setDate(dateStr)
            }

            container.addView(cell)
            dateViews.add(dateStr to cell)

            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Chọn ngày đầu tiên (hôm nay) làm mặc định nếu chưa chọn
        if (viewModel.selectedDate.value == null && dateViews.isNotEmpty()) {
            viewModel.setDate(dateViews.first().first)
        }

        viewModel.selectedDate.observe(viewLifecycleOwner) { selected ->
            dateViews.forEach { (dateStr, view) ->
                val isSelected = dateStr == selected
                val cell = view as LinearLayout
                val tvDow = cell.getChildAt(0) as TextView
                val tvDay = cell.getChildAt(1) as TextView

                if (isSelected) {
                    cell.setBackgroundResource(R.drawable.bg_booking_date_selected)
                    tvDow.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                    tvDay.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                } else {
                    cell.setBackgroundResource(R.drawable.bg_booking_date_normal)
                    tvDow.setTextColor(ContextCompat.getColor(requireContext(), R.color.booktable_muted))
                    tvDay.setTextColor(ContextCompat.getColor(requireContext(), R.color.booktable_dark))
                }
            }
        }
    }

    private fun setupTimeOptions() {
        val container = binding.flTimeOptions
        container.removeAllViews()

        val timeSlots = listOf(
            "11:00", "11:30", "12:00", "12:30",
            "17:30", "18:00", "18:30", "19:00", "19:30", "20:00", "20:30"
        )

        val timeViews = mutableListOf<Pair<String, TextView>>()

        timeSlots.forEach { timeStr ->
            val chip = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    resources.getDimensionPixelSize(R.dimen.booking_chip_height)
                ).apply {
                    marginEnd = resources.getDimensionPixelSize(R.dimen.booking_chip_gap)
                }
                text = timeStr
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(
                    resources.getDimensionPixelSize(R.dimen.booking_chip_padding_h),
                    0,
                    resources.getDimensionPixelSize(R.dimen.booking_chip_padding_h),
                    0
                )
                setBackgroundResource(R.drawable.bg_booking_chip_normal)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.booktable_muted))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    viewModel.setTime(timeStr)
                }
            }

            container.addView(chip)
            timeViews.add(timeStr to chip)
        }

        // Mặc định chọn 18:30 như Figma nếu chưa chọn
        if (viewModel.selectedTime.value == null) {
            viewModel.setTime("18:30")
        }

        viewModel.selectedTime.observe(viewLifecycleOwner) { selected ->
            timeViews.forEach { (timeStr, chip) ->
                val isSelected = timeStr == selected
                if (isSelected) {
                    chip.setBackgroundResource(R.drawable.bg_booking_chip_selected)
                    chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                    chip.paint.isFakeBoldText = true
                } else {
                    chip.setBackgroundResource(R.drawable.bg_booking_chip_normal)
                    chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.booktable_muted))
                    chip.paint.isFakeBoldText = false
                }
            }
        }
    }

    private fun setupGuestOptions() {
        val container = binding.llGuestOptions
        container.removeAllViews()

        val guestCounts = listOf(
            2L to getString(R.string.booking_guest_format, 2),
            4L to getString(R.string.booking_guest_format, 4),
            6L to getString(R.string.booking_guest_format, 6),
            8L to getString(R.string.booking_guest_8plus)
        )

        val guestViews = mutableListOf<Pair<Long, TextView>>()

        guestCounts.forEach { (count, label) ->
            val chip = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    resources.getDimensionPixelSize(R.dimen.booking_chip_height)
                ).apply {
                    marginEnd = resources.getDimensionPixelSize(R.dimen.booking_chip_gap)
                }
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(
                    resources.getDimensionPixelSize(R.dimen.booking_chip_padding_h),
                    0,
                    resources.getDimensionPixelSize(R.dimen.booking_chip_padding_h),
                    0
                )
                setBackgroundResource(R.drawable.bg_booking_chip_normal)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.booktable_muted))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    viewModel.setGuests(count)
                }
            }

            container.addView(chip)
            guestViews.add(count to chip)
        }

        viewModel.selectedGuests.observe(viewLifecycleOwner) { selectedCount ->
            guestViews.forEach { (count, chip) ->
                val isSelected = count == selectedCount
                if (isSelected) {
                    chip.setBackgroundResource(R.drawable.bg_booking_chip_selected)
                    chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                    chip.paint.isFakeBoldText = true
                } else {
                    chip.setBackgroundResource(R.drawable.bg_booking_chip_normal)
                    chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.booktable_muted))
                    chip.paint.isFakeBoldText = false
                }
            }
        }
    }

    private fun setupNoteField() {
        binding.etNote.setText(viewModel.note.value.orEmpty())
        binding.etNote.doAfterTextChanged { text ->
            viewModel.setNote(text?.toString().orEmpty())
        }
    }

    private fun setupContinueButton() {
        binding.btnContinue.setOnClickListener {
            if (viewModel.validateStep1()) {
                val restaurantId = viewModel.restaurant.value?.id.orEmpty()
                val bundle = Bundle().apply {
                    putString("restaurantId", restaurantId)
                }
                findNavController().navigate(
                    R.id.action_bookingFragment_to_tableSelectFragment,
                    bundle
                )
            } else {
                val error = viewModel.errorMessage.value ?: getString(R.string.booking_error_no_date)
                SnackbarHelper.show(binding.root, error)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.restaurant.observe(viewLifecycleOwner) { res ->
            if (res != null) {
                binding.tvRestaurantName.text = res.name
                binding.tvRestaurantAddress.text = res.address
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                SnackbarHelper.show(binding.root, err)
                viewModel.clearMessages()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
