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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.databinding.ActivityTableQrScanBinding
import com.ute.quanlydatban.util.SnackbarHelper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TableQRScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTableQrScanBinding

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
            SnackbarHelper.show(binding.root, getString(R.string.table_qr_error_invalid))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTableQrScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        initIntentData()
        setupListeners()
        checkCameraPermissionAndStart()
    }

    private fun initIntentData() {
        bookingId = intent.getStringExtra(EXTRA_BOOKING_ID).orEmpty()
        restaurantId = intent.getStringExtra(EXTRA_RESTAURANT_ID).orEmpty()

        if (bookingId.isEmpty() || restaurantId.isEmpty()) {
            SnackbarHelper.show(binding.root, getString(R.string.table_qr_error_invalid))
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnManualInput.setOnClickListener {
            showManualInputDialog()
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
                Log.e("TableQRScan", "Lỗi khởi động camera: ${e.message}", e)
                SnackbarHelper.show(binding.root, getString(R.string.table_qr_error_invalid))
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
                        val rawValue = barcode.rawValue
                        if (!rawValue.isNullOrBlank() && isScanningActive) {
                            isScanningActive = false
                            runOnUiThread {
                                verifyAndOpenTableOrder(rawValue)
                            }
                            break
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("TableQRScan", "Lỗi quét barcode: ${e.message}", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun showManualInputDialog() {
        val container = FrameLayout(this)
        val padding = (20 * resources.displayMetrics.density).toInt()
        val editText = EditText(this).apply {
            hint = getString(R.string.table_qr_dialog_manual_hint)
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
            .setTitle(R.string.table_qr_dialog_manual_title)
            .setMessage(R.string.table_qr_dialog_manual_message)
            .setView(container)
            .setPositiveButton(R.string.table_qr_dialog_manual_confirm) { dialog, _ ->
                val code = editText.text.toString().trim()
                if (code.isNotEmpty()) {
                    isScanningActive = false
                    verifyAndOpenTableOrder(code)
                } else {
                    SnackbarHelper.show(binding.root, getString(R.string.table_qr_error_empty))
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.table_qr_dialog_manual_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun verifyAndOpenTableOrder(scannedCode: String) {
        binding.pbScanning.visibility = View.VISIBLE
        val cleanCode = scannedCode.trim().lowercase()

        val firestore = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

        firestore.collection("restaurants").document(restaurantId)
            .collection("tables").get()
            .addOnSuccessListener { tableSnap ->
                var matchedTableId = ""
                for (doc in tableSnap.documents) {
                    val id = doc.id.lowercase()
                    val qrCode = doc.getString("qrCode").orEmpty().lowercase()
                    val name = doc.getString("name").orEmpty().lowercase()
                    if (id == cleanCode || qrCode == cleanCode || name == cleanCode) {
                        matchedTableId = doc.id
                        break
                    }
                }

                if (matchedTableId.isEmpty()) {
                    binding.pbScanning.visibility = View.GONE
                    SnackbarHelper.show(binding.root, getString(R.string.table_qr_error_invalid))
                    resumeScanningDelay()
                    return@addOnSuccessListener
                }

                firestore.collection("bookings").document(bookingId).get()
                    .addOnSuccessListener { bSnap ->
                        binding.pbScanning.visibility = View.GONE
                        val b = bSnap.toObject(Booking::class.java)?.copy(id = bSnap.id)
                        if (b == null || b.userId != userId || b.restaurantId != restaurantId) {
                            SnackbarHelper.show(binding.root, getString(R.string.table_qr_scan_no_active_booking))
                            resumeScanningDelay()
                            return@addOnSuccessListener
                        }

                        val st = BookingStatus.from(b.status)
                        if (st != BookingStatus.CHECKED_IN && st != BookingStatus.SERVING) {
                            SnackbarHelper.show(binding.root, getString(R.string.table_qr_scan_no_active_booking))
                            resumeScanningDelay()
                            return@addOnSuccessListener
                        }

                        val intent = Intent(this, TableOrderActivity::class.java).apply {
                            putExtra(TableOrderActivity.EXTRA_BOOKING_ID, b.id)
                            putExtra(TableOrderActivity.EXTRA_RESTAURANT_ID, restaurantId)
                            putExtra(TableOrderActivity.EXTRA_TABLE_ID, matchedTableId)
                        }
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener {
                        binding.pbScanning.visibility = View.GONE
                        SnackbarHelper.show(binding.root, getString(R.string.table_qr_scan_no_active_booking))
                        resumeScanningDelay()
                    }
            }
            .addOnFailureListener {
                binding.pbScanning.visibility = View.GONE
                SnackbarHelper.show(binding.root, getString(R.string.table_qr_error_invalid))
                resumeScanningDelay()
            }
    }

    private fun resumeScanningDelay() {
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
        const val EXTRA_BOOKING_ID = "extra_booking_id"
        const val EXTRA_RESTAURANT_ID = "extra_restaurant_id"
    }
}
