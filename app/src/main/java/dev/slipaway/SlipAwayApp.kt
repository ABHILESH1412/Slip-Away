package dev.slipaway

import android.app.Application
import dev.slipaway.data.Store

class SlipAwayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Load settings once, up front, for every part of the app (screens, call service, tile, alarm).
        Store.get(this)
    }
}
