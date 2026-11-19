package com.ute.quanlydatban.ui.customer.booking

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ActivityBookingBinding
import com.ute.quanlydatban.util.SnackbarHelper

class BookingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBookingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val restaurantId = intent.getStringExtra(EXTRA_RESTAURANT_ID).orEmpty()
        if (restaurantId.isEmpty()) {
            SnackbarHelper.show(binding.root, getString(R.string.restaurant_not_found))
            finish()
            return
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_booking) as? NavHostFragment ?: return
        val navController = navHostFragment.navController
        val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
        navGraph.setStartDestination(R.id.bookingFragment)
        navController.setGraph(navGraph, intent.extras)
    }

    companion object {
        const val EXTRA_RESTAURANT_ID = "restaurantId"
        const val EXTRA_RESTAURANT_NAME = "restaurantName"
        const val EXTRA_RESTAURANT_ADDRESS = "restaurantAddress"
        const val EXTRA_RESTAURANT_OPEN_TIME = "restaurantOpenTime"
        const val EXTRA_RESTAURANT_CLOSE_TIME = "restaurantCloseTime"
        const val EXTRA_DEPOSIT_AMOUNT = "depositAmount"
    }
}
