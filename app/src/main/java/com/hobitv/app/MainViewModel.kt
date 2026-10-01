package com.hobitv.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application): AndroidViewModel(app) {
    private val progress = ProgressStore(app)
    private val store = ContentStore(app)
    val client = BuildConfig.TMDB_TOKEN.takeIf { it.isNotBlank() }?.let(::TmdbClient)

    var trending by mutableStateOf<List<Media>>(emptyList()); private set
    var movies by mutableStateOf<List<Media>>(emptyList()); private set
    var shows by mutableStateOf<List<Media>>(emptyList()); private set
    var live by mutableStateOf<List<Media>>(emptyList()); private set
    var providerMovies by mutableStateOf<List<Media>>(emptyList()); private set
    var providerShows by mutableStateOf<List<Media>>(emptyList()); private set
    var favorites by mutableStateOf<List<Media>>(emptyList()); private set
    var recent by mutableStateOf<List<Media>>(emptyList()); private set
    var searchResults by mutableStateOf<List<Media>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var providerLoading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var providerError by mutableStateOf<String?>(null); private set
    var providerConfig by mutableStateOf(ProviderConfig("M3U", "", "", "")); private set

    init { refreshAll() }

    fun refreshAll() {
        refreshHome(); refreshLocal(); loadProvider()
    }

    fun refreshHome() {
        val c = client ?: return
        viewModelScope.launch {
            loading = true; error = null
            try {
                val result = withContext(Dispatchers.IO) { Triple(c.trending(), c.popularMovies(), c.popularTv()) }
                trending = result.first; movies = result.second; shows = result.third
            } catch (e: Exception) { error = e.message ?: "TMDB bağlantı hatası" }
            loading = false
        }
    }

    fun search(q: String) {
        val c = client ?: return
        viewModelScope.launch {
            try { searchResults = withContext(Dispatchers.IO) { c.search(q) } }
            catch (e: Exception) { error = e.message ?: "Arama başarısız" }
        }
    }

    fun detail(m: Media, onResult: (Media?) -> Unit) {
        val c = client ?: run { onResult(m); return }
        viewModelScope.launch {
            try { onResult(withContext(Dispatchers.IO) { if (m.mediaType == MediaType.MOVIE) c.movie(m.id) else c.tv(m.id) }) }
            catch (_: Exception) { onResult(m) }
        }
    }

    fun toggleFavorite(media: Media) = viewModelScope.launch { store.toggleFavorite(media); refreshLocal() }
    fun isFavorite(media: Media, done: (Boolean) -> Unit) = viewModelScope.launch { done(store.isFavorite(media)) }

    fun resolveForPlayback(media: Media, done: (Media?) -> Unit) {
        viewModelScope.launch {
            try {
                val cfg = providerConfig
                val resolved = withContext(Dispatchers.IO) { ProviderClient(cfg).resolve(media) }
                done(resolved)
            } catch (e: Exception) {
                providerError = e.message ?: "Yayın bağlantısı çözülemedi"
                done(null)
            }
        }
    }

    fun save(media: Media, pos: Long) = viewModelScope.launch {
        progress.save(media.id, pos); store.addRecent(media); refreshLocal()
    }
    fun load(media: Media, done: (Long) -> Unit) = viewModelScope.launch { done(progress.get(media.id)) }

    fun loadProvider() {
        viewModelScope.launch {
            providerConfig = store.provider()
            if (providerConfig.url.isBlank()) return@launch
            providerLoading = true; providerError = null
            try {
                val lib = withContext(Dispatchers.IO) { ProviderClient(providerConfig).load() }
                live = lib.live; providerMovies = lib.movies; providerShows = lib.series
            } catch (e: Exception) { providerError = e.message ?: "Yayın sağlayıcısı yüklenemedi" }
            providerLoading = false
        }
    }

    fun saveProvider(type: String, url: String, user: String, pass: String, mac: String = "", serial: String = "", deviceId: String = "", deviceId2: String = "", model: String = "MAG250", timezone: String = "Europe/Istanbul") {
        viewModelScope.launch {
            store.saveProvider(type, url.trim(), user.trim(), pass, mac.trim(), serial.trim(), deviceId.trim(), deviceId2.trim(), model.trim(), timezone.trim())
            providerConfig = ProviderConfig(type, url.trim(), user.trim(), pass, mac.trim(), serial.trim(), deviceId.trim(), deviceId2.trim(), model.trim(), timezone.trim())
            loadProvider()
        }
    }

    private fun refreshLocal() {
        viewModelScope.launch {
            favorites = store.favorites(); recent = store.recents()
        }
    }
}
