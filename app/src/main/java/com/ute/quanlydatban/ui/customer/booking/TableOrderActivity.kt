package com.ute.quanlydatban.ui.customer.booking

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ActivityTableOrderBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.util.formatMoney
import com.ute.quanlydatban.viewmodel.TableOrderViewModel

class TableOrderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTableOrderBinding

    private val viewModel by lazy {
        ViewModelProvider(this, TableOrderViewModel.Factory())[TableOrderViewModel::class.java]
    }

    private lateinit var adapter: TableOrderAdapter

    private var bookingId: String = ""
    private var restaurantId: String = ""
    private var tableId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTableOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initIntentData()
        setupUI()
        observeViewModel()

        if (restaurantId.isNotBlank()) {
            viewModel.loadMenu(restaurantId)
        }
    }

    private fun initIntentData() {
        bookingId = intent.getStringExtra(EXTRA_BOOKING_ID).orEmpty()
        restaurantId = intent.getStringExtra(EXTRA_RESTAURANT_ID).orEmpty()
        tableId = intent.getStringExtra(EXTRA_TABLE_ID).orEmpty()

        if (bookingId.isEmpty() || restaurantId.isEmpty()) {
            SnackbarHelper.show(binding.root, "Thiếu thông tin đặt bàn.")
            finish()
        }
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        adapter = TableOrderAdapter { menuItem, delta ->
            viewModel.updateQuantity(menuItem.id, delta)
        }

        binding.rvMenu.layoutManager = LinearLayoutManager(this)
        binding.rvMenu.adapter = adapter

        binding.btnSubmitOrder.setOnClickListener {
            viewModel.submitOrder(bookingId)
        }

        binding.layoutStates.btnRetry.setOnClickListener {
            viewModel.loadMenu(restaurantId)
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.btnSubmitOrder.isEnabled = !loading
            if (loading) {
                UiStateHelper.showLoading(binding.layoutStates.root)
            } else {
                UiStateHelper.hideAll(binding.layoutStates.root)
            }
        }

        viewModel.menuItems.observe(this) { items ->
            if (items.isEmpty() && viewModel.isLoading.value == false) {
                UiStateHelper.showEmpty(binding.layoutStates.root)
            } else if (viewModel.isLoading.value == false) {
                UiStateHelper.hideAll(binding.layoutStates.root)
            }
            adapter.submitList(items, viewModel.quantities.value.orEmpty())
        }

        viewModel.quantities.observe(this) { qMap ->
            adapter.submitList(viewModel.menuItems.value.orEmpty(), qMap)
        }

        viewModel.totalPrice.observe(this) { total ->
            binding.tvTotalPrice.text = formatMoney(total)
        }

        viewModel.orderSuccess.observe(this) { success ->
            if (success) {
                val intent = Intent(this, OrderTrackingActivity::class.java).apply {
                    putExtra(OrderTrackingActivity.EXTRA_BOOKING_ID, bookingId)
                    putExtra(OrderTrackingActivity.EXTRA_RESTAURANT_ID, restaurantId)
                }
                startActivity(intent)
                viewModel.clearMessages()
                finish()
            }
        }

        viewModel.errorMessage.observe(this) { err ->
            if (!err.isNullOrBlank()) {
                SnackbarHelper.show(binding.root, err) {
                    viewModel.loadMenu(restaurantId)
                }
                viewModel.clearMessages()
            }
        }
    }

    companion object {
        const val EXTRA_BOOKING_ID = "extra_booking_id"
        const val EXTRA_RESTAURANT_ID = "extra_restaurant_id"
        const val EXTRA_TABLE_ID = "extra_table_id"
    }
}
