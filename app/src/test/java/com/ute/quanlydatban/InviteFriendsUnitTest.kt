package com.ute.quanlydatban

import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingGuest
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.repository.InviteFriendsValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteFriendsUnitTest {

    @Test
    fun validateJoin_selfInvite_returnsFailure() {
        val booking = Booking(
            id = "B_001",
            userId = "USER_HOST",
            guests = 4,
            status = BookingStatus.CONFIRMED.name
        )

        val result = InviteFriendsValidator.validateJoinEligibility(
            booking = booking,
            existingGuests = emptyList(),
            currentUserId = "USER_HOST"
        )

        assertTrue(result.isFailure)
        assertEquals("SELF_INVITE", result.exceptionOrNull()?.message)
    }

    @Test
    fun validateJoin_closedBookingStatus_returnsFailure() {
        val closedStatuses = listOf(
            BookingStatus.CANCELLED.name,
            BookingStatus.CHECKED_IN.name,
            BookingStatus.SERVING.name,
            BookingStatus.PAID.name,
            BookingStatus.EXPIRED.name
        )

        for (status in closedStatuses) {
            val booking = Booking(
                id = "B_002",
                userId = "USER_HOST",
                guests = 4,
                status = status
            )

            val result = InviteFriendsValidator.validateJoinEligibility(
                booking = booking,
                existingGuests = emptyList(),
                currentUserId = "USER_GUEST"
            )

            assertTrue(result.isFailure)
            assertEquals("NOT_OPEN", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun validateJoin_exceedsGuestsCapacity_returnsFailure() {
        val booking = Booking(
            id = "B_003",
            userId = "USER_HOST",
            guests = 3, // Tối đa 3 người (bao gồm cả chủ đơn)
            status = BookingStatus.CONFIRMED.name
        )

        // Đã có 2 người bạn được accepted (chủ đơn + 2 bạn = 3 người)
        val existingGuests = listOf(
            BookingGuest(id = "G1", bookingId = "B_003", userId = "USER_1", status = "accepted"),
            BookingGuest(id = "G2", bookingId = "B_003", userId = "USER_2", status = "accepted")
        )

        val result = InviteFriendsValidator.validateJoinEligibility(
            booking = booking,
            existingGuests = existingGuests,
            currentUserId = "USER_3"
        )

        assertTrue(result.isFailure)
        assertEquals("FULL", result.exceptionOrNull()?.message)
    }

    @Test
    fun validateJoin_alreadyAccepted_preventsDuplicateJoin() {
        val booking = Booking(
            id = "B_004",
            userId = "USER_HOST",
            guests = 4,
            status = BookingStatus.CONFIRMED.name
        )

        val existingGuests = listOf(
            BookingGuest(id = "G1", bookingId = "B_004", userId = "USER_1", status = "accepted")
        )

        val result = InviteFriendsValidator.validateJoinEligibility(
            booking = booking,
            existingGuests = existingGuests,
            currentUserId = "USER_1" // Cố bấm chấp nhận lần 2
        )

        assertTrue(result.isFailure)
        assertEquals("ALREADY_JOINED", result.exceptionOrNull()?.message)
    }

    @Test
    fun validateJoin_notLoggedIn_returnsFailure() {
        val booking = Booking(
            id = "B_005",
            userId = "USER_HOST",
            guests = 4,
            status = BookingStatus.CONFIRMED.name
        )

        val result = InviteFriendsValidator.validateJoinEligibility(
            booking = booking,
            existingGuests = emptyList(),
            currentUserId = ""
        )

        assertTrue(result.isFailure)
        assertEquals("NOT_LOGGED_IN", result.exceptionOrNull()?.message)
    }

    @Test
    fun validateJoin_validInvitation_returnsSuccess() {
        val booking = Booking(
            id = "B_006",
            userId = "USER_HOST",
            guests = 4,
            status = BookingStatus.CONFIRMED.name
        )

        val existingGuests = listOf(
            BookingGuest(id = "G1", bookingId = "B_006", userId = "USER_1", status = "accepted"),
            BookingGuest(id = "G2", bookingId = "B_006", userId = "USER_2", status = "declined") // người từ chối không chiếm slot
        )

        val result = InviteFriendsValidator.validateJoinEligibility(
            booking = booking,
            existingGuests = existingGuests,
            currentUserId = "USER_NEW"
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun validateJoin_nullOrInvalidBooking_returnsFailure() {
        val result = InviteFriendsValidator.validateJoinEligibility(
            booking = null,
            existingGuests = emptyList(),
            currentUserId = "USER_GUEST"
        )

        assertTrue(result.isFailure)
    }
}
