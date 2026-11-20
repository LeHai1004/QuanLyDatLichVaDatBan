package com.ute.quanlydatban.ui.customer.booking

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ActivityOrderTrackingBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.viewmodel.OrderTrackingViewModel

class OrderTrackingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderTrackingBinding

    private val viewModel by lazy {
        ViewModelProvider(this, OrderTrackingViewModel.Factory())[OrderTrackingViewModel::class.java]
    }

    private lateinit var adapter: OrderTrackingAdapter

    private var bookingId: String = ""
    private var restaurantId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityOrderTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initIntentData()
        setupUI()
        observeViewModel()
    }

    private fun initIntentData() {
        bookingId = intent.getStringExtra(EXTRA_BOOKING_ID).orEmpty()
        restaurantId = intent.getStringExtra(EXTRA_RESTAURANT_ID).orEmpty()

        if (bookingId.isEmpty()) {
            SnackbarHelper.show(binding.root, "Thiếu thông tin đơn đặt bàn.")
        }
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        adapter = OrderTrackingAdapter()
        binding.rvOrderTracking.layoutManager = LinearLayoutManager(this)
        binding.rvOrderTracking.adapter = adapter

        binding.btnCallStaff.setOnClickListener {
            val bottomSheet = CustomerServiceRequestBottomSheet.newInstance(bookingId, restaurantId)
            bottomSheet.show(supportFragmentManager, "SERVICE_REQUEST_BOTTOM_SHEET")
        }

        binding.layoutStates.btnRetry.setOnClickListener {
            viewModel.startListening(bookingId)
        }
    }

    private fun checkNetworkStatus() {
        val hasNetwork = isNetworkAvailable()
        binding.tvOfflineBanner.visibility = if (hasNetwork) View.GONE else View.VISIBLE
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            if (loading) {
                UiStateHelper.showLoading(binding.layoutStates.root)
            } else {
                UiStateHelper.hideAll(binding.layoutStates.root)
            }
        }

        viewModel.isServiceCompleted.observe(this) { completed ->
            if (completed) {
                binding.tvProgressTitle.text = getString(R.string.order_tracking_service_completed)
            } else {
                binding.tvProgressTitle.text = getString(R.string.order_tracking_service_in_progress)
            }
        }

        viewModel.orderItems.observe(this) { items ->
            checkNetworkStatus()
            if (items.isEmpty() && viewModel.isLoading.value == false) {
                UiStateHelper.showEmpty(binding.layoutStates.root)
            } else if (viewModel.isLoading.value == false) {
                UiStateHelper.hideAll(binding.layoutStates.root)
            }
            adapter.submitList(items)
        }

        viewModel.errorMessage.observe(this) { err ->
            if (!err.isNullOrBlank()) {
                checkNetworkStatus()
                SnackbarHelper.show(binding.root, err) {
                    viewModel.startListening(bookingId)
                }
                viewModel.clearError()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        checkNetworkStatus()
        if (bookingId.isNotBlank()) {
            viewModel.startListening(bookingId)
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopListening()
    }

    companion object {
        const val EXTRA_BOOKING_ID = "extra_booking_id"
        const val EXTRA_RESTAURANT_ID = "extra_restaurant_id"
    }
}
