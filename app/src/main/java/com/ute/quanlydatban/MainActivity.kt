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

        val role = intent.getStringExtra(EXTRA_ROLE)?.lowercase()
        if (role != null && role != ROLE_CUSTOMER && role != ROLE_OWNER) {
            rejectInvalidRole()
            return
        }

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        if (navHostFragment == null) {
            SnackbarHelper.show(binding.root, getString(R.string.state_error))
            finish()
            return
        }
        navController = navHostFragment.navController

        val graph = navController.navInflater.inflate(R.navigation.nav_graph)
        val startDest = if (role == ROLE_OWNER) {
            R.id.ownerOverviewFragment
        } else {
            R.id.customerHomeFragment
        }
        graph.setStartDestination(startDest)
        navController.graph = graph

        when (role) {
            ROLE_OWNER -> setOwnerNavigation()
            else -> setCustomerNavigation()
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
        binding.llNavBottom.visibility = View.VISIBLE
        configureTab(binding.llNavItem1, R.id.customerHomeFragment)
        configureTab(binding.llNavItem2, R.id.customerBookingFragment)
        configureTab(binding.llNavItem3, R.id.customerHistoryFragment)
        configureTab(binding.llNavItem4, R.id.customerProfileFragment)
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
        binding.llNavBottom.visibility = View.VISIBLE
        configureTab(binding.llNavItem1, R.id.ownerOverviewFragment)
        configureTab(binding.llNavItem2, R.id.ownerBookingFragment)
        configureTab(binding.llNavItem3, R.id.ownerHistoryFragment)
        configureTab(binding.llNavItem4, R.id.ownerProfileFragment)
        selectTab(0)
    }

    private fun rejectInvalidRole() {
        binding.llNavBottom.visibility = View.GONE
        SnackbarHelper.show(binding.root, getString(R.string.nav_role_invalid))
        binding.root.postDelayed({ finish() }, DELAY_REJECT_MS)
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
                    R.id.llNavItem1 -> 0
                    R.id.llNavItem2 -> 1
                    R.id.llNavItem3 -> 2
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
        val active = getColor(R.color.booktable_orange)
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
        const val ROLE_CUSTOMER = "customer"
        const val ROLE_OWNER = "owner"
        const val DELAY_REJECT_MS = 1800L
    }
}