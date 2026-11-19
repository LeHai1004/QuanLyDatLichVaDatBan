package com.ute.quanlydatban.ui.customer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.ute.quanlydatban.MainActivity
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.databinding.ActivityRestaurantDetailBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.UiStateHelper
import com.ute.quanlydatban.util.formatMoney
import com.ute.quanlydatban.viewmodel.HomeViewModel
import java.util.Locale

class RestaurantDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRestaurantDetailBinding
    private val viewModel by lazy {
        ViewModelProvider(this, HomeViewModel.Factory)[HomeViewModel::class.java]
    }

    private lateinit var menuAdapter: MenuItemPreviewAdapter
    private var restaurantId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRestaurantDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.coordinatorDetailRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            binding.llBottomBar.setPadding(
                binding.llBottomBar.paddingLeft,
                binding.llBottomBar.paddingTop,
                binding.llBottomBar.paddingRight,
                systemBars.bottom
            )
            insets
        }

        setupToolbar()
        setupPlaceholders()
        setupRecyclerView()
        setupBookingButton()
        setupRetryButton()
        observeViewModel()

        restaurantId = intent.getStringExtra(EXTRA_RESTAURANT_ID)
            ?: intent.getStringExtra("restaurant_id")
            ?: intent.getStringExtra("id").orEmpty()

        if (restaurantId.isEmpty()) {
            Log.e("RestaurantDetail", "RestaurantDetailActivity: restaurantId rỗng hoặc không có trong Intent")
            val notFoundMessage = getString(R.string.restaurant_not_found)
            UiStateHelper.showError(binding.layoutStates.root, notFoundMessage)
            binding.llDetailBody.visibility = View.GONE
            SnackbarHelper.show(binding.root, notFoundMessage)
            return
        }

        viewModel.loadRestaurantDetail(restaurantId)
    }

    private fun setupToolbar() {
        binding.ivBack.setOnClickListener {
            finish()
        }
    }

    private fun setupPlaceholders() {
        // Chừa chỗ cho nút tim (T29) và nút danh sách chờ (T32)
        binding.ivFavorite.setOnClickListener {
            SnackbarHelper.show(binding.root, getString(R.string.detail_favorite_desc))
        }

        binding.ivWaitlist.setOnClickListener {
            SnackbarHelper.show(binding.root, getString(R.string.detail_waitlist_desc))
        }
    }

    private fun setupRecyclerView() {
        menuAdapter = MenuItemPreviewAdapter()
        binding.rvMenuItems.apply {
            layoutManager = LinearLayoutManager(this@RestaurantDetailActivity)
            adapter = menuAdapter
        }
    }

    private fun setupBookingButton() {
        binding.btnBooking.setOnClickListener {
            if (restaurantId.isBlank()) {
                SnackbarHelper.show(binding.root, getString(R.string.restaurant_not_found))
                return@setOnClickListener
            }
            val res = viewModel.currentRestaurant.value
            val bookingIntent = Intent(this, com.ute.quanlydatban.ui.customer.booking.BookingActivity::class.java).apply {
                putExtra(EXTRA_RESTAURANT_ID, restaurantId)
                if (res != null) {
                    putExtra(com.ute.quanlydatban.ui.customer.booking.BookingActivity.EXTRA_RESTAURANT_NAME, res.name)
                    putExtra(com.ute.quanlydatban.ui.customer.booking.BookingActivity.EXTRA_RESTAURANT_ADDRESS, res.address)
                    putExtra(com.ute.quanlydatban.ui.customer.booking.BookingActivity.EXTRA_RESTAURANT_OPEN_TIME, res.openTime)
                    putExtra(com.ute.quanlydatban.ui.customer.booking.BookingActivity.EXTRA_RESTAURANT_CLOSE_TIME, res.closeTime)
                    putExtra(com.ute.quanlydatban.ui.customer.booking.BookingActivity.EXTRA_DEPOSIT_AMOUNT, res.depositAmount)
                }
            }
            startActivity(bookingIntent)
        }
    }

    private fun setupRetryButton() {
        binding.layoutStates.btnRetry.setOnClickListener {
            if (restaurantId.isNotEmpty()) {
                viewModel.loadRestaurantDetail(restaurantId)
            } else {
                finish()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.isDetailLoading.observe(this) {
            updateUiState()
        }

        viewModel.detailErrorMessage.observe(this) { error ->
            updateUiState()
            if (error != null) {
                SnackbarHelper.show(binding.root, error) {
                    viewModel.loadRestaurantDetail(restaurantId)
                }
            }
        }

        viewModel.currentRestaurant.observe(this) { restaurant ->
            updateUiState()
            if (restaurant != null) {
                bindRestaurantData(restaurant)
            }
        }

        viewModel.isOpenNow.observe(this) { isOpen ->
            updateOpenStatusBadge(isOpen)
        }

        viewModel.menuItems.observe(this) { items ->
            if (items.isNullOrEmpty()) {
                binding.tvMenuEmpty.visibility = View.VISIBLE
                binding.rvMenuItems.visibility = View.GONE
            } else {
                binding.tvMenuEmpty.visibility = View.GONE
                binding.rvMenuItems.visibility = View.VISIBLE
                menuAdapter.submitList(items)
            }
        }
    }

    private fun updateUiState() {
        val isLoading = viewModel.isDetailLoading.value ?: false
        val error = viewModel.detailErrorMessage.value
        val restaurant = viewModel.currentRestaurant.value

        when {
            isLoading -> {
                UiStateHelper.showLoading(binding.layoutStates.root)
                binding.llDetailBody.visibility = View.GONE
            }
            error != null -> {
                UiStateHelper.showError(binding.layoutStates.root, error)
                binding.llDetailBody.visibility = View.GONE
            }
            restaurant == null -> {
                UiStateHelper.showEmpty(binding.layoutStates.root)
                binding.llDetailBody.visibility = View.GONE
            }
            else -> {
                UiStateHelper.hideAll(binding.layoutStates.root)
                binding.llDetailBody.visibility = View.VISIBLE
            }
        }
    }

    private fun updateOpenStatusBadge(isOpen: Boolean) {
        val restaurant = viewModel.currentRestaurant.value ?: return
        if (isOpen) {
            binding.tvOpenStatus.text = getString(
                R.string.detail_hours_open_now,
                restaurant.openTime,
                restaurant.closeTime
            )
            binding.tvOpenStatus.setBackgroundResource(R.drawable.bg_status_badge_open)
            binding.tvOpenStatus.setTextColor(ContextCompat.getColor(this, R.color.auth_success))
            binding.llClosedNotice.visibility = View.GONE
        } else {
            binding.tvOpenStatus.text = getString(
                R.string.detail_hours_closed_now,
                restaurant.openTime
            )
            binding.tvOpenStatus.setBackgroundResource(R.drawable.bg_status_badge_closed)
            binding.tvOpenStatus.setTextColor(ContextCompat.getColor(this, R.color.booktable_error))
            binding.llClosedNotice.visibility = View.VISIBLE
        }
    }

    private fun bindRestaurantData(restaurant: Restaurant) {
        binding.tvRestaurantName.text = restaurant.name
        binding.tvCategory.text = restaurant.category
        binding.tvRating.text = String.format(Locale.US, "★ %.1f (Đánh giá chất lượng)", restaurant.rating)
        binding.tvAddress.text = restaurant.address
        binding.tvHours.text = getString(
            R.string.restaurant_open_hours_format,
            restaurant.openTime,
            restaurant.closeTime
        )
        binding.tvDepositAmount.text = formatMoney(restaurant.depositAmount)
        binding.tvBottomDepositAmount.text = formatMoney(restaurant.depositAmount)
        updateOpenStatusBadge(viewModel.isOpenNow.value ?: true)
    }

    companion object {
        const val EXTRA_RESTAURANT_ID = "restaurantId"
    }
}
