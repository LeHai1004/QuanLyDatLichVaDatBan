package com.ute.quanlydatban.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingGuest
import com.ute.quanlydatban.data.model.MenuItem
import com.ute.quanlydatban.data.model.OrderItem
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.data.repository.BookingGuestRepository
import com.ute.quanlydatban.data.repository.InviteFriendsValidator
import com.ute.quanlydatban.data.repository.RestaurantRepository
import com.ute.quanlydatban.data.repository.UserRepository
import com.ute.quanlydatban.ui.customer.invite.GuestUiModel

class InviteFriendsViewModel(
    private val guestRepository: BookingGuestRepository = BookingGuestRepository(),
    private val restaurantRepository: RestaurantRepository = RestaurantRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return InviteFriendsViewModel() as T
            }
        }
    }

    private var guestListener: ListenerRegistration? = null
    private var orderItemsListener: ListenerRegistration? = null

    private var currentBooking: Booking? = null
    private var currentGuestsList: List<BookingGuest> = emptyList()
    private var currentOrderItemsList: List<OrderItem> = emptyList()

    private val _booking = MutableLiveData<Booking?>()
    val booking: LiveData<Booking?> = _booking

    private val _restaurant = MutableLiveData<Restaurant?>()
    val restaurant: LiveData<Restaurant?> = _restaurant

    private val _guests = MutableLiveData<List<GuestUiModel>>(emptyList())
    val guests: LiveData<List<GuestUiModel>> = _guests

    private val _inviteCode = MutableLiveData<String>("")
    val inviteCode: LiveData<String> = _inviteCode

    private val _shareContent = MutableLiveData<String>("")
    val shareContent: LiveData<String> = _shareContent

    private val _participantsCountText = MutableLiveData<String>("1/2")
    val participantsCountText: LiveData<String> = _participantsCountText

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    val currentUserId: String
        get() = auth.currentUser?.uid.orEmpty()

    fun initialize(bookingId: String, passedRestaurantId: String? = null) {
        if (bookingId.isBlank()) return

        _isLoading.value = true
        _errorMessage.value = null

        guestRepository.getBooking(bookingId) { result ->
            result.onSuccess { b ->
                if (b == null) {
                    _isLoading.postValue(false)
                    _errorMessage.postValue("Không tìm thấy thông tin đơn đặt bàn.")
                    return@onSuccess
                }

                currentBooking = b
                _booking.postValue(b)

                val displayCode = "INVITE-BT${b.id.takeLast(6).uppercase()}"
                _inviteCode.postValue(displayCode)

                val resId = if (!passedRestaurantId.isNullOrBlank()) passedRestaurantId else b.restaurantId
                if (resId.isNotBlank()) {
                    restaurantRepository.get(resId) { resResult ->
                        resResult.onSuccess { res ->
                            _restaurant.postValue(res)
                            updateShareContent(b, res, displayCode)
                        }
                    }
                }

                listenRealtimeData(b)
            }.onFailure { err ->
                _isLoading.postValue(false)
                _errorMessage.postValue(err.message ?: "Lỗi tải thông tin đơn đặt bàn")
            }
        }
    }

    private fun listenRealtimeData(b: Booking) {
        guestListener?.remove()
        orderItemsListener?.remove()

        guestListener = guestRepository.listenGuestsForBooking(b.id) { result ->
            result.onSuccess { guestsList ->
                currentGuestsList = guestsList
                rebuildGuestsUi()
            }
        }

        orderItemsListener = guestRepository.listenOrderItemsForBooking(b.id) { result ->
            _isLoading.postValue(false)
            result.onSuccess { itemsList ->
                currentOrderItemsList = itemsList
                rebuildGuestsUi()
            }
        }
    }

    private fun rebuildGuestsUi() {
        val b = currentBooking ?: return
        val uiList = mutableListOf<GuestUiModel>()

        // 1. Chủ đơn
        val hostItems = currentOrderItemsList.filter { it.userId == b.userId || it.userId.isBlank() }
        val hostDishesText = hostItems.joinToString(", ") { "${it.qty}x ${it.name}" }

        uiList.add(
            GuestUiModel(
                id = "HOST_${b.id}",
                userId = b.userId,
                name = "Chủ đơn (Bạn)",
                status = "accepted",
                isHost = true,
                orderedDishesText = hostDishesText
            )
        )

        // 2. Những người được mời
        for (guest in currentGuestsList) {
            val guestItems = currentOrderItemsList.filter { it.userId == guest.userId && guest.userId.isNotBlank() }
            val guestDishesText = guestItems.joinToString(", ") { "${it.qty}x ${it.name}" }

            val displayName = if (guest.userId == currentUserId) {
                "${guest.userName} (Bạn)"
            } else {
                guest.userName.ifBlank { "Khách mời" }
            }

            uiList.add(
                GuestUiModel(
                    id = guest.id,
                    userId = guest.userId,
                    name = displayName,
                    avatarUrl = guest.avatarUrl,
                    status = guest.status,
                    isHost = false,
                    orderedDishesText = guestDishesText
                )
            )
        }

        _guests.postValue(uiList)

        val acceptedCount = currentGuestsList.count { it.status == "accepted" } + 1
        val maxGuests = if (b.guests > 0) b.guests else 2L
        _participantsCountText.postValue("$acceptedCount/$maxGuests")
    }

    private fun updateShareContent(b: Booking, res: Restaurant?, code: String) {
        val resName = res?.name ?: "nhà hàng"
        val time = b.time
        val date = b.date
        val content = "Mình mời bạn tham gia bữa ăn tại $resName vào lúc $time ngày $date! Nhập mã mời: $code (hoặc ${b.id}) trong ứng dụng BookTable nhé!"
        _shareContent.postValue(content)
    }

    fun findAndValidateInviteCode(
        rawCode: String,
        onResult: (Booking?, Restaurant?, String?) -> Unit
    ) {
        val code = rawCode.trim()
        if (code.isBlank()) {
            onResult(null, null, "Mã mời không tồn tại hoặc không hợp lệ.")
            return
        }

        guestRepository.findBookingByCode(code) { result ->
            result.onSuccess { foundBooking ->
                if (foundBooking == null) {
                    onResult(null, null, "Mã mời không tồn tại hoặc không hợp lệ.")
                    return@onSuccess
                }

                // Check 7 validation rules
                guestRepository.listenGuestsForBooking(foundBooking.id) { guestSnapResult ->
                    val guests = guestSnapResult.getOrDefault(emptyList())
                    val validation = InviteFriendsValidator.validateJoinEligibility(
                        foundBooking,
                        guests,
                        currentUserId
                    )

                    if (validation.isFailure) {
                        val errorType = validation.exceptionOrNull()?.message.orEmpty()
                        val msg = when (errorType) {
                            "NOT_LOGGED_IN" -> "Vui lòng đăng nhập để tham gia đơn đặt bàn."
                            "SELF_INVITE" -> "Bạn là chủ của đơn đặt bàn này."
                            "NOT_OPEN" -> "Đơn đặt bàn này hiện không còn nhận khách mời."
                            "ALREADY_JOINED" -> "Bạn đã tham gia đơn đặt bàn này rồi."
                            "FULL" -> "Đơn đặt bàn đã đủ số lượng khách."
                            else -> "Mã mời không tồn tại hoặc không hợp lệ."
                        }
                        onResult(foundBooking, null, msg)
                        return@listenGuestsForBooking
                    }

                    // Tải thông tin quán
                    restaurantRepository.get(foundBooking.restaurantId) { resResult ->
                        val res = resResult.getOrNull()
                        onResult(foundBooking, res, null)
                    }
                }
            }.onFailure { err ->
                onResult(null, null, err.message ?: "Lỗi tìm kiếm đơn đặt bàn")
            }
        }
    }

    fun acceptInvite(
        booking: Booking,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (currentUserId.isBlank()) {
            onResult(false, "Vui lòng đăng nhập để tham gia đơn đặt bàn.")
            return
        }

        userRepository.getUserProfile(currentUserId) { uResult ->
            val user = uResult.getOrNull()
            val userName = user?.name ?: "Bạn bè"
            val avatarUrl = user?.avatarUrl.orEmpty()

            guestRepository.acceptInvitation(booking, currentUserId, userName, avatarUrl) { joinResult ->
                joinResult.onSuccess {
                    onResult(true, null)
                }.onFailure { err ->
                    val msg = when (err.message) {
                        "ALREADY_JOINED" -> "Bạn đã tham gia đơn đặt bàn này rồi."
                        "FULL" -> "Đơn đặt bàn đã đủ số lượng khách."
                        "NOT_OPEN" -> "Đơn đặt bàn này hiện không còn nhận khách mời."
                        "SELF_INVITE" -> "Bạn là chủ của đơn đặt bàn này."
                        else -> err.message ?: "Lỗi tham gia đơn đặt bàn"
                    }
                    onResult(false, msg)
                }
            }
        }
    }

    fun declineInvite(
        bookingId: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (currentUserId.isBlank()) {
            onResult(false, "Chưa đăng nhập")
            return
        }

        guestRepository.declineInvitation(bookingId, currentUserId) { result ->
            result.onSuccess {
                onResult(true, null)
            }.onFailure { err ->
                onResult(false, err.message ?: "Lỗi từ chối lời mời")
            }
        }
    }

    fun addMyOrderItem(
        menuItem: MenuItem,
        qty: Long,
        onResult: (Boolean, String?) -> Unit
    ) {
        val b = currentBooking
        if (b == null) {
            onResult(false, "Chưa tải thông tin đơn đặt bàn")
            return
        }

        userRepository.getUserProfile(currentUserId) { uResult ->
            val user = uResult.getOrNull()
            val userName = user?.name ?: "Khách mời"

            guestRepository.addGuestOrderItem(b.id, menuItem, qty, currentUserId, userName) { result ->
                result.onSuccess {
                    onResult(true, null)
                }.onFailure { err ->
                    onResult(false, err.message ?: "Lỗi thêm món ăn")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        guestListener?.remove()
        orderItemsListener?.remove()
    }
}
