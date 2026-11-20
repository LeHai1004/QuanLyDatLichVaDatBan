package com.ute.quanlydatban.ui.customer.booking

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ActivityQrScanBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.viewmodel.CheckInViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrScanBinding

    private val viewModel by lazy {
        ViewModelProvider(this, CheckInViewModel.Factory())[CheckInViewModel::class.java]
    }

    private var bookingId: String = ""
    private var restaurantId: String = ""

    private var cameraExecutor: ExecutorService? = null
    private var isScanningActive: Boolean = true
    private val barcodeScanner by lazy { BarcodeScanning.getClient() }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            SnackbarHelper.show(binding.root, getString(R.string.qr_scan_permission_denied_message))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityQrScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        initIntentData()
        setupListeners()
        observeViewModel()
        checkCameraPermissionAndStart()
    }

    private fun initIntentData() {
        bookingId = intent.getStringExtra(EXTRA_BOOKING_ID).orEmpty()
        restaurantId = intent.getStringExtra(EXTRA_RESTAURANT_ID).orEmpty()

        if (bookingId.isEmpty()) {
            SnackbarHelper.show(binding.root, getString(R.string.qr_scan_error_booking_not_found))
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnNotification.setOnClickListener {
            SnackbarHelper.show(binding.root, getString(R.string.feature_coming_soon))
        }

        // Nút nhập mã vị trí thủ công khi không quét được hoặc camera không có
        binding.btnManualInput.setOnClickListener {
            showManualInputDialog()
        }
    }

    private fun checkCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.qr_scan_dialog_manual_title)
                    .setMessage(R.string.qr_scan_permission_rationale)
                    .setPositiveButton(R.string.qr_scan_permission_btn_grant) { _, _ ->
                        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    .setNegativeButton(R.string.qr_scan_btn_manual) { _, _ ->
                        showManualInputDialog()
                    }
                    .show()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor ?: Executors.newSingleThreadExecutor()) { imageProxy ->
                    processImageProxy(imageProxy)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (e: Exception) {
                Log.e(TAG, "Không thể khởi động camera: ${e.message}", e)
                SnackbarHelper.show(binding.root, "Không thể khởi động máy ảnh. Bạn hãy nhập mã thủ công.")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null && isScanningActive) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        if (barcode.valueType == Barcode.TYPE_TEXT ||
                            barcode.valueType == Barcode.TYPE_URL ||
                            barcode.format == Barcode.FORMAT_QR_CODE
                        ) {
                            val rawValue = barcode.rawValue
                            if (!rawValue.isNullOrBlank() && isScanningActive) {
                                isScanningActive = false
                                runOnUiThread {
                                    handleQrDetected(rawValue)
                                }
                                break
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Lỗi quét barcode: ${e.message}", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun handleQrDetected(rawContent: String) {
        if (bookingId.isEmpty()) {
            SnackbarHelper.show(binding.root, getString(R.string.qr_scan_error_booking_not_found))
            resumeScanningDelay()
            return
        }

        binding.pbScanning.visibility = View.VISIBLE
        viewModel.performCheckIn(bookingId, restaurantId, rawContent)
    }

    private fun showManualInputDialog() {
        val container = FrameLayout(this)
        val padding = (20 * resources.displayMetrics.density).toInt()
        val editText = EditText(this).apply {
            hint = getString(R.string.qr_scan_dialog_manual_hint)
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
            .setTitle(R.string.qr_scan_dialog_manual_title)
            .setMessage(R.string.qr_scan_dialog_manual_message)
            .setView(container)
            .setPositiveButton(R.string.qr_scan_dialog_manual_btn_confirm) { dialog, _ ->
                val code = editText.text.toString().trim()
                if (code.isNotEmpty()) {
                    isScanningActive = false
                    handleQrDetected(code)
                } else {
                    SnackbarHelper.show(binding.root, getString(R.string.qr_scan_dialog_manual_empty_error))
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.qr_scan_dialog_manual_btn_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.pbScanning.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnManualInput.isEnabled = !loading
        }

        viewModel.errorMessage.observe(this) { errorMsg ->
            if (!errorMsg.isNullOrBlank()) {
                SnackbarHelper.show(binding.root, errorMsg)
                viewModel.clearError()
                resumeScanningDelay()
            }
        }

        viewModel.checkInSuccess.observe(this) { result ->
            if (result != null) {
                val intent = Intent(this, CheckInSuccessActivity::class.java).apply {
                    putExtra(EXTRA_BOOKING_ID, result.booking.id)
                    putExtra(EXTRA_RESTAURANT_ID, result.booking.restaurantId)
                }
                startActivity(intent)
                finish()
            }
        }
    }

    private fun resumeScanningDelay() {
        // Tạm dừng 2 giây trước khi cho phép quét tiếp để tránh liên tục bắn sự kiện
        Handler(Looper.getMainLooper()).postDelayed({
            isScanningActive = true
        }, 2000)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor?.shutdown()
        barcodeScanner.close()
    }

    companion object {
        private const val TAG = "QRScanActivity"
        const val EXTRA_BOOKING_ID = "extra_booking_id"
        const val EXTRA_RESTAURANT_ID = "extra_restaurant_id"
    }
}
