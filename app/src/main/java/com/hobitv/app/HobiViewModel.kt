package com.hobitv.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HobiViewModel(app: Application) : AndroidViewModel(app) {
    private val addons = AddonRepository(app)
    private val _installed = MutableStateFlow<List<InstalledAddon>>(emptyList()); val installed: StateFlow<List<InstalledAddon>> = _installed.asStateFlow()
    private val _movies = MutableStateFlow<List<MediaItem>>(emptyList()); val movies: StateFlow<List<MediaItem>> = _movies.asStateFlow()
    private val _series = MutableStateFlow<List<MediaItem>>(emptyList()); val series: StateFlow<List<MediaItem>> = _series.asStateFlow()
    private val _live = MutableStateFlow<List<MediaItem>>(emptyList()); val live: StateFlow<List<MediaItem>> = _live.asStateFlow()
    private val _tmdb = MutableStateFlow<List<MediaItem>>(emptyList()); val tmdb: StateFlow<List<MediaItem>> = _tmdb.asStateFlow()
    private val _search = MutableStateFlow<List<MediaItem>>(emptyList()); val search: StateFlow<List<MediaItem>> = _search.asStateFlow()
    private val _streams = MutableStateFlow<List<StreamSource>>(emptyList()); val streams: StateFlow<List<StreamSource>> = _streams.asStateFlow()
    private val _detail = MutableStateFlow<MediaItem?>(null); val detail: StateFlow<MediaItem?> = _detail.asStateFlow()
    private val _loading = MutableStateFlow(false); val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _message = MutableStateFlow<String?>(null); val message: StateFlow<String?> = _message.asStateFlow()
    private val _zap = MutableStateFlow<List<MediaItem>>(emptyList()); val zap: StateFlow<List<MediaItem>> = _zap.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch(Dispatchers.IO) {
        _loading.value = true
        _installed.value = addons.installed()
        val all = _installed.value.flatMap { addon -> addon.manifest.catalogs.map { c -> async { addons.catalog(addon, c) } } }.awaitAll().flatten()
            .distinctBy { "${it.kind}:${it.id}" }
        _movies.value = all.filter { it.kind == MediaKind.MOVIE }
        _series.value = all.filter { it.kind == MediaKind.SERIES }
        _live.value = all.filter { it.kind == MediaKind.LIVE }
        loadTmdb()
        _loading.value = false
    }

    private suspend fun loadTmdb() {
        val token = BuildConfig.TMDB_TOKEN
        if (token.isBlank()) return
        runCatching {
            val root = getJson("https://api.themoviedb.org/3/trending/all/day", token)
            val arr = root.optJSONArray("results") ?: return
            val out = buildList {
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    val movie = m.optString("media_type") == "movie"
                    val title = m.optString(if (movie) "title" else "name").takeIf { it.isNotBlank() } ?: continue
                    add(MediaItem(m.optString("id"), title, if (movie) MediaKind.MOVIE else MediaKind.SERIES,
                        tmdbImage(m.optString("poster_path"), "w500"), tmdbImage(m.optString("backdrop_path"), "w1280"),
                        m.optString("overview"), m.optString(if (movie) "release_date" else "first_air_date").take(4).takeIf { it.isNotBlank() },
                        m.optDouble("vote_average", 0.0)))
                }
            }
            _tmdb.value = out
        }
    }

    fun install(url: String) = viewModelScope.launch {
        _message.value = null
        runCatching { addons.install(url) }.onSuccess { _message.value = "Kaynak eklendi: ${it.manifest.name}"; refresh() }
            .onFailure { _message.value = "Kaynak eklenemedi: ${it.message ?: "manifest okunamadı"}" }
    }
    fun remove(url: String) { addons.remove(url); refresh() }

    fun search(query: String) {
        val q = query.trim().lowercase()
        val base = (_movies.value + _series.value + _live.value + _tmdb.value).distinctBy { "${it.kind}:${it.id}:${it.addonId}" }
        _search.value = if (q.isBlank()) emptyList() else base.filter { it.title.lowercase().contains(q) || it.genres.any { g -> g.lowercase().contains(q) } }.take(80)
    }

    fun openDetail(item: MediaItem) = viewModelScope.launch(Dispatchers.IO) {
        _detail.value = item; _streams.value = emptyList()
        if (item.addonId != null) {
            _installed.value.firstOrNull { it.manifest.id == item.addonId }?.let { addon ->
                addons.meta(addon, when (item.kind) { MediaKind.MOVIE -> "movie"; MediaKind.SERIES -> "series"; MediaKind.LIVE -> "tv"; else -> "episode" }, item.sourceId ?: item.id)?.let { _detail.value = it }
            }
        }
        resolveStreams(item)
    }

    fun clearDetail() { _detail.value = null; _streams.value = emptyList() }

    fun prepareZap(channels: List<MediaItem>) = viewModelScope.launch(Dispatchers.IO) {
        val result = channels.take(40).map { channel ->
            val source = _installed.value.flatMap { addon ->
                runCatching { addons.streams(addon, "tv", channel.sourceId ?: channel.id) }.getOrDefault(emptyList())
            }.firstOrNull()
            source?.let { channel.copy(streamUrl = it.url) }
        }.filterNotNull()
        _zap.value = result
    }

    fun clearZap() { _zap.value = emptyList() }

    fun resolveStreams(item: MediaItem) = viewModelScope.launch(Dispatchers.IO) {
        val type = when (item.kind) { MediaKind.MOVIE -> "movie"; MediaKind.SERIES -> "series"; MediaKind.LIVE -> "tv"; MediaKind.EPISODE -> "series" }
        _streams.value = _installed.value.map { addon -> async { addons.streams(addon, type, item.sourceId ?: item.id) } }.awaitAll().flatten()
    }

    private fun getJson(url: String, token: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        c.setRequestProperty("Authorization", "Bearer $token")
        return try { JSONObject(c.inputStream.bufferedReader().use { it.readText() }) } finally { c.disconnect() }
    }
    private fun tmdbImage(path: String?, size: String): String? = path?.takeIf { it.isNotBlank() && it != "null" }?.let { "https://image.tmdb.org/t/p/$size$it" }
}
