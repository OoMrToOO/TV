package com.hobitv.app
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun PlayerScreen(media:Media,startPosition:Long,onSave:(Long)->Unit,onBack:()->Unit) {
    val context=LocalContext.current
    val player=remember(media.streamUrl) {
        if(media.streamUrl.isNullOrBlank()) null else ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(media.streamUrl)))
            prepare(); seekTo(startPosition); playWhenReady=true
        }
    }
    DisposableEffect(player) {
        onDispose { player?.currentPosition?.let(onSave); player?.release() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if(player!=null) AndroidView(
            factory={ctx->PlayerView(ctx).apply{this.player=player;useController=true}},
            modifier=Modifier.fillMaxSize()
        ) else Text(
            "Bu içerik için yetkili yayın adresi henüz bağlanmadı.",
            color=Color.White, modifier=Modifier.padding(48.dp)
        )
    }
}
