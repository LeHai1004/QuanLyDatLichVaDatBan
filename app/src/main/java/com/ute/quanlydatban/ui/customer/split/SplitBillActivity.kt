package com.ute.quanlydatban.ui.customer.split

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ActivitySplitBillBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.formatMoney
import com.ute.quanlydatban.viewmodel.SplitBillViewModel

class SplitBillActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOOKING_ID = "extra_booking_id"
        const val EXTRA_TOTAL_AMOUNT = "extra_total_amount"

        fun newIntent(context: Context, bookingId: String, totalAmount: Long): Intent {
            return Intent(context, SplitBillActivity::class.java).apply {
                putExtra(EXTRA_BOOKING_ID, bookingId)
                putExtra(EXTRA_TOTAL_AMOUNT, totalAmount)
            }
        }
    }

    private lateinit var binding: ActivitySplitBillBinding
    private val viewModel: SplitBillViewModel by viewModels { SplitBillViewModel.Factory }

    private lateinit var personAdapter: SplitPersonAdapter
    private lateinit var dishAdapter: SplitDishAdapter

    private var currentBookingId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySplitBillBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        currentBookingId = intent.getStringExtra(EXTRA_BOOKING_ID).orEmpty()
        val totalAmount = intent.getLongExtra(EXTRA_TOTAL_AMOUNT, 0L)

        if (currentBookingId.isBlank()) {
            Toast.makeText(this, "Thiếu mã đơn đặt bàn để chia hóa đơn", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar()
        setupModeTabs()
        setupGuestCounter()
        setupRecyclerViews()
        setupFinishButton()
        observeViewModel()

        viewModel.initialize(currentBookingId, totalAmount)
    }

    private fun setupToolbar() {
        binding.ivBack.setOnClickListener {
            finish()
        }
    }

    private fun setupModeTabs() {
        binding.llTabEqual.setOnClickListener {
            viewModel.setMode(SplitBillViewModel.MODE_EQUAL)
        }

        binding.llTabItems.setOnClickListener {
            viewModel.setMode(SplitBillViewModel.MODE_ITEMS)
        }
    }

    private fun updateModeTabVisuals(mode: String) {
        val isEqual = (mode == SplitBillViewModel.MODE_EQUAL)

        if (isEqual) {
            binding.llTabEqual.setBackgroundResource(R.drawable.bg_owner_payment_method_selected)
            binding.tvTabEqual.setTextColor(ContextCompat.getColor(this, R.color.booktable_orange))
            binding.ivIconEqual.setColorFilter(ContextCompat.getColor(this, R.color.booktable_orange))

            binding.llTabItems.setBackgroundResource(0)
            binding.tvTabItems.setTextColor(ContextCompat.getColor(this, R.color.booktable_muted))
            binding.ivIconItems.setColorFilter(ContextCompat.getColor(this, R.color.booktable_muted))

            binding.llModeEqualContainer.visibility = View.VISIBLE
            binding.llModeItemsContainer.visibility = View.GONE
        } else {
            binding.llTabEqual.setBackgroundResource(0)
            binding.tvTabEqual.setTextColor(ContextCompat.getColor(this, R.color.booktable_muted))
            binding.ivIconEqual.setColorFilter(ContextCompat.getColor(this, R.color.booktable_muted))

            binding.llTabItems.setBackgroundResource(R.drawable.bg_owner_payment_method_selected)
            binding.tvTabItems.setTextColor(ContextCompat.getColor(this, R.color.booktable_orange))
            binding.ivIconItems.setColorFilter(ContextCompat.getColor(this, R.color.booktable_orange))

            binding.llModeEqualContainer.visibility = View.GONE
            binding.llModeItemsContainer.visibility = View.VISIBLE
        }
    }

    private fun setupGuestCounter() {
        binding.btnMinusGuest.setOnClickListener {
            val count = viewModel.guestCount.value ?: 2
            if (count <= 2) {
                SnackbarHelper.show(binding.root, getString(R.string.split_bill_error_min_guests))
            } else {
                viewModel.changeGuestCount(-1)
            }
        }

        binding.btnPlusGuest.setOnClickListener {
            viewModel.changeGuestCount(1)
        }
    }

    private fun setupRecyclerViews() {
        personAdapter = SplitPersonAdapter { person ->
            showPaymentMethodDialog(person)
        }
        binding.rvPersons.apply {
            layoutManager = LinearLayoutManager(this@SplitBillActivity)
            adapter = personAdapter
        }

        dishAdapter = SplitDishAdapter { dishId, personIndex ->
            viewModel.assignDish(dishId, personIndex)
        }
        binding.rvOrderItems.apply {
            layoutManager = LinearLayoutManager(this@SplitBillActivity)
            adapter = dishAdapter
        }
    }

    private fun showPaymentMethodDialog(person: SplitPersonUiModel) {
        val amountStr = formatMoney(person.shareAmount)
        val title = getString(R.string.split_bill_dialog_method_title)
        val msg = getString(R.string.split_bill_dialog_method_msg, amountStr, person.name)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton(getString(R.string.split_bill_dialog_btn_cash)) { _, _ ->
                processPayment(person, "cash")
            }
            .setNeutralButton(getString(R.string.split_bill_dialog_btn_vietqr)) { _, _ ->
                processPayment(person, "vietqr")
            }
            .setNegativeButton(getString(R.string.split_bill_dialog_btn_cancel), null)
            .show()

        val orangeColor = ContextCompat.getColor(this, R.color.booktable_orange)
        val mutedColor = ContextCompat.getColor(this, R.color.booktable_muted)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(orangeColor)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(orangeColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(mutedColor)
    }

    private fun processPayment(person: SplitPersonUiModel, method: String) {
        viewModel.payPersonShare(person, method) { success, errorMsg ->
            if (success) {
                SnackbarHelper.show(
                    binding.root,
                    getString(R.string.split_bill_pay_success_format, person.name)
                )
            } else {
                val err = errorMsg ?: getString(R.string.state_error)
                SnackbarHelper.show(binding.root, err)
            }
        }
    }

    private fun setupFinishButton() {
        binding.btnFinishSplit.setOnClickListener {
            val persons = viewModel.persons.value.orEmpty()
            val unpaidCount = persons.count { !it.isPaid }

            if (unpaidCount > 0) {
                SnackbarHelper.show(
                    binding.root,
                    getString(R.string.split_bill_error_unpaid_remaining, unpaidCount)
                )
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.split_bill_all_completed_success),
                    Toast.LENGTH_LONG
                ).show()
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.mode.observe(this) { mode ->
            updateModeTabVisuals(mode)
        }

        viewModel.totalPayable.observe(this) { total ->
            binding.tvTotalPayable.text = formatMoney(total)
        }

        viewModel.guestCount.observe(this) { count ->
            binding.tvGuestCount.text = getString(R.string.split_bill_person_count_format, count)
            binding.tvPersonsListHeader.text = "Danh sách người chia ($count người)"
        }

        viewModel.remainderNotice.observe(this) { notice ->
            if (notice != null && viewModel.mode.value == SplitBillViewModel.MODE_EQUAL) {
                binding.llRemainderNotice.visibility = View.VISIBLE
                binding.tvRemainderNotice.text = notice
            } else {
                binding.llRemainderNotice.visibility = View.GONE
            }
        }

        viewModel.itemAssignError.observe(this) { err ->
            if (err != null && viewModel.mode.value == SplitBillViewModel.MODE_ITEMS) {
                binding.tvItemAssignError.visibility = View.VISIBLE
                binding.tvItemAssignError.text = err
            } else {
                binding.tvItemAssignError.visibility = View.GONE
            }
        }

        viewModel.persons.observe(this) { list ->
            personAdapter.submitList(list)

            val paid = list.count { it.isPaid }
            val total = list.size
            binding.tvPaidProgress.text = getString(R.string.split_bill_paid_summary_format, paid, total)

            // Refresh dish adapter options with person names
            val items = viewModel.orderItems.value.orEmpty()
            val names = list.map { it.name }
            val assignments = viewModel.itemAssignments.value.orEmpty()
            dishAdapter.submitData(items, names, assignments)
        }

        viewModel.orderItems.observe(this) { items ->
            val persons = viewModel.persons.value.orEmpty()
            val names = persons.map { it.name }
            val assignments = viewModel.itemAssignments.value.orEmpty()
            dishAdapter.submitData(items, names, assignments)
        }

        viewModel.errorMessage.observe(this) { err ->
            if (!err.isNullOrBlank()) {
                SnackbarHelper.show(binding.root, err)
            }
        }

        viewModel.allPaid.observe(this) { allDone ->
            if (allDone) {
                binding.btnFinishSplit.text = getString(R.string.split_bill_all_completed_success)
            } else {
                binding.btnFinishSplit.text = getString(R.string.split_bill_btn_finish)
            }
        }
    }

    override fun finish() {
        val persons = viewModel.persons.value.orEmpty()
        if (persons.isNotEmpty() && persons.all { it.isPaid }) {
            setResult(RESULT_OK)
        }
        super.finish()
    }
}
