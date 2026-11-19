package com.ute.quanlydatban.ui.auth

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.View
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.snackbar.Snackbar
import com.ute.quanlydatban.MainActivity
import com.ute.quanlydatban.R

@Suppress("DEPRECATION")
internal fun Activity.configureAuthWindow(
    root: View,
    @ColorRes navigationBarColor: Int = R.color.booktable_cream
) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = ContextCompat.getColor(this, navigationBarColor)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        isAppearanceLightStatusBars = true
        isAppearanceLightNavigationBars = navigationBarColor != R.color.booktable_dark
    }

    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        view.setPadding(
            view.paddingLeft,
            bars.top,
            view.paddingRight,
            maxOf(bars.bottom, ime.bottom)
        )
        insets
    }
}

internal fun Activity.showAuthMessage(view: View, messageRes: Int) {
    Snackbar.make(view, messageRes, Snackbar.LENGTH_LONG).show()
}

internal fun Activity.navigateToHome(role: String) {
    startActivity(
        Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ROLE, role)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
    finish()
}
