package dev.sonixdevs.kodra

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationHelper {
    const val CHANNEL_ID = "kodra_server"

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    ctx.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW // no sound, low importance
                ).apply {
                    description = ctx.getString(R.string.notification_channel_desc)
                    setShowBadge(false)
                }
            )
        }
    }
}
