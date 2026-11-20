package com.ute.quanlydatban.ui.customer.booking

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ActivityIndoorMapPreviewBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.viewmodel.IndoorMapViewModel

class IndoorMapPreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIndoorMapPreviewBinding

    private val viewModel by lazy {
        ViewModelProvider(this, IndoorMapViewModel.Factory())[IndoorMapViewModel::class.java]
    }

    private var bookingId: String = ""
    private var restaurantId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityIndoorMapPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initIntentData()
        setupListeners()
        observeViewModel()

        viewModel.loadData(bookingId, restaurantId)
    }

    private fun initIntentData() {
        bookingId = intent.getStringExtra(EXTRA_BOOKING_ID).orEmpty()
        restaurantId = intent.getStringExtra(EXTRA_RESTAURANT_ID).orEmpty()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnNotification.setOnClickListener {
            SnackbarHelper.show(binding.root, getString(R.string.feature_coming_soon))
        }

        binding.btnFloor1.setOnClickListener {
            viewModel.selectFloor(1)
        }

        binding.btnFloor2.setOnClickListener {
            viewModel.selectFloor(2)
        }

        // Bấm "Tôi đã đến quán" -> Chuyển sang quét QR vị trí check-in
        binding.btnArrived.setOnClickListener {
            val intent = Intent(this, QRScanActivity::class.java).apply {
                putExtra(QRScanActivity.EXTRA_BOOKING_ID, bookingId)
                putExtra(QRScanActivity.EXTRA_RESTAURANT_ID, restaurantId)
            }
            startActivity(intent)
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.pbLoading.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.bookedTable.observe(this) { table ->
            if (table != null) {
                binding.tvDestinationTitle.text = "${table.name} · Tầng ${table.floor}"
                binding.indoorMapView.setMapData(
                    bookedTable = table,
                    userLocation = null,
                    userFloor = 0,
                    routePoints = emptyList()
                )
            }
        }

        viewModel.selectedFloor.observe(this) { floor ->
            updateFloorTabs(floor)
            binding.indoorMapView.setFloor(floor)
        }

        viewModel.errorMessage.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                val snackbar = Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)
                snackbar.setAction(R.string.indoor_map_btn_retry) {
                    viewModel.loadData(bookingId, restaurantId)
                }
                snackbar.show()
                viewModel.clearError()
            }
        }
    }

    private fun updateFloorTabs(selectedFloor: Long) {
        if (selectedFloor == 1L) {
            binding.btnFloor1.setBackgroundResource(R.drawable.bg_booking_btn_primary)
            binding.btnFloor1.setTextColor(ContextCompat.getColor(this, R.color.white))

            binding.btnFloor2.setBackgroundResource(R.drawable.bg_filter_chip)
            binding.btnFloor2.setTextColor(ContextCompat.getColor(this, R.color.booktable_muted))
        } else {
            binding.btnFloor2.setBackgroundResource(R.drawable.bg_booking_btn_primary)
            binding.btnFloor2.setTextColor(ContextCompat.getColor(this, R.color.white))

            binding.btnFloor1.setBackgroundResource(R.drawable.bg_filter_chip)
            binding.btnFloor1.setTextColor(ContextCompat.getColor(this, R.color.booktable_muted))
        }
    }

    companion object {
        const val EXTRA_BOOKING_ID = "extra_booking_id"
        const val EXTRA_RESTAURANT_ID = "extra_restaurant_id"
    }
}
