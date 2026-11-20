package com.ute.quanlydatban

import android.app.Application
import android.content.Context

class BookTableApplication : Application() {

    companion object {
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }
}
