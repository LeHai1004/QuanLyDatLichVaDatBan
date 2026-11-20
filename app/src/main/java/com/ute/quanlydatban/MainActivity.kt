package com.ute.quanlydatban

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
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
        val isOwner = (role == ROLE_OWNER)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        if (navHostFragment == null) {
            SnackbarHelper.show(binding.root, getString(R.string.state_error))
            finish()
            return
        }
        navController = navHostFragment.navController

        val graph = navController.navInflater.inflate(R.navigation.nav_graph)
        val startDest = if (isOwner) {
            R.id.ownerOverviewFragment
        } else {
            R.id.customerHomeFragment
        }
        graph.setStartDestination(startDest)
        navController.graph = graph

        setupNavigation(isOwner)
    }

    private fun setupNavigation(isOwner: Boolean) {
        if (isOwner) {
            binding.llNavCustomer.visibility = View.GONE
            binding.llNavOwner.visibility = View.VISIBLE
            setOwnerNavigation()
        } else {
            binding.llNavOwner.visibility = View.GONE
            binding.llNavCustomer.visibility = View.VISIBLE
            setCustomerNavigation()
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (isOwner) {
                when (destination.id) {
                    R.id.ownerOverviewFragment -> {
                        binding.llNavBottom.visibility = View.VISIBLE
                        selectOwnerTab(0)
                    }
                    R.id.ownerBookingFragment -> {
                        binding.llNavBottom.visibility = View.VISIBLE
                        selectOwnerTab(1)
                    }
                    R.id.ownerTablePlaceholderFragment -> {
                        binding.llNavBottom.visibility = View.VISIBLE
                        selectOwnerTab(2)
                    }
                    R.id.ownerMenuPlaceholderFragment -> {
                        binding.llNavBottom.visibility = View.VISIBLE
                        selectOwnerTab(3)
                    }
                    R.id.ownerProfileFragment -> {
                        binding.llNavBottom.visibility = View.VISIBLE
                        selectOwnerTab(4)
                    }
                    else -> {
                        binding.llNavBottom.visibility = View.GONE
                    }
                }
            } else {
                when (destination.id) {
                    R.id.customerHomeFragment -> {
                        binding.llNavBottom.visibility = View.VISIBLE
                        selectCustomerTab(0)
                    }
                    R.id.customerBookingFragment -> {
                        binding.llNavBottom.visibility = View.VISIBLE
                        selectCustomerTab(1)
                    }
                    R.id.customerHistoryFragment -> {
                        binding.llNavBottom.visibility = View.VISIBLE
                        selectCustomerTab(2)
                    }
                    R.id.customerProfileFragment -> {
                        binding.llNavBottom.visibility = View.VISIBLE
                        selectCustomerTab(3)
                    }
                    else -> {
                        binding.llNavBottom.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun setCustomerNavigation() {
        setCustomerNavLabels(
            R.string.nav_home,
            R.string.nav_booking,
            R.string.nav_history,
            R.string.nav_profile
        )
        setCustomerNavIcons(
            R.drawable.ic_nav_home,
            R.drawable.ic_nav_calendar,
            R.drawable.ic_nav_history,
            R.drawable.ic_nav_profile
        )
        binding.llNavBottom.visibility = View.VISIBLE
        configureCustomerTab(binding.llNavItem1, R.id.customerHomeFragment, 0)
        configureCustomerTab(binding.llNavItem2, R.id.customerBookingFragment, 1)
        configureCustomerTab(binding.llNavItem3, R.id.customerHistoryFragment, 2)
        configureCustomerTab(binding.llNavItem4, R.id.customerProfileFragment, 3)
        selectCustomerTab(0)
    }

    private fun setOwnerNavigation() {
        binding.llNavBottom.visibility = View.VISIBLE
        configureOwnerTab(binding.llOwnerNavItem1, R.id.ownerOverviewFragment, 0)
        configureOwnerTab(binding.llOwnerNavItem2, R.id.ownerBookingFragment, 1)
        configureOwnerTab(binding.llOwnerNavItem3, R.id.ownerTablePlaceholderFragment, 2)
        configureOwnerTab(binding.llOwnerNavItem4, R.id.ownerMenuPlaceholderFragment, 3)
        configureOwnerTab(binding.llOwnerNavItem5, R.id.ownerProfileFragment, 4)
        selectOwnerTab(0)
    }

    private fun configureCustomerTab(item: LinearLayout, destinationId: Int, index: Int) {
        item.setOnClickListener {
            if (navController.currentDestination?.id != destinationId) {
                val options = androidx.navigation.NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .setPopUpTo(navController.graph.startDestinationId, false, true)
                    .build()
                navController.navigate(destinationId, null, options)
            }
            selectCustomerTab(index)
        }
    }

    private fun configureOwnerTab(item: LinearLayout, destinationId: Int, index: Int) {
        item.setOnClickListener {
            if (navController.currentDestination?.id != destinationId) {
                val options = androidx.navigation.NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .setPopUpTo(navController.graph.startDestinationId, false, true)
                    .build()
                navController.navigate(destinationId, null, options)
            }
            selectOwnerTab(index)
        }
    }

    private fun setCustomerNavLabels(vararg labels: Int) {
        val views = listOf(binding.tvNav1, binding.tvNav2, binding.tvNav3, binding.tvNav4)
        labels.forEachIndexed { index, label ->
            views[index].setText(label)
        }
    }

    private fun setCustomerNavIcons(vararg icons: Int) {
        val views = listOf(binding.ivNav1, binding.ivNav2, binding.ivNav3, binding.ivNav4)
        icons.forEachIndexed { index, icon ->
            views[index].setImageResource(icon)
        }
    }

    private fun selectCustomerTab(index: Int) {
        val labels = listOf(binding.tvNav1, binding.tvNav2, binding.tvNav3, binding.tvNav4)
        val icons = listOf(binding.ivNav1, binding.ivNav2, binding.ivNav3, binding.ivNav4)
        val active = getColor(R.color.booktable_orange)
        val muted = getColor(R.color.booktable_muted)
        labels.forEachIndexed { i, label ->
            label.setTextColor(if (i == index) active else muted)
            label.setTypeface(
                label.typeface,
                if (i == index) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
            )
        }
        icons.forEachIndexed { i, icon ->
            icon.setColorFilter(if (i == index) active else muted)
        }
    }

    private fun selectOwnerTab(index: Int) {
        val pillContainers = listOf(
            binding.flOwnerNavIcon1,
            binding.flOwnerNavIcon2,
            binding.flOwnerNavIcon3,
            binding.flOwnerNavIcon4,
            binding.flOwnerNavIcon5
        )
        val icons = listOf(
            binding.ivOwnerNav1,
            binding.ivOwnerNav2,
            binding.ivOwnerNav3,
            binding.ivOwnerNav4,
            binding.ivOwnerNav5
        )
        val labels = listOf(
            binding.tvOwnerNav1,
            binding.tvOwnerNav2,
            binding.tvOwnerNav3,
            binding.tvOwnerNav4,
            binding.tvOwnerNav5
        )
        val activeColor = getColor(R.color.booktable_orange)
        val mutedColor = getColor(R.color.booktable_muted)

        pillContainers.forEachIndexed { i, container ->
            if (i == index) {
                container.setBackgroundResource(R.drawable.bg_nav_tab_selected)
            } else {
                container.background = null
            }
        }

        icons.forEachIndexed { i, icon ->
            icon.setColorFilter(if (i == index) activeColor else mutedColor)
        }

        labels.forEachIndexed { i, label ->
            label.setTextColor(if (i == index) activeColor else mutedColor)
            label.setTypeface(
                label.typeface,
                if (i == index) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
            )
        }
    }

    companion object {
        const val EXTRA_ROLE = "role"
        const val ROLE_CUSTOMER = "customer"
        const val ROLE_OWNER = "owner"
        const val DELAY_REJECT_MS = 1800L
    }
}
