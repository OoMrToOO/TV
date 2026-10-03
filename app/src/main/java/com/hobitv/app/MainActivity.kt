package com.hobitv.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage

private val Bg = Color(0xFF050609)
private val Surface = Color(0xFF101116)
private val Surface2 = Color(0xFF17191F)
private val White = Color(0xFFF5F5F7)
private val Muted = Color(0xFFA0A3AD)
private val Accent = Color(0xFFE50914)
private val Gold = Color(0xFFFFC857)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { HobiApp() } }

    fun play(item: MediaItem, source: StreamSource? = null, zap: List<MediaItem> = emptyList()) {
        val url = source?.url ?: item.streamUrl ?: return
        val i = Intent(this, PlayerActivity::class.java)
            .putExtra("title", item.title).putExtra("id", item.id).putExtra("url", url).putExtra("kind", item.kind.name).putExtra("poster", item.poster)
            .putStringArrayListExtra("zap_titles", ArrayList(zap.map { it.title }))
            .putStringArrayListExtra("zap_urls", ArrayList(zap.mapNotNull { it.streamUrl }))
            .putExtra("zap_index", zap.indexOfFirst { it.id == item.id }.coerceAtLeast(0))
        startActivity(i)
    }
}

@Composable
fun HobiApp(vm: HobiViewModel = viewModel()) {
    val movies by vm.movies.collectAsState(); val series by vm.series.collectAsState(); val live by vm.live.collectAsState()
    val tmdb by vm.tmdb.collectAsState(); val addons by vm.installed.collectAsState(); val streams by vm.streams.collectAsState()
    val detail by vm.detail.collectAsState(); val zap by vm.zap.collectAsState(); val loading by vm.loading.collectAsState(); val message by vm.message.collectAsState()
    var tab by remember { mutableStateOf(0) }; var settings by remember { mutableStateOf(false) }; var search by remember { mutableStateOf(false) }
    val activity = androidx.compose.ui.platform.LocalContext.current as MainActivity

    LaunchedEffect(zap) { if (zap.isNotEmpty()) { activity.play(zap.first(), zap = zap); vm.clearZap() } }
    BackHandler(enabled = detail != null || settings || search) { when { detail != null -> vm.clearDetail(); settings -> settings = false; search -> search = false } }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Surface)) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            Column(Modifier.fillMaxSize()) {
                Header(tab, { tab = it }, { search = true }, { settings = true })
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        0 -> Home(tmdb, movies, series, live, loading, { vm.openDetail(it) })
                        1 -> LibraryPage("Filmler", "Kaynaklardan ve TMDB'den gelen katalog", movies + tmdb.filter { it.kind == MediaKind.MOVIE }, vm::openDetail)
                        2 -> LibraryPage("Diziler", "Sezon ve bölüm kaynakları", series + tmdb.filter { it.kind == MediaKind.SERIES }, vm::openDetail)
                        else -> LivePage(live, { vm.prepareZap(live) }, vm::openDetail)
                    }
                }
            }
            if (message != null) ToastMessage(message!!)
            detail?.let { Detail(it, streams, { source -> activity.play(it, source) }, { vm.clearDetail() }) }
            if (settings) SettingsPage(addons, vm::install, vm::remove) { settings = false }
            if (search) SearchPage(vm, { search = false }) { vm.openDetail(it); search = false }
        }
    }
}

@Composable private fun Header(tab: Int, select: (Int) -> Unit, search: () -> Unit, settings: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(82.dp).background(Color(0xF5050609)).padding(horizontal = 46.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("HOBİ", color = Accent, fontSize = 28.sp, fontWeight = FontWeight.Black); Text("TV", color = White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(42.dp))
        listOf("Ana Sayfa", "Filmler", "Diziler", "Canlı TV").forEachIndexed { i, label -> NavButton(label, i == tab) { select(i) } }
        Spacer(Modifier.weight(1f)); NavButton("⌕  Ara", false, search); Spacer(Modifier.width(8.dp)); NavButton("⚙", false, settings)
    }
}

@Composable private fun NavButton(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }; val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "nav")
    Text(text, color = if (selected || focused) White else Muted, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 10.dp).scale(scale).onFocusChanged { focused = it.isFocused }.focusable().clickable { onClick() }.padding(10.dp))
}

@Composable private fun Home(tmdb: List<MediaItem>, movies: List<MediaItem>, series: List<MediaItem>, live: List<MediaItem>, loading: Boolean, open: (MediaItem) -> Unit) {
    val hero = tmdb.firstOrNull { it.backdrop != null } ?: movies.firstOrNull() ?: series.firstOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (hero != null) Hero(hero, open) else EmptyHero(loading)
        Rail("Devam Edenler", "Kaldığın yerden devam et", emptyList(), open)
        Rail("Şimdi Trend", "TMDB keşif akışı", tmdb, open)
        Rail("Popüler Filmler", "Kaynaklardan gelen içerikler", movies, open)
        Rail("Diziler", "Sezon ve bölüm içerikleri", series, open)
        Rail("Canlı TV", "Kanal listesi", live, open, true)
        Spacer(Modifier.height(80.dp))
    }
}

@Composable private fun EmptyHero(loading: Boolean) {
    Box(Modifier.fillMaxWidth().height(470.dp).background(Brush.verticalGradient(listOf(Surface2, Bg))), contentAlignment = Alignment.CenterStart) {
        Column(Modifier.padding(start = 58.dp)) { Text("HOBİ TV", color = White, fontSize = 52.sp, fontWeight = FontWeight.Black); Text(if (loading) "İçerikler yükleniyor…" else "Kaynak ekleyerek keşfetmeye başlayın.", color = Muted, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp)) }
    }
}

@Composable private fun Hero(item: MediaItem, open: (MediaItem) -> Unit) {
    Box(Modifier.fillMaxWidth().height(500.dp)) {
        AsyncImage(item.backdrop ?: item.poster, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Bg, Bg.copy(alpha = .78f), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Bg))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 58.dp, bottom = 52.dp).width(650.dp)) {
            Text(item.title, color = White, fontSize = 44.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 12.dp)) { item.year?.let { Text(it, color = Muted) }; if (item.rating > 0) Text("★ ${"%.1f".format(item.rating)}", color = Gold) }
            Text(item.overview.ifBlank { "İzlemek için keşfet." }, color = Color(0xFFE1E2E7), fontSize = 16.sp, lineHeight = 24.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 15.dp))
            Button(onClick = { open(item) }, modifier = Modifier.padding(top = 22.dp)) { Text("▶  Detayları Aç") }
        }
    }
}

@Composable private fun Rail(title: String, subtitle: String, items: List<MediaItem>, open: (MediaItem) -> Unit, landscape: Boolean = false) {
    if (items.isEmpty()) return
    Column(Modifier.padding(top = 26.dp)) {
        Column(Modifier.padding(horizontal = 46.dp)) { Text(title, color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold); if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)) }
        LazyRow(contentPadding = PaddingValues(46.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { items(items.take(40), key = { "${it.kind}:${it.id}:${it.addonId}" }) { CardItem(it, open, landscape) } }
    }
}

@Composable private fun CardItem(item: MediaItem, open: (MediaItem) -> Unit, landscape: Boolean) {
    var focused by remember { mutableStateOf(false) }; val scale by animateFloatAsState(if (focused) 1.075f else 1f, label = "card")
    val w = if (landscape) 300.dp else 178.dp; val h = if (landscape) 169.dp else 266.dp
    Column(Modifier.width(w).scale(scale).clip(RoundedCornerShape(12.dp)).background(Surface).border(if (focused) 2.dp else 0.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(12.dp)).onFocusChanged { focused = it.isFocused }.focusable().clickable { open(item) }) {
        AsyncImage(item.backdrop?.takeIf { landscape } ?: item.poster ?: item.backdrop, item.title, Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
        Text(item.title, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(10.dp, 9.dp, 10.dp, 2.dp))
        Text(item.year ?: item.channelGroup ?: "", color = Muted, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

@Composable private fun LibraryPage(title: String, subtitle: String, items: List<MediaItem>, open: (MediaItem) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { Text(title, color = White, fontSize = 36.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(48.dp, 34.dp, 48.dp, 4.dp)); Text(subtitle, color = Muted, modifier = Modifier.padding(horizontal = 48.dp)); Rail("Tümü", "", items.distinctBy { "${it.kind}:${it.id}:${it.addonId}" }, open); Spacer(Modifier.height(50.dp)) }
}

@Composable private fun LivePage(channels: List<MediaItem>, zap: () -> Unit, open: (MediaItem) -> Unit) {
    Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(Modifier.width(330.dp).fillMaxHeight()) {
            Text("CANLI TV", color = White, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(10.dp))
            Text("OK izle  •  CH+/CH− zap", color = Muted, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(8.dp)) {
                items(channels.take(100), key = { "${it.id}:${it.addonId}" }) { ch ->
                    FocusCard(onClick = { open(ch); zap() }) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(ch.poster, ch.title, Modifier.size(74.dp, 42.dp).clip(RoundedCornerShape(7.dp)), contentScale = ContentScale.Crop)
                            Column(Modifier.padding(start = 11.dp)) { Text(ch.title, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(ch.channelGroup ?: "Canlı", color = Muted, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Canal seç", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("Bir kanal seçtiğinde oynatıcı açılır.", color = Muted, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@Composable private fun FocusCard(onClick: () -> Unit, content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }; val scale by animateFloatAsState(if (focused) 1.025f else 1f, label = "focus")
    Row(Modifier.fillMaxWidth().scale(scale).clip(RoundedCornerShape(12.dp)).background(if (focused) Surface2 else Surface).border(if (focused) 2.dp else 0.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(12.dp)).onFocusChanged { focused = it.isFocused }.focusable().clickable { onClick() }) { content() }
}

@Composable private fun Detail(item: MediaItem, streams: List<StreamSource>, play: (StreamSource?) -> Unit, back: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        AsyncImage(item.backdrop ?: item.poster, item.title, Modifier.fillMaxWidth().height(570.dp).alpha(.58f), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Bg.copy(alpha = .45f), Bg))))
        Column(Modifier.align(Alignment.BottomStart).padding(54.dp).width(930.dp)) {
            Text(item.title, color = White, fontSize = 42.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 9.dp)) { item.year?.let { Text(it, color = Muted) }; if (item.rating > 0) Text("★ ${"%.1f".format(item.rating)}", color = Gold); item.runtime?.let { Text("${it} dk", color = Muted) } }
            Text(item.overview.ifBlank { "Açıklama bulunamadı." }, color = Color(0xFFD8D9DE), fontSize = 16.sp, lineHeight = 24.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 22.dp)) {
                Button(onClick = { play(streams.firstOrNull()) }, enabled = streams.isNotEmpty()) { Text(if (streams.isEmpty()) "Kaynak aranıyor…" else "▶  İzle") }
                OutlinedButton(onClick = back) { Text("Geri") }
            }
            if (streams.isNotEmpty()) {
                Text("Kaynaklar", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 7.dp)) { items(streams.take(12), key = { "${it.addonId}:${it.url}" }) { s -> OutlinedButton(onClick = { play(s) }) { Text(s.title.take(26)) } } }
            }
        }
    }
}

@Composable private fun SettingsPage(addons: List<InstalledAddon>, install: (String) -> Unit, remove: (String) -> Unit, close: () -> Unit) {
    var url by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Color(0xFA050609))) { Column(Modifier.fillMaxSize().padding(60.dp).verticalScroll(rememberScrollState())) {
        Text("Kaynaklar / Depolar", color = White, fontSize = 38.sp, fontWeight = FontWeight.Black); Text("Stremio uyumlu manifest tabanlı eklentiler", color = Muted, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(28.dp)); Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Manifest URL") }, modifier = Modifier.width(760.dp), singleLine = true); Spacer(Modifier.width(12.dp)); Button(onClick = { if (url.isNotBlank()) { install(url); url = "" } }) { Text("+ Ekle") } }
        Spacer(Modifier.height(28.dp)); addons.forEach { addon ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp).clip(RoundedCornerShape(14.dp)).background(Surface).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(addon.manifest.logo, addon.manifest.name, Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f).padding(start = 14.dp)) { Text(addon.manifest.name, color = White, fontWeight = FontWeight.Bold); Text("v${addon.manifest.version}  •  ${addon.manifest.description.ifBlank { "Manifest addon" }}", color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                OutlinedButton(onClick = { remove(addon.manifestUrl) }) { Text("Kaldır") }
            }
        }
        Spacer(Modifier.height(24.dp)); OutlinedButton(onClick = close) { Text("Kapat") }
    } }
}

@Composable private fun SearchPage(vm: HobiViewModel, close: () -> Unit, open: (MediaItem) -> Unit) {
    var q by remember { mutableStateOf("") }; val results by vm.search.collectAsState()
    Box(Modifier.fillMaxSize().background(Bg)) { Column(Modifier.fillMaxSize().padding(50.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Ara", color = White, fontSize = 38.sp, fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f)); TextButton(onClick = close) { Text("Kapat", color = White) } }
        Spacer(Modifier.height(18.dp)); OutlinedTextField(value = q, onValueChange = { q = it; vm.search(it) }, label = { Text("Film, dizi veya kanal") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(22.dp)); LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) { items(results, key = { "${it.kind}:${it.id}:${it.addonId}" }) { CardItem(it, open, false) } }
    } }
}

@Composable private fun ToastMessage(message: String) { Box(Modifier.fillMaxSize().padding(bottom = 34.dp), contentAlignment = Alignment.BottomCenter) { Surface(color = Surface2, shape = RoundedCornerShape(12.dp), shadowElevation = 8.dp) { Text(message, color = White, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) } } }
