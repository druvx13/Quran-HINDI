package com.quran.hindi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service for background Quran audio playback.
 *
 * The WebView handles inline `<audio>` elements directly, but this service can be used
 * to continue playback when the user navigates away from the app or locks the screen.
 * Call [play], [pause], and [stop] via the [LocalBinder] obtained from [onBind].
 */
class AudioService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var currentUrl: String? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> intent.getStringExtra(EXTRA_URL)?.let { play(it) }
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    // -----------------------------------------------------------------------------------------
    // Public API (called via LocalBinder or Intent actions)
    // -----------------------------------------------------------------------------------------

    fun play(url: String) {
        if (url == currentUrl && mediaPlayer?.isPlaying == true) return

        releasePlayer()
        currentUrl = url

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(url)
            setOnPreparedListener { start() }
            setOnCompletionListener { stopForeground(STOP_FOREGROUND_REMOVE) }
            prepareAsync()
        }
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    fun pause() {
        if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
    }

    fun resume() {
        if (mediaPlayer?.isPlaying == false) mediaPlayer?.start()
    }

    fun stopPlayback() {
        releasePlayer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    val isPlaying: Boolean get() = mediaPlayer?.isPlaying == true

    // -----------------------------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------------------------

    private fun releasePlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        currentUrl = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.audio_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.audio_channel_desc)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, AudioService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.audio_playing))
            .setSmallIcon(R.drawable.ic_audio_notification)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.audio_stop), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_PLAY = "com.quran.hindi.ACTION_PLAY"
        const val ACTION_PAUSE = "com.quran.hindi.ACTION_PAUSE"
        const val ACTION_STOP = "com.quran.hindi.ACTION_STOP"
        const val EXTRA_URL = "extra_url"

        private const val CHANNEL_ID = "quran_audio_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
