package com.ute.quanlydatban.ui.customer

import android.content.Intent
import android.os.Bundle
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

        restaurantId = intent.getStringExtra(EXTRA_RESTAURANT_ID).orEmpty()
        if (restaurantId.isEmpty()) {
            SnackbarHelper.show(binding.root, getString(R.string.restaurant_not_found))
            finish()
            return
        }

        setupToolbar()
        setupPlaceholders()
        setupRecyclerView()
        setupBookingButton()
        setupRetryButton()
        observeViewModel()

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
            // Chuyển sang màn hình Đặt bàn (T9)
            val bookingIntent = try {
                Intent(this, Class.forName("com.ute.quanlydatban.ui.customer.booking.BookingActivity"))
            } catch (e: Exception) {
                try {
                    Intent(this, Class.forName("com.ute.quanlydatban.ui.customer.BookingActivity"))
                } catch (e2: Exception) {
                    Intent(this, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_ROLE, MainActivity.ROLE_CUSTOMER)
                    }
                }
            }
            bookingIntent.putExtra(EXTRA_RESTAURANT_ID, restaurantId)
            startActivity(bookingIntent)
        }
    }

    private fun setupRetryButton() {
        binding.layoutStates.btnRetry.setOnClickListener {
            viewModel.loadRestaurantDetail(restaurantId)
        }
    }

    private fun observeViewModel() {
        viewModel.isDetailLoading.observe(this) { loading ->
            if (loading) {
                UiStateHelper.showLoading(binding.layoutStates.root)
                binding.llDetailBody.visibility = View.GONE
            }
        }

        viewModel.detailErrorMessage.observe(this) { error ->
            if (error != null) {
                UiStateHelper.showError(binding.layoutStates.root, error)
                binding.llDetailBody.visibility = View.GONE
                SnackbarHelper.show(binding.root, error) {
                    viewModel.loadRestaurantDetail(restaurantId)
                }
            }
        }

        viewModel.currentRestaurant.observe(this) { restaurant ->
            if (restaurant != null && viewModel.isDetailLoading.value == false && viewModel.detailErrorMessage.value == null) {
                UiStateHelper.hideAll(binding.layoutStates.root)
                binding.llDetailBody.visibility = View.VISIBLE
                bindRestaurantData(restaurant)
            }
        }

        viewModel.isOpenNow.observe(this) { isOpen ->
            val restaurant = viewModel.currentRestaurant.value ?: return@observe
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
                // Quán ngoài giờ mở cửa: báo rõ trên Detail
                binding.llClosedNotice.visibility = View.VISIBLE
            }
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
    }

    companion object {
        const val EXTRA_RESTAURANT_ID = "restaurantId"
    }
}
