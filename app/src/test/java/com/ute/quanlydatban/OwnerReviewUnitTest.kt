package com.ute.quanlydatban

import com.ute.quanlydatban.data.model.Review
import com.ute.quanlydatban.data.repository.ReviewRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerReviewUnitTest {

    @Test
    fun calculateAverageRating_emptyList_returnsZero() {
        val reviews = emptyList<Review>()
        val avg = ReviewRepository.calculateAverageRating(reviews)
        assertEquals(0.0, avg, 0.001)
    }

    @Test
    fun calculateAverageRating_singleReview_returnsExactRating() {
        val reviews = listOf(
            Review(id = "1", rating = 5)
        )
        val avg = ReviewRepository.calculateAverageRating(reviews)
        assertEquals(5.0, avg, 0.001)
    }

    @Test
    fun calculateAverageRating_multipleReviews_roundsToOneDecimalPlace() {
        // 5 + 5 + 4 = 14 / 3 = 4.6666... -> 4.7
        val reviews1 = listOf(
            Review(id = "1", rating = 5),
            Review(id = "2", rating = 5),
            Review(id = "3", rating = 4)
        )
        val avg1 = ReviewRepository.calculateAverageRating(reviews1)
        assertEquals(4.7, avg1, 0.001)

        // 5 + 4 = 9 / 2 = 4.5
        val reviews2 = listOf(
            Review(id = "1", rating = 5),
            Review(id = "2", rating = 4)
        )
        val avg2 = ReviewRepository.calculateAverageRating(reviews2)
        assertEquals(4.5, avg2, 0.001)

        // 1 + 1 + 2 = 4 / 3 = 1.333... -> 1.3
        val reviews3 = listOf(
            Review(id = "1", rating = 1),
            Review(id = "2", rating = 1),
            Review(id = "3", rating = 2)
        )
        val avg3 = ReviewRepository.calculateAverageRating(reviews3)
        assertEquals(1.3, avg3, 0.001)
    }

    @Test
    fun filterReviewsByStar_nullOrInvalidStar_returnsAll() {
        val reviews = listOf(
            Review(id = "1", rating = 5),
            Review(id = "2", rating = 4),
            Review(id = "3", rating = 3)
        )
        assertEquals(3, ReviewRepository.filterReviewsByStar(reviews, null).size)
        assertEquals(3, ReviewRepository.filterReviewsByStar(reviews, 0).size)
        assertEquals(3, ReviewRepository.filterReviewsByStar(reviews, 6).size)
    }

    @Test
    fun filterReviewsByStar_specificStar_filtersCorrectly() {
        val reviews = listOf(
            Review(id = "1", rating = 5, comment = "Tuyệt vời"),
            Review(id = "2", rating = 5, comment = "Ngon"),
            Review(id = "3", rating = 4, comment = "Ổn"),
            Review(id = "4", rating = 2, comment = "Hơi lâu")
        )

        val star5 = ReviewRepository.filterReviewsByStar(reviews, 5)
        assertEquals(2, star5.size)
        assertTrue(star5.all { it.rating == 5L })

        val star4 = ReviewRepository.filterReviewsByStar(reviews, 4)
        assertEquals(1, star4.size)
        assertEquals("3", star4.first().id)

        val star3 = ReviewRepository.filterReviewsByStar(reviews, 3)
        assertTrue(star3.isEmpty())

        val star2 = ReviewRepository.filterReviewsByStar(reviews, 2)
        assertEquals(1, star2.size)
        assertEquals("4", star2.first().id)
    }

    @Test
    fun reviewComment_blankDetection() {
        val reviewWithComment = Review(id = "1", rating = 5, comment = "Ngon tuyệt")
        val reviewEmptyComment = Review(id = "2", rating = 5, comment = "")
        val reviewWhitespaceComment = Review(id = "3", rating = 5, comment = "   ")

        assertFalse(reviewWithComment.comment.isBlank())
        assertTrue(reviewEmptyComment.comment.isBlank())
        assertTrue(reviewWhitespaceComment.comment.isBlank())
    }

    @Test
    fun reviewSorting_newestFirst() {
        val reviews = listOf(
            Review(id = "1", createdAt = 1000L),
            Review(id = "2", createdAt = 5000L),
            Review(id = "3", createdAt = 3000L)
        )
        val sorted = reviews.sortedByDescending { it.createdAt }
        assertEquals("2", sorted[0].id)
        assertEquals("3", sorted[1].id)
        assertEquals("1", sorted[2].id)
    }
}
