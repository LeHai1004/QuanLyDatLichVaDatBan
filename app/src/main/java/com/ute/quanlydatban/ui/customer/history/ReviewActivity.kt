package com.ute.quanlydatban.ui.customer.history

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ActivityReviewBinding
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.viewmodel.ReviewViewModel

class ReviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOOKING_ID = "extra_booking_id"
        const val EXTRA_RESTAURANT_ID = "extra_restaurant_id"
        const val EXTRA_BOOKING_DATE = "extra_booking_date"
        const val EXTRA_BOOKING_TIME = "extra_booking_time"
        const val EXTRA_TABLE_ID = "extra_table_id"
    }

    private lateinit var binding: ActivityReviewBinding
    private val viewModel: ReviewViewModel by viewModels()

    private lateinit var starViews: List<ImageView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val bookingId = intent.getStringExtra(EXTRA_BOOKING_ID).orEmpty()
        val restaurantId = intent.getStringExtra(EXTRA_RESTAURANT_ID).orEmpty()
        val date = intent.getStringExtra(EXTRA_BOOKING_DATE).orEmpty()
        val time = intent.getStringExtra(EXTRA_BOOKING_TIME).orEmpty()
        val tableId = intent.getStringExtra(EXTRA_TABLE_ID).orEmpty()

        if (bookingId.isBlank()) {
            Toast.makeText(this, "Không tìm thấy mã đặt bàn để đánh giá", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Điền tạm các thông tin từ Intent nếu có
        val dateText = if (date.isNotBlank()) date else ""
        val timeText = if (time.isNotBlank()) " · $time" else ""
        val tableText = if (tableId.isNotBlank()) " · Bàn $tableId" else ""
        binding.tvBookingSummary.text = "Bữa hẹn $dateText$timeText$tableText"

        starViews = listOf(
            binding.ivStar1,
            binding.ivStar2,
            binding.ivStar3,
            binding.ivStar4,
            binding.ivStar5
        )

        setupListeners()
        observeViewModel()

        viewModel.loadBookingAndRestaurant(bookingId, restaurantId)
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener { finish() }

        starViews.forEachIndexed { index, starView ->
            starView.setOnClickListener {
                val rating = index + 1
                viewModel.setRating(rating)
            }
        }

        binding.etComment.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val length = s?.length ?: 0
                binding.tvCharCount.text = getString(R.string.review_comment_counter_format, length)
                viewModel.setComment(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSubmitReview.setOnClickListener {
            viewModel.submitReview()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.flLoadingOverlay.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnSubmitReview.isEnabled = !loading
        }

        viewModel.errorMessage.observe(this) { error ->
            if (!error.isNullOrBlank()) {
                SnackbarHelper.show(binding.root, error)
            }
        }

        viewModel.restaurant.observe(this) { res ->
            if (res != null) {
                binding.tvRestaurantName.text = res.name
            }
        }

        viewModel.booking.observe(this) { b ->
            if (b != null) {
                binding.tvBookingSummary.text = "Bữa ăn ngày ${b.date} · ${b.time} · Bàn ${b.tableId}"
            }
        }

        viewModel.rating.observe(this) { rating ->
            updateStarViews(rating)
        }

        viewModel.submitSuccess.observe(this) { success ->
            if (success) {
                Toast.makeText(this, getString(R.string.review_success), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun updateStarViews(rating: Int) {
        val filledStar = ContextCompat.getDrawable(this, R.drawable.ic_star)
        val outlineStar = ContextCompat.getDrawable(this, R.drawable.ic_star_outline)

        starViews.forEachIndexed { index, iv ->
            if (index < rating) {
                iv.setImageDrawable(filledStar)
            } else {
                iv.setImageDrawable(outlineStar)
            }
        }

        val desc = when (rating) {
            1 -> getString(R.string.review_rating_1)
            2 -> getString(R.string.review_rating_2)
            3 -> getString(R.string.review_rating_3)
            4 -> getString(R.string.review_rating_4)
            5 -> getString(R.string.review_rating_5)
            else -> ""
        }
        binding.tvRatingDesc.text = desc
    }
}
