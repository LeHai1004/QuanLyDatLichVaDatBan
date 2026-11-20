package com.ute.quanlydatban.ui.owner.scan

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
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
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
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ActivityOwnerQrScanBinding
import com.ute.quanlydatban.ui.owner.checkout.OwnerCheckoutActivity
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.viewmodel.OwnerCheckoutViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class OwnerQRScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOwnerQrScanBinding
    private val viewModel: OwnerCheckoutViewModel by viewModels()

    private var cameraExecutor: ExecutorService? = null
    private var isScanningActive: Boolean = true
    private val barcodeScanner by lazy { BarcodeScanning.getClient() }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            SnackbarHelper.show(binding.root, getString(R.string.owner_scan_permission_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityOwnerQrScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupListeners()
        observeViewModel()

        viewModel.initOwnerRestaurant()
        checkCameraPermissionAndStart()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnManualInput.setOnClickListener {
            showManualInputDialog()
        }
    }

    private fun observeViewModel() {
        viewModel.isScanProcessing.observe(this) { processing ->
            binding.flLoadingOverlay.visibility = if (processing) View.VISIBLE else View.GONE
        }

        viewModel.scanError.observe(this) { error ->
            if (!error.isNullOrBlank()) {
                SnackbarHelper.show(binding.root, error)
                Handler(Looper.getMainLooper()).postDelayed({
                    isScanningActive = true
                }, 2000)
            }
        }

        viewModel.scanSuccessBooking.observe(this) { booking ->
            if (booking != null) {
                showScanSuccessDialog(booking.id)
            }
        }
    }

    private fun checkCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
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
                Log.e("OwnerQRScanActivity", "Lỗi khởi động camera: ${e.message}", e)
                SnackbarHelper.show(binding.root, "Không thể mở camera. Vui lòng thử lại.")
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
                        if (barcode.valueType == Barcode.TYPE_TEXT || barcode.valueType == Barcode.TYPE_URL || barcode.rawValue != null) {
                            val rawValue = barcode.rawValue ?: barcode.displayValue.orEmpty()
                            if (rawValue.isNotBlank() && isScanningActive) {
                                isScanningActive = false
                                runOnUiThread {
                                    viewModel.processTicket(rawValue)
                                }
                                break
                            }
                        }
                    }
                }
                .addOnFailureListener {
                    // Tiếp tục phân tích frame tiếp theo
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun showManualInputDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.owner_scan_manual_dialog_hint)
            setSingleLine()
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val container = FrameLayout(this)
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle(R.string.owner_scan_manual_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.owner_scan_manual_dialog_btn_confirm) { dialog, _ ->
                val code = input.text.toString().trim()
                if (code.isNotBlank()) {
                    isScanningActive = false
                    viewModel.processTicket(code)
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.owner_scan_manual_dialog_btn_cancel, null)
            .show()
    }

    private fun showScanSuccessDialog(bookingId: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.owner_scan_success_dialog_title)
            .setMessage(getString(R.string.owner_scan_success_dialog_msg, bookingId))
            .setCancelable(false)
            .setPositiveButton(R.string.owner_scan_success_btn_checkout) { _, _ ->
                val intent = Intent(this, OwnerCheckoutActivity::class.java).apply {
                    putExtra(OwnerCheckoutActivity.EXTRA_BOOKING_ID, bookingId)
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton(R.string.owner_scan_success_btn_continue) { dialog, _ ->
                dialog.dismiss()
                viewModel.clearScanMessages()
                isScanningActive = true
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor?.shutdown()
        barcodeScanner.close()
    }
}
