package com.github.chsxthwik.gochat.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.chsxthwik.gochat.MainActivity
import com.github.chsxthwik.gochat.R

class ReplyNotifier(private val ctx: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val ch = NotificationChannel(CHANNEL_ID, "Replies", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Finished assistant replies" }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun reply(convId: String, title: String, preview: String) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val intent = Intent(ctx, MainActivity::class.java)
            .putExtra(EXTRA_CONVERSATION, convId)
        val pending = PendingIntent.getActivity(
            ctx, convId.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title.ifBlank { "GoChat" })
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        nm.notify(convId.hashCode(), n)
    }

    companion object {
        const val CHANNEL_ID = "replies"
        const val EXTRA_CONVERSATION = "gochat.conv"
    }
}
