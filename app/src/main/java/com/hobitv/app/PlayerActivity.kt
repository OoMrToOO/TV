package com.hobitv.app

import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem as M3MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@UnstableApi
class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private val zapTitles = arrayListOf<String>()
    private val zapUrls = arrayListOf<String>()
    private var zapIndex = 0
    private var title = "Hobi TV"
    private var kind = MediaKind.MOVIE
    private var poster: String? = null
    private var positionAtStart = 0L
    private var currentUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentUrl = intent.getStringExtra("url").orEmpty()
        title = intent.getStringExtra("title").orEmpty().ifBlank { "Hobi TV" }
        kind = runCatching { MediaKind.valueOf(intent.getStringExtra("kind").orEmpty()) }.getOrDefault(MediaKind.MOVIE)
        poster = intent.getStringExtra("poster")
        zapTitles.addAll(intent.getStringArrayListExtra("zap_titles") ?: arrayListOf())
        zapUrls.addAll(intent.getStringArrayListExtra("zap_urls") ?: arrayListOf())
        zapIndex = intent.getIntExtra("zap_index", 0).coerceIn(0, (zapUrls.size - 1).coerceAtLeast(0))
        if (currentUrl.isBlank()) { finish(); return }
        view = PlayerView(this).apply { useController = true; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING); keepScreenOn = true }
        setContentView(FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK); addView(view, FrameLayout.LayoutParams(-1, -1)) })
        val resumeKey = MediaItem(intent.getStringExtra("id").orEmpty(), title, kind, poster)
        createPlayer(currentUrl, WatchStore(this).position(resumeKey))
    }

    private fun createPlayer(url: String, resume: Long) {
        player?.release()
        currentUrl = url
        player = ExoPlayer.Builder(this).build().also { p ->
            view.player = p
            p.setMediaItem(M3MediaItem.fromUri(Uri.parse(url)))
            p.prepare()
            if (resume > 0) p.seekTo(resume)
            p.playWhenReady = true
        }
    }

    private fun zap(delta: Int) {
        if (zapUrls.size < 2) return
        zapIndex = (zapIndex + delta + zapUrls.size) % zapUrls.size
        title = zapTitles.getOrNull(zapIndex) ?: title
        createPlayer(zapUrls[zapIndex], 0L)
        view.showController()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) when (event.keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP -> { zap(-1); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(1); return true }
            KeyEvent.KEYCODE_DPAD_UP -> if (kind == MediaKind.LIVE) { zap(-1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (kind == MediaKind.LIVE) { zap(1); return true }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        player?.let { p ->
            if (kind != MediaKind.LIVE && p.duration > 0) WatchStore(this).save(MediaItem(intent.getStringExtra("id").orEmpty(), title, kind, poster), p.currentPosition, p.duration)
            p.pause()
        }
        super.onStop()
    }
    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
}
