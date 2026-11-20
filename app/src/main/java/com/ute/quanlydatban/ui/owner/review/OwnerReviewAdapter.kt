package com.ute.quanlydatban.ui.owner.review

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ute.quanlydatban.R
import com.ute.quanlydatban.databinding.ItemReviewBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OwnerReviewAdapter : ListAdapter<ReviewItemUiModel, OwnerReviewAdapter.ReviewViewHolder>(ReviewDiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val binding = ItemReviewBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ReviewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ReviewViewHolder(
        private val binding: ItemReviewBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ReviewItemUiModel) {
            val context = binding.root.context

            binding.tvCustomerName.text = item.userName.ifBlank {
                context.getString(R.string.owner_review_customer_default_name)
            }

            if (item.createdAt > 0) {
                binding.tvReviewTime.text = dateFormat.format(Date(item.createdAt))
                binding.tvReviewTime.visibility = View.VISIBLE
            } else {
                binding.tvReviewTime.visibility = View.GONE
            }

            // Star rating visual
            val starViews = listOf(
                binding.ivStar1,
                binding.ivStar2,
                binding.ivStar3,
                binding.ivStar4,
                binding.ivStar5
            )
            starViews.forEachIndexed { index, starView ->
                if (index < item.rating) {
                    starView.setImageResource(R.drawable.ic_star)
                } else {
                    starView.setImageResource(R.drawable.ic_star_outline)
                }
            }

            // Comment text
            if (item.comment.isBlank()) {
                binding.tvComment.visibility = View.GONE
            } else {
                binding.tvComment.visibility = View.VISIBLE
                binding.tvComment.text = item.comment
            }

            binding.ivCustomerAvatar.setImageResource(R.drawable.ic_customer_default_avatar)
        }
    }

    class ReviewDiffCallback : DiffUtil.ItemCallback<ReviewItemUiModel>() {
        override fun areItemsTheSame(oldItem: ReviewItemUiModel, newItem: ReviewItemUiModel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ReviewItemUiModel, newItem: ReviewItemUiModel): Boolean {
            return oldItem == newItem
        }
    }
}
