package com.ute.quanlydatban.ui.owner

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ActivityOwnerPreOrderBinding
import com.ute.quanlydatban.util.SnackbarHelper

class OwnerPreOrderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOwnerPreOrderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityOwnerPreOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val bookingId = intent.getStringExtra(EXTRA_BOOKING_ID).orEmpty()
        if (bookingId.isBlank()) {
            SnackbarHelper.show(binding.root, "Không tìm thấy mã đơn đặt bàn.")
            finish()
            return
        }

        if (savedInstanceState == null) {
            val fragment = OwnerPreOrderFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_BOOKING_ID, bookingId)
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
        }
    }

    companion object {
        const val EXTRA_BOOKING_ID = "extra_booking_id"
        const val ARG_BOOKING_ID = "arg_booking_id"
    }
}
