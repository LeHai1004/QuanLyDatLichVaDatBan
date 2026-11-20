package com.ute.quanlydatban.ui.customer.booking

import android.content.Intent
import android.graphics.PointF
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ActivityIndoorMapBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.viewmodel.IndoorMapViewModel

class IndoorMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIndoorMapBinding

    private val viewModel by lazy {
        ViewModelProvider(this, IndoorMapViewModel.Factory())[IndoorMapViewModel::class.java]
    }

    private var bookingId: String = ""
    private var restaurantId: String = ""
    private var initialQrCode: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityIndoorMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initIntentData()
        setupListeners()
        observeViewModel()

        viewModel.loadData(bookingId, restaurantId, initialQrCode.ifEmpty { null })
    }

    private fun initIntentData() {
        bookingId = intent.getStringExtra(EXTRA_BOOKING_ID).orEmpty()
        restaurantId = intent.getStringExtra(EXTRA_RESTAURANT_ID).orEmpty()
        initialQrCode = intent.getStringExtra(EXTRA_QR_CODE).orEmpty()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnFloor1.setOnClickListener {
            viewModel.selectFloor(1)
        }

        binding.btnFloor2.setOnClickListener {
            viewModel.selectFloor(2)
        }

        // Bấm nút QR trên header: cho phép quét hoặc nhập mã vị trí mới dọc đường đi
        binding.btnScanQr.setOnClickListener {
            showUpdateLocationDialog()
        }

        binding.tvCallStaff.setOnClickListener {
            SnackbarHelper.show(binding.root, "Đã gửi thông báo nhờ nhân viên hỗ trợ đến bàn của bạn.")
        }
    }

    private fun showUpdateLocationDialog() {
        val container = FrameLayout(this)
        val padding = (20 * resources.displayMetrics.density).toInt()
        val editText = EditText(this).apply {
            hint = getString(R.string.indoor_map_dialog_manual_hint)
            setSingleLine(true)
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(padding, padding / 2, padding, padding / 2)
        }
        container.addView(editText, params)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.indoor_map_dialog_manual_title)
            .setMessage(R.string.indoor_map_dialog_manual_message)
            .setView(container)
            .setPositiveButton(R.string.indoor_map_dialog_manual_confirm) { dialog, _ ->
                val code = editText.text.toString().trim()
                if (code.isNotEmpty()) {
                    viewModel.updateLocationByQr(code)
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.indoor_map_dialog_manual_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.pbLoading.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.isNavigationAllowed.observe(this) { allowed ->
            if (allowed == false && viewModel.booking.value != null) {
                // Đơn chưa CHECKED_IN -> Chặn và chuyển sang Preview
                val intent = Intent(this, IndoorMapPreviewActivity::class.java).apply {
                    putExtra(IndoorMapPreviewActivity.EXTRA_BOOKING_ID, bookingId)
                    putExtra(IndoorMapPreviewActivity.EXTRA_RESTAURANT_ID, restaurantId)
                }
                startActivity(intent)
                finish()
            }
        }

        viewModel.restaurant.observe(this) { res ->
            if (res != null) {
                binding.tvBrandAndRestaurant.text = "BookTable · ${res.name}"
            }
        }

        viewModel.selectedFloor.observe(this) { floor ->
            updateFloorTabs(floor)
            binding.indoorMapView.setFloor(floor)
            updateMapView()
        }

        viewModel.bookedTable.observe(this) { table ->
            if (table != null) {
                updateMapView()
            }
        }

        viewModel.currentQrLocation.observe(this) {
            updateMapView()
        }

        viewModel.pathResult.observe(this) { pathRes ->
            if (pathRes != null) {
                binding.tvInstructionTitle.text = pathRes.instruction
                val table = viewModel.bookedTable.value
                val tableName = if (table != null) "${table.name} · Tầng ${table.floor}" else "Bàn của bạn"
                binding.tvRemainingDetails.text = getString(R.string.indoor_map_detail_format, pathRes.distanceMeters, tableName)
                updateMapView()
            }
        }

        viewModel.errorMessage.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                val snackbar = Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)
                snackbar.setAction(R.string.indoor_map_btn_retry) {
                    viewModel.loadData(bookingId, restaurantId, initialQrCode.ifEmpty { null })
                }
                snackbar.show()
                viewModel.clearError()
            }
        }
    }

    private fun updateMapView() {
        val table = viewModel.bookedTable.value
        val loc = viewModel.currentQrLocation.value
        val pathRes = viewModel.pathResult.value

        val userPt = if (loc != null) PointF(loc.x.toFloat(), loc.y.toFloat()) else null
        val userFloor = loc?.floor ?: 0L
        val routePts = pathRes?.pathOnCurrentFloor ?: emptyList()

        binding.indoorMapView.setMapData(
            bookedTable = table,
            userLocation = userPt,
            userFloor = userFloor,
            routePoints = routePts
        )
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
        const val EXTRA_QR_CODE = "extra_qr_code"
    }
}
