package com.ute.quanlydatban

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.ute.quanlydatban.databinding.ActivityMainBinding
import com.ute.quanlydatban.util.SnackbarHelper

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val role = intent.getStringExtra(EXTRA_ROLE)?.lowercase()
        when (role) {
            null, "customer" -> setCustomerNavigation()
            "owner" -> setOwnerNavigation()
            else -> rejectInvalidRole()
        }
    }

    private fun setCustomerNavigation() {
        setNavLabels(
            R.string.nav_home,
            R.string.nav_booking,
            R.string.nav_history,
            R.string.nav_profile
        )
        setNavIcons(
            R.drawable.ic_nav_home,
            R.drawable.ic_nav_calendar,
            R.drawable.ic_nav_history,
            R.drawable.ic_nav_profile
        )
        binding.navBottom.visibility = View.VISIBLE
        configureTab(binding.navItem1, R.id.customerHomeFragment)
        configureTab(binding.navItem2, R.id.customerBookingFragment)
        configureTab(binding.navItem3, R.id.customerHistoryFragment)
        configureTab(binding.navItem4, R.id.customerProfileFragment)
        selectTab(0)
    }

    private fun setOwnerNavigation() {
        setNavLabels(
            R.string.nav_owner_overview,
            R.string.nav_owner_orders,
            R.string.nav_history,
            R.string.nav_profile
        )
        setNavIcons(
            R.drawable.ic_nav_home,
            R.drawable.ic_nav_calendar,
            R.drawable.ic_nav_history,
            R.drawable.ic_nav_profile
        )
        binding.navBottom.visibility = View.VISIBLE
        configureTab(binding.navItem1, R.id.ownerOverviewFragment)
        configureTab(binding.navItem2, R.id.ownerBookingFragment)
        configureTab(binding.navItem3, R.id.ownerHistoryFragment)
        configureTab(binding.navItem4, R.id.ownerProfileFragment)
        navController.navigate(R.id.ownerOverviewFragment)
        selectTab(0)
    }

    private fun rejectInvalidRole() {
        binding.navBottom.visibility = View.GONE
        SnackbarHelper.show(binding.root, getString(R.string.nav_role_invalid))
        binding.root.postDelayed({ finish() }, 1800)
    }

    private fun configureTab(item: LinearLayout, destinationId: Int) {
        item.setOnClickListener {
            val options = androidx.navigation.NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(navController.graph.startDestinationId, false, true)
                .build()
            navController.navigate(destinationId, null, options)
            selectTab(
                when (item.id) {
                    R.id.nav_item_1 -> 0
                    R.id.nav_item_2 -> 1
                    R.id.nav_item_3 -> 2
                    else -> 3
                }
            )
        }
    }

    private fun setNavLabels(vararg labels: Int) {
        val views = listOf(binding.tvNav1, binding.tvNav2, binding.tvNav3, binding.tvNav4)
        labels.forEachIndexed { index, label ->
            views[index].setText(label)
        }
    }

    private fun setNavIcons(vararg icons: Int) {
        val views = listOf(binding.ivNav1, binding.ivNav2, binding.ivNav3, binding.ivNav4)
        icons.forEachIndexed { index, icon ->
            views[index].setImageResource(icon)
        }
    }

    private fun selectTab(index: Int) {
        val labels = listOf(binding.tvNav1, binding.tvNav2, binding.tvNav3, binding.tvNav4)
        val icons = listOf(binding.ivNav1, binding.ivNav2, binding.ivNav3, binding.ivNav4)
        val active = getColor(R.color.booktable_active)
        val muted = getColor(R.color.booktable_muted)
        labels.forEachIndexed { i, label ->
            label.setTextColor(if (i == index) active else muted)
            label.setTypeface(label.typeface, if (i == index) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
        icons.forEachIndexed { i, icon ->
            icon.setColorFilter(if (i == index) active else muted)
        }
    }

    companion object {
        const val EXTRA_ROLE = "role"
    }
}