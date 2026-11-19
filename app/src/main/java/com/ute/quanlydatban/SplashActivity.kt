package com.ute.quanlydatban

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.WindowCompat
import com.ute.quanlydatban.databinding.ActivitySplashBinding
import com.ute.quanlydatban.ui.auth.LoginActivity
import com.ute.quanlydatban.ui.auth.navigateToHome
import com.ute.quanlydatban.viewmodel.AuthViewModel

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())
    private val viewModel by lazy {
        ViewModelProvider(this, AuthViewModel.Factory)[AuthViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.state.observe(this) { state ->
            if (state.sessionChecked) {
                val role = state.destinationRole
                if (role == null) {
                    startActivity(
                        Intent(this, LoginActivity::class.java)
                            .putExtra(LoginActivity.EXTRA_AUTH_MESSAGE, state.messageRes ?: 0)
                    )
                    finish()
                } else {
                    navigateToHome(role)
                }
            }
        }
        handler.postDelayed({ viewModel.restoreSession() }, SPLASH_DURATION_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val SPLASH_DURATION_MS = 1800L
    }
}
