package com.gpdb.android.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.gpdb.android.R

object ScraperNotificationHelper {
    private const val CHANNEL_ID = "gpdb_scraper_channel"
    private const val NOTIFICATION_ID = 9001

    fun initChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = I18n.t("scraper.notificationTitle", defaultVal = "GPDb 影视刮削器")
            val channel = NotificationChannel(
                CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time GPDb scraper background synchronization status"
                setSound(null, null)
                enableVibration(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showCheckingNotification(context: Context) {
        initChannel(context)
        val title = I18n.t("scraper.notificationTitle", defaultVal = "GPDb 影视刮削器")
        val content = I18n.t("scraper.checking", defaultVal = "正在检查在线增量更新...")

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(content)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun updateProgressNotification(context: Context, current: Int, total: Int, item: String) {
        initChannel(context)
        val title = I18n.t("scraper.notificationTitle", defaultVal = "GPDb 影视刮削器")
        val contentTemplate = I18n.t("scraper.syncingProgress", defaultVal = "正在同步元数据 ({current}/{total}): {item}")
        val content = contentTemplate
            .replace("{current}", current.toString())
            .replace("{total}", total.toString())
            .replace("{item}", item)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(content)
            .setProgress(total, current, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun showCompletedNotification(context: Context, movies: Int, episodes: Int) {
        initChannel(context)
        val title = I18n.t("scraper.notificationTitle", defaultVal = "GPDb 影视刮削器")
        val contentTemplate = I18n.t("scraper.syncCompleted", defaultVal = "增量刮削完成：新增 {movies} 部影片，{episodes} 个分集")
        val content = contentTemplate
            .replace("{movies}", movies.toString())
            .replace("{episodes}", episodes.toString())

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(content)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun cancelNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
