package com.ute.quanlydatban.ui.customer.invite

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.MenuItem
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.data.repository.OrderRepository
import com.ute.quanlydatban.databinding.ActivityInviteFriendsBinding
import com.ute.quanlydatban.ui.auth.LoginActivity
import com.ute.quanlydatban.util.SnackbarHelper
import com.ute.quanlydatban.util.formatMoney
import com.ute.quanlydatban.viewmodel.InviteFriendsViewModel

class InviteFriendsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOOKING_ID = "extra_booking_id"
        const val EXTRA_RESTAURANT_ID = "extra_restaurant_id"
        const val EXTRA_INVITE_CODE = "extra_invite_code"
        private const val PREFS_NAME = "booktable_invite_prefs"
        private const val KEY_PENDING_CODE = "key_pending_invite_code"

        fun newIntent(context: Context, bookingId: String, restaurantId: String? = null): Intent {
            return Intent(context, InviteFriendsActivity::class.java).apply {
                putExtra(EXTRA_BOOKING_ID, bookingId)
                putExtra(EXTRA_RESTAURANT_ID, restaurantId)
            }
        }
    }

    private lateinit var binding: ActivityInviteFriendsBinding
    private val viewModel: InviteFriendsViewModel by viewModels { InviteFriendsViewModel.Factory }

    private lateinit var guestAdapter: InviteGuestAdapter
    private val orderRepository by lazy { OrderRepository() }

    private var currentBookingId: String = ""
    private var currentRestaurantId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityInviteFriendsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        currentBookingId = intent.getStringExtra(EXTRA_BOOKING_ID).orEmpty()
        currentRestaurantId = intent.getStringExtra(EXTRA_RESTAURANT_ID).orEmpty()
        val incomingCode = intent.getStringExtra(EXTRA_INVITE_CODE).orEmpty()

        setupToolbar()
        setupButtons()
        setupRecyclerView()
        observeViewModel()

        if (currentBookingId.isNotBlank()) {
            viewModel.initialize(currentBookingId, currentRestaurantId)
        } else if (incomingCode.isNotBlank()) {
            handleIncomingCode(incomingCode)
        } else {
            checkPendingCodeOnResume()
        }
    }

    override fun onResume() {
        super.onResume()
        checkPendingCodeOnResume()
    }

    private fun checkPendingCodeOnResume() {
        if (FirebaseAuth.getInstance().currentUser != null) {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val pendingCode = prefs.getString(KEY_PENDING_CODE, null)
            if (!pendingCode.isNullOrBlank()) {
                prefs.edit().remove(KEY_PENDING_CODE).apply()
                handleIncomingCode(pendingCode)
            }
        }
    }

    private fun setupToolbar() {
        binding.ivBack.setOnClickListener {
            finish()
        }
    }

    private fun setupButtons() {
        // Sao chép mã
        binding.btnCopyCode.setOnClickListener {
            val code = viewModel.inviteCode.value.orEmpty()
            if (code.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("BookTable Invite Code", code)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, getString(R.string.invite_friends_copied_toast), Toast.LENGTH_SHORT).show()
            }
        }

        // Chia sẻ lời mời qua Android Share Intent
        binding.btnShareInvite.setOnClickListener {
            val shareText = viewModel.shareContent.value.orEmpty()
            if (shareText.isNotBlank()) {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(
                    sendIntent,
                    getString(R.string.invite_friends_share_chooser_title)
                )
                startActivity(shareIntent)
            }
        }

        // Nhập mã mời tham gia đơn khác
        binding.btnEnterInviteCode.setOnClickListener {
            showEnterCodeDialog()
        }

        // Chọn thêm món cho bạn
        binding.btnAddMyDishes.setOnClickListener {
            showAddDishDialog()
        }
    }

    private fun setupRecyclerView() {
        guestAdapter = InviteGuestAdapter()
        binding.rvGuests.apply {
            layoutManager = LinearLayoutManager(this@InviteFriendsActivity)
            adapter = guestAdapter
        }
    }

    private fun showEnterCodeDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.invite_join_dialog_hint)
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.invite_join_dialog_title))
            .setView(input)
            .setPositiveButton(getString(R.string.invite_join_dialog_btn_join)) { _, _ ->
                val code = input.text?.toString().orEmpty().trim()
                if (code.isNotBlank()) {
                    handleIncomingCode(code)
                }
            }
            .setNegativeButton(getString(R.string.split_bill_dialog_btn_cancel), null)
            .show()
    }

    private fun handleIncomingCode(code: String) {
        if (FirebaseAuth.getInstance().currentUser == null) {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PENDING_CODE, code)
                .apply()

            Toast.makeText(this, getString(R.string.invite_join_error_not_logged_in), Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            return
        }

        binding.llLoadingState.visibility = View.VISIBLE
        viewModel.findAndValidateInviteCode(code) { booking, restaurant, errorMsg ->
            binding.llLoadingState.visibility = View.GONE

            if (errorMsg != null) {
                SnackbarHelper.show(binding.root, errorMsg)
                return@findAndValidateInviteCode
            }

            if (booking != null) {
                showConfirmJoinDialog(booking, restaurant)
            }
        }
    }

    private fun showConfirmJoinDialog(booking: Booking, restaurant: Restaurant?) {
        val resName = restaurant?.name ?: "Nhà hàng"
        val time = booking.time
        val date = booking.date
        val host = "Chủ đơn"
        val maxGuests = if (booking.guests > 0) booking.guests else 2L

        val info = getString(
            R.string.invite_join_confirm_info_format,
            resName,
            time,
            date,
            host,
            maxGuests,
            maxGuests
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.invite_join_confirm_title))
            .setMessage(info)
            .setPositiveButton(getString(R.string.invite_join_btn_accept)) { _, _ ->
                binding.llLoadingState.visibility = View.VISIBLE
                viewModel.acceptInvite(booking) { success, err ->
                    binding.llLoadingState.visibility = View.GONE
                    if (success) {
                        Toast.makeText(this, getString(R.string.invite_join_success), Toast.LENGTH_SHORT).show()
                        currentBookingId = booking.id
                        currentRestaurantId = booking.restaurantId
                        viewModel.initialize(booking.id, booking.restaurantId)
                    } else {
                        SnackbarHelper.show(binding.root, err ?: getString(R.string.state_error))
                    }
                }
            }
            .setNegativeButton(getString(R.string.invite_join_btn_decline)) { _, _ ->
                viewModel.declineInvite(booking.id) { success, _ ->
                    if (success) {
                        Toast.makeText(this, getString(R.string.invite_decline_success), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showAddDishDialog() {
        val resId = viewModel.restaurant.value?.id ?: currentRestaurantId
        if (resId.isBlank()) {
            SnackbarHelper.show(binding.root, "Chưa xác định được nhà hàng.")
            return
        }

        binding.llLoadingState.visibility = View.VISIBLE
        orderRepository.getMenuItems(resId) { result ->
            binding.llLoadingState.visibility = View.GONE
            result.onSuccess { menuItems ->
                if (menuItems.isEmpty()) {
                    SnackbarHelper.show(binding.root, "Nhà hàng chưa có món ăn trong thực đơn.")
                    return@onSuccess
                }

                val dishNames = menuItems.map { "${it.name} - ${formatMoney(it.price)}" }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.invite_friends_btn_add_my_dishes))
                    .setItems(dishNames) { _, which ->
                        val selectedDish = menuItems[which]
                        viewModel.addMyOrderItem(selectedDish, 1L) { success, err ->
                            if (success) {
                                SnackbarHelper.show(binding.root, "Đã thêm món ${selectedDish.name} vào đơn!")
                            } else {
                                SnackbarHelper.show(binding.root, err ?: getString(R.string.state_error))
                            }
                        }
                    }
                    .setNegativeButton(getString(R.string.split_bill_dialog_btn_cancel), null)
                    .show()
            }.onFailure { err ->
                SnackbarHelper.show(binding.root, err.message ?: getString(R.string.state_error))
            }
        }
    }

    private fun observeViewModel() {
        viewModel.booking.observe(this) { b ->
            if (b != null) {
                binding.tvBookingDateTime.text = "${b.time} · ${b.date}"
                binding.tvBookingTable.text = "Bàn #${b.tableId}"
                binding.tvBookingGuestsLimit.text = "Tối đa: ${b.guests} khách"
            }
        }

        viewModel.restaurant.observe(this) { res ->
            if (res != null) {
                binding.tvRestaurantName.text = res.name
            }
        }

        viewModel.inviteCode.observe(this) { code ->
            binding.tvInviteCode.text = code
        }

        viewModel.guests.observe(this) { list ->
            guestAdapter.submitList(list)
        }

        viewModel.participantsCountText.observe(this) { countText ->
            binding.tvParticipantsHeader.text = "Những người tham gia ($countText)"
        }

        viewModel.isLoading.observe(this) { loading ->
            binding.llLoadingState.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(this) { err ->
            if (!err.isNullOrBlank()) {
                SnackbarHelper.show(binding.root, err)
            }
        }
    }
}
