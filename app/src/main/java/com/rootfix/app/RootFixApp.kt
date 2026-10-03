package com.rootfix.app

import android.app.Application
import com.topjohnwu.superuser.Shell

class RootFixApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Configure libsu root shell defaults
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(15)
        )
    }
}
