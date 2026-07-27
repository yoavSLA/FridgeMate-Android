package com.project.fridgemate

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.project.fridgemate.data.local.AppDatabase
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.remote.PicassoCache

class FridgeMateApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)
        AppDatabase.getInstance(this)
        PicassoCache.init(this)
        registerActivityLifecycleCallbacks(ForegroundTracker)
        NotificationHelper.createNotificationChannel(this)
    }

    companion object {
        val isForeground: Boolean get() = ForegroundTracker.startedCount > 0
    }

    private object ForegroundTracker : ActivityLifecycleCallbacks {
        var startedCount: Int = 0
            private set

        override fun onActivityStarted(activity: Activity) { startedCount++ }
        override fun onActivityStopped(activity: Activity) {
            startedCount = (startedCount - 1).coerceAtLeast(0)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
}
