package com.example

import android.app.Application

class ViabrplayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LogKeeper.init(this)
    }
}
