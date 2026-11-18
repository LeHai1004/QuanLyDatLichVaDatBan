package com.ute.quanlydatban.util

import android.view.View
import android.widget.TextView
import com.ute.quanlydatban.R

object UiStateHelper {
    fun showLoading(root: View) {
        setStates(root, loading = true)
    }

    fun showEmpty(root: View) {
        setStates(root, empty = true)
    }

    fun showError(root: View, message: String) {
        setStates(root, error = true)
        root.findViewById<TextView>(R.id.tvErrorMessage)?.text = message
    }

    fun hideAll(root: View) {
        setStates(root)
    }

    private fun setStates(root: View, loading: Boolean = false, empty: Boolean = false, error: Boolean = false) {
        root.findViewById<View>(R.id.llLoadingState)?.visibility =
            if (loading) View.VISIBLE else View.GONE
        root.findViewById<View>(R.id.llEmptyState)?.visibility =
            if (empty) View.VISIBLE else View.GONE
        root.findViewById<View>(R.id.llErrorState)?.visibility =
            if (error) View.VISIBLE else View.GONE
    }
}
