package com.ute.quanlydatban.util

import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.ute.quanlydatban.R

object SnackbarHelper {
    fun show(view: View, message: String, action: (() -> Unit)? = null) {
        val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_SHORT)
        if (action != null) {
            snackbar.setAction(view.context.getString(R.string.snackbar_action_close)) { action() }
        }
        snackbar.show()
    }
}
