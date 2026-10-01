package com.hobitv.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) { super.onCreate(s); setContent { MaterialTheme { HobiTV() } } }
}

@Composable fun HobiTV(vm: MainViewModel = viewModel()) {
    var tab by remember { mutableStateOf("Ana Sayfa") }
    var selected by remember { mutableStateOf<Media?>(null) }
    var playing by remember { mutableStateOf<Media?>(null) }
    var search by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current

    if (playing != null) {
        var pos by remember(playing!!.id) { mutableLongStateOf(0) }
        LaunchedEffect(playing!!.id) { vm.load(playing!!){ pos = it } }
        PlayerScreen(playing!!, pos, { vm.save(playing!!, it) }, { playing = null })
        return
    }
    if (settings) {
        SettingsScreen(vm, { settings = false })
        return
    }
    if (selected != null) {
        var detail by remember(selected!!.id) { mutableStateOf(selected!!) }
        var favorite by remember(selected!!.id) { mutableStateOf(false) }
        LaunchedEffect(selected!!.id) { vm.detail(selected!!){ if (it != null) detail = it }; vm.isFavorite(selected!!){ favorite = it } }
        Detail(detail, favorite, { vm.toggleFavorite(detail); favorite = !favorite }, { selected = null }) { media ->
            if (media.streamUrl.isNullOrBlank()) Toast.makeText(context, "Bu içerik için yayın kaynağı bağlı değil.", Toast.LENGTH_SHORT).show()
            else vm.resolveForPlayback(media) { resolved -> playing = resolved }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF030303))) {
        Column(Modifier.fillMaxSize()) {
            Top(tab, { tab = it }, { search = true }, { settings = true })
            if (vm.loading && vm.trending.isEmpty()) Loading()
            else when (tab) {
                "Ana Sayfa" -> HomeContent(vm) { selected = it }
                "Canlı TV" -> LiveContent(vm) { selected = it }
                "Filmler" -> MoviesContent(vm) { selected = it }
                "Diziler" -> ShowsContent(vm) { selected = it }
                "Favoriler" -> ContentSection("Favoriler", "Kaydettiğin içerikler", vm.favorites, { selected = it })
            }
        }
        if (search) SearchOverlay(query, vm.searchResults, { query = it; vm.search(it) }, { selected = it }, { search = false })
    }
}

@Composable private fun HomeContent(vm: MainViewModel, onOpen: (Media) -> Unit) {
    val hero = vm.trending.firstOrNull() ?: vm.movies.firstOrNull() ?: vm.providerMovies.firstOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (hero != null) Hero(hero, onOpen)
        if (vm.recent.isNotEmpty()) Rail("▶ Devam Et", vm.recent, onOpen)
        if (vm.trending.isNotEmpty()) Rail("🔥 Şimdi Trend", vm.trending, onOpen)
        if (vm.movies.isNotEmpty()) Rail("🎬 Popüler Filmler", vm.movies, onOpen)
        if (vm.shows.isNotEmpty()) Rail("✨ Popüler Diziler", vm.shows, onOpen)
        if (vm.providerMovies.isNotEmpty()) Rail("▶ Yayın Kütüphanesi", vm.providerMovies, onOpen)
        Spacer(Modifier.height(30.dp))
    }
}

@Composable private fun MoviesContent(vm: MainViewModel, open: (Media) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Filmler", "TMDB kataloğu ve bağlı yayın kütüphanesi")
        if (vm.movies.isNotEmpty()) Rail("TMDB Filmleri", vm.movies, open)
        if (vm.providerMovies.isNotEmpty()) Rail("Yayın Filmleri", vm.providerMovies, open)
    }
}

@Composable private fun ShowsContent(vm: MainViewModel, open: (Media) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Diziler", "TMDB kataloğu ve bağlı yayın kütüphanesi")
        if (vm.shows.isNotEmpty()) Rail("TMDB Dizileri", vm.shows, open)
        if (vm.providerShows.isNotEmpty()) Rail("Yayın Dizileri", vm.providerShows, open)
    }
}

@Composable private fun LiveContent(vm: MainViewModel, open: (Media) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Canlı TV", if (vm.providerConfig.url.isBlank()) "Ayarlar'dan yetkili yayın sağlayıcını bağla" else "Bağlı yayın sağlayıcısı")
        if (vm.providerLoading) Loading()
        else if (vm.live.isEmpty()) EmptySection("Kanal bulunamadı", "Ayarlar → Yayın Kaynağı bölümünden M3U veya Xtream bilgilerini ekle.")
        else Rail("Kanallar", vm.live, open)
    }
}

@Composable private fun ContentSection(title: String, subtitle: String, list: List<Media>, open: (Media) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(title, subtitle)
        if (list.isEmpty()) EmptySection("Henüz içerik yok", "İçerikleri eklediğinde burada görünecek.") else Rail("Kütüphane", list, open)
    }
}

@Composable private fun EmptySection(title: String, message: String) {
    Column(Modifier.fillMaxSize().padding(58.dp, 30.dp)) { Text(title, color = Color.White, style = MaterialTheme.typography.displaySmall); Spacer(Modifier.height(12.dp)); Text(message, color = Color(0xFF888888)) }
}

@Composable private fun Loading() { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("HOBİ TV yükleniyor…", color = Color.White, style = MaterialTheme.typography.headlineSmall) } }

@Composable fun Top(tab: String, onTab: (String) -> Unit, onSearch: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 58.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("HOBİ TV", color = Color(0xFFE50914), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.width(45.dp))
        listOf("Ana Sayfa", "Canlı TV", "Filmler", "Diziler", "Favoriler").forEach { Nav(it, tab == it) { onTab(it) }; Spacer(Modifier.width(5.dp)) }
        Spacer(Modifier.weight(1f)); Nav("⌕ Ara", false, onSearch); Spacer(Modifier.width(8.dp)); Nav("⚙", false, onSettings)
    }
}

@Composable fun Nav(t: String, sel: Boolean, click: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Text(t, color = if (sel || f) Color.White else Color(0xFF858585), modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (sel) Color(0xFF242424) else Color.Transparent).border(if (f) 2.dp else 0.dp, Color.White, RoundedCornerShape(8.dp)).focusable().onFocusChanged { f = it.isFocused }.clickable { click() }.padding(horizontal = 12.dp, vertical = 8.dp))
}

@Composable fun Hero(m: Media, onOpen: (Media) -> Unit) {
    Box(Modifier.fillMaxWidth().height(380.dp)) {
        RemoteImage(m.backdropPath, "w1280", Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF030303), Color.Transparent, Color(0xDD030303)))))
        Column(Modifier.align(Alignment.CenterStart).padding(start = 62.dp).width(680.dp)) {
            Text("HOBİ TV • ÖNE ÇIKAN", color = Color(0xFFC5C5C5)); Spacer(Modifier.height(8.dp)); Text(m.title, color = Color.White, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(10.dp)); Text(m.overview.ifBlank { "İçerik bilgisi hazırlanıyor." }, color = Color(0xFFD0D0D0), maxLines = 3)
            Spacer(Modifier.height(22.dp)); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Button({ onOpen(m) }) { Text("▶ Detay") }; OutlinedButton({ onOpen(m) }) { Text("ⓘ Bilgi") } }
        }
    }
}

@Composable fun Header(t: String, s: String) { Column(Modifier.padding(58.dp, 28.dp)) { Text(t, color = Color.White, style = MaterialTheme.typography.displaySmall); Text(s, color = Color(0xFF777777)) } }

@Composable fun Rail(t: String, list: List<Media>, open: (Media) -> Unit) {
    Column(Modifier.padding(start = 58.dp, top = 17.dp)) { Text(t, color = Color.White, style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(11.dp)); Row(Modifier.horizontalScroll(rememberScrollState()).padding(end = 58.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { list.forEach { Card(it, open) } } }
}

@Composable fun Card(m: Media, open: (Media) -> Unit) {
    var f by remember { mutableStateOf(false) }
    Column(Modifier.width(208.dp).focusable().onFocusChanged { f = it.isFocused }.clickable { open(m) }) {
        Box(Modifier.width(208.dp).height(300.dp).clip(RoundedCornerShape(12.dp)).border(if (f) 3.dp else 0.dp, Color.White, RoundedCornerShape(12.dp))) {
            RemoteImage(m.posterPath, "w500", Modifier.fillMaxSize())
            if (m.posterPath == null) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF4A4A4A), Color(0xFF121212))))) { Text(m.title, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(12.dp)) }
        }
        Spacer(Modifier.height(7.dp)); Text(m.title, color = Color.White, maxLines = 1); Row { Text(m.releaseDate?.take(4) ?: "", color = Color(0xFF777777)); Spacer(Modifier.width(8.dp)); if (m.rating > 0) Text("★ %.1f".format(m.rating), color = Color(0xFFAAAAAA)) }
    }
}

@Composable fun Detail(m: Media, favorite: Boolean, onFavorite: () -> Unit, onBack: () -> Unit, onPlay: (Media) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(Color(0xFF050505)).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(300.dp)) { RemoteImage(m.backdropPath, "w1280", Modifier.fillMaxSize()); Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF050505))))) }
        Row(Modifier.padding(60.dp, 0.dp)) {
            Box(Modifier.width(250.dp).height(350.dp).clip(RoundedCornerShape(15.dp))) { RemoteImage(m.posterPath, "w500", Modifier.fillMaxSize()) }
            Spacer(Modifier.width(44.dp)); Column(Modifier.width(850.dp)) {
                Text(m.title, color = Color.White, style = MaterialTheme.typography.displaySmall); Spacer(Modifier.height(10.dp))
                Text(listOfNotNull(m.releaseDate?.take(4), m.genres.takeIf { it.isNotEmpty() }?.joinToString(" • "), if (m.rating > 0) "★ %.1f".format(m.rating) else null, m.runtime?.let { "$it dk" }).joinToString("   "), color = Color(0xFFAAAAAA))
                Spacer(Modifier.height(18.dp)); Text(m.overview.ifBlank { "Açıklama bulunamadı." }, color = Color(0xFFD0D0D0), maxLines = 7); Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button({ onPlay(m) }) { Text("▶ Oynat") }
                    if (m.trailerKey != null) OutlinedButton({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${m.trailerKey}"))) }) { Text("▷ Fragman") }
                    OutlinedButton(onFavorite) { Text(if (favorite) "✓ Listemde" else "＋ Listeme Ekle") }
                }
                Spacer(Modifier.height(20.dp)); Nav("‹ Geri", false, onBack)
            }
        }
        Spacer(Modifier.height(50.dp))
    }
}

@Composable fun SearchOverlay(q: String, results: List<Media>, onQuery: (String) -> Unit, open: (Media) -> Unit, close: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xF5101010))) { Column(Modifier.padding(60.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("ARA", color = Color.White, style = MaterialTheme.typography.displaySmall); Spacer(Modifier.width(25.dp)); BasicTextField(q, onValueChange = onQuery, modifier = Modifier.width(600.dp).background(Color(0xFF202020), RoundedCornerShape(8.dp)).padding(16.dp), textStyle = TextStyle(color = Color.White)); Spacer(Modifier.weight(1f)); Nav("Kapat", false, close) }
        Spacer(Modifier.height(30.dp)); if (q.isNotBlank()) Rail(if (results.isEmpty()) "Sonuç bulunamadı" else "Sonuçlar", results, open) else Text("Film, dizi veya oyuncu adı yaz.", color = Color(0xFF888888))
    } }
}

@Composable private fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    var type by remember { mutableStateOf(vm.providerConfig.type) }
    var url by remember { mutableStateOf(vm.providerConfig.url) }
    var user by remember { mutableStateOf(vm.providerConfig.username) }
    var pass by remember { mutableStateOf(vm.providerConfig.password) }
    var mac by remember { mutableStateOf(vm.providerConfig.mac) }
    var serial by remember { mutableStateOf(vm.providerConfig.serialNumber) }
    var deviceId by remember { mutableStateOf(vm.providerConfig.deviceId) }
    var deviceId2 by remember { mutableStateOf(vm.providerConfig.deviceId2) }
    var model by remember { mutableStateOf(vm.providerConfig.model.ifBlank { "MAG250" }) }
    var timezone by remember { mutableStateOf(vm.providerConfig.timezone.ifBlank { "Europe/Istanbul" }) }
    Column(Modifier.fillMaxSize().background(Color(0xFF050505)).verticalScroll(rememberScrollState()).padding(60.dp)) {
        Text("Ayarlar", color = Color.White, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp)); Text("HOBİ TV yayın kaynağını bağla.", color = Color(0xFF888888)); Spacer(Modifier.height(30.dp))
        Text("Yayın Kaynağı", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp)); Text("Stalker / Ministra için portal adresi ve sağlayıcının verdiği MAC bilgisi yeterlidir. Ek cihaz kimliklerini yalnızca sağlayıcı verdiyse doldur.", color = Color(0xFF888888), modifier = Modifier.width(900.dp)); Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Nav("Stalker", type == "STALKER") { type = "STALKER" }
            Nav("M3U", type == "M3U") { type = "M3U" }
            Nav("Xtream", type == "XTREAM") { type = "XTREAM" }
        }
        Spacer(Modifier.height(20.dp)); Field("Portal / Sunucu URL", url) { url = it }
        when (type) {
            "STALKER" -> {
                Spacer(Modifier.height(12.dp)); Field("MAC adresi", mac) { mac = it }
                Spacer(Modifier.height(12.dp)); Field("Serial Number (opsiyonel)", serial) { serial = it }
                Spacer(Modifier.height(12.dp)); Field("Device ID (opsiyonel)", deviceId) { deviceId = it }
                Spacer(Modifier.height(12.dp)); Field("Device ID 2 (opsiyonel)", deviceId2) { deviceId2 = it }
                Spacer(Modifier.height(12.dp)); Field("STB Model", model) { model = it }
                Spacer(Modifier.height(12.dp)); Field("Timezone", timezone) { timezone = it }
            }
            "XTREAM" -> { Spacer(Modifier.height(12.dp)); Field("Kullanıcı adı", user) { user = it }; Spacer(Modifier.height(12.dp)); Field("Parola", pass) { pass = it } }
        }
        Spacer(Modifier.height(25.dp)); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button({ vm.saveProvider(type, url, user, pass, mac, serial, deviceId, deviceId2, model, timezone); onBack() }) { Text("Kaydet ve Bağlan") }
            OutlinedButton(onBack) { Text("Geri") }
        }
        Spacer(Modifier.height(25.dp)); if (vm.providerError != null) Text(vm.providerError!!, color = Color(0xFFFF7777))
        if (vm.providerLoading) Text("Portal içeriği yükleniyor…", color = Color(0xFFBBBBBB))
        Text("Yalnızca erişim hakkına sahip olduğun Stalker/Ministra portalını kullan.", color = Color(0xFF666666))
        Spacer(Modifier.height(40.dp)); Text("TMDB", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Text("Film/dizi metadata, poster, backdrop ve fragman verileri TMDB üzerinden alınır. TMDB kullanım şartları ve attribution gerekliliklerini dağıtım modeline göre kontrol et.", color = Color(0xFF777777), modifier = Modifier.width(900.dp))
    }
}

@Composable private fun Field(label: String, value: String, onChange: (String) -> Unit) { Column { Text(label, color = Color(0xFFAAAAAA)); Spacer(Modifier.height(6.dp)); BasicTextField(value, onValueChange = onChange, modifier = Modifier.width(850.dp).background(Color(0xFF1D1D1D), RoundedCornerShape(8.dp)).padding(16.dp), textStyle = TextStyle(color = Color.White)) } }

@Composable private fun RemoteImage(path: String?, size: String, modifier: Modifier) {
    val url = when { path.isNullOrBlank() -> null; path.startsWith("http://") || path.startsWith("https://") -> path; else -> "https://image.tmdb.org/t/p/$size$path" }
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = url) { if (url != null) value = withContext(Dispatchers.IO) { runCatching { URL(url).openStream().use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull() } }
    if (bitmap != null) androidx.compose.foundation.Image(bitmap!!.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop) else Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF3D3D3D), Color(0xFF111111)))))
}
