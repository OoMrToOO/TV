package com.hobitv.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class AddonRepository(context: Context) {
    private val prefs = context.getSharedPreferences("hobi_addons", Context.MODE_PRIVATE)

    suspend fun installed(): List<InstalledAddon> = withContext(Dispatchers.IO) {
        prefs.getStringSet("urls", emptySet()).orEmpty().mapNotNull { url ->
            runCatching { loadManifest(url) }.getOrNull()?.let { InstalledAddon(url, it) }
        }.sortedBy { it.manifest.name.lowercase() }
    }

    suspend fun install(url: String): InstalledAddon = withContext(Dispatchers.IO) {
        val clean = url.trim().removeSuffix("/")
        require(clean.startsWith("http://") || clean.startsWith("https://")) { "Geçerli bir http/https manifest URL'si girin." }
        val manifest = loadManifest(clean)
        val urls = prefs.getStringSet("urls", emptySet()).orEmpty().toMutableSet().apply { add(clean) }
        prefs.edit().putStringSet("urls", urls).apply()
        InstalledAddon(clean, manifest)
    }

    fun remove(url: String) {
        prefs.getStringSet("urls", emptySet()).orEmpty().toMutableSet().apply { remove(url) }
            .also { prefs.edit().putStringSet("urls", it).apply() }
    }

    private fun loadManifest(url: String): AddonManifest {
        val root = JSONObject(http(url))
        val resources = mutableSetOf<String>()
        root.optJSONArray("resources")?.let { arr ->
            for (i in 0 until arr.length()) {
                when (val v = arr.opt(i)) {
                    is String -> resources += v
                    is JSONObject -> resources += v.optString("name")
                }
            }
        }
        val types = mutableSetOf<String>()
        root.optJSONArray("types")?.let { arr -> for (i in 0 until arr.length()) types += arr.optString(i) }
        val catalogs = mutableListOf<AddonCatalog>()
        root.optJSONArray("catalogs")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val extra = mutableListOf<String>()
                c.optJSONArray("extraSupported")?.let { e -> for (j in 0 until e.length()) extra += e.optString(j) }
                catalogs += AddonCatalog(c.optString("type"), c.optString("id"), c.optString("name", c.optString("id")), extra)
            }
        }
        return AddonManifest(
            id = root.optString("id", url), name = root.optString("name", "Hobi Addon"),
            version = root.optString("version", "1.0.0"), logo = root.optString("logo").takeIf { it.isNotBlank() },
            description = root.optString("description"), resources = resources, types = types,
            catalogs = catalogs, baseUrl = url.substringBeforeLast("/")
        )
    }

    suspend fun catalog(addon: InstalledAddon, catalog: AddonCatalog): List<MediaItem> = withContext(Dispatchers.IO) {
        val url = join(addon.manifest.baseUrl, "catalog/${enc(catalog.type)}/${enc(catalog.id)}.json")
        val root = runCatching { JSONObject(http(url)) }.getOrNull() ?: return@withContext emptyList()
        parseMetas(root.optJSONArray("metas"), addon.manifest)
    }

    suspend fun meta(addon: InstalledAddon, type: String, id: String): MediaItem? = withContext(Dispatchers.IO) {
        val url = join(addon.manifest.baseUrl, "meta/${enc(type)}/${enc(id)}.json")
        val root = runCatching { JSONObject(http(url)) }.getOrNull() ?: return@withContext null
        parseMeta(root.optJSONObject("meta") ?: root, addon.manifest)
    }

    suspend fun streams(addon: InstalledAddon, type: String, id: String): List<StreamSource> = withContext(Dispatchers.IO) {
        val url = join(addon.manifest.baseUrl, "stream/${enc(type)}/${enc(id)}.json")
        val root = runCatching { JSONObject(http(url)) }.getOrNull() ?: return@withContext emptyList()
        val arr = root.optJSONArray("streams") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val stream = s.optString("url").takeIf { it.isNotBlank() } ?: continue
                val hints = mutableMapOf<String, String>()
                s.optJSONObject("behaviorHints")?.keys()?.forEach { k -> hints[k] = s.optString(k) }
                add(StreamSource(addon.manifest.id, addon.manifest.name,
                    s.optString("name", s.optString("title", addon.manifest.name)), stream,
                    s.optString("quality").takeIf { it.isNotBlank() }, hints))
            }
        }
    }

    private fun parseMetas(arr: JSONArray?, manifest: AddonManifest): List<MediaItem> = buildList {
        if (arr == null) return@buildList
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            parseMeta(m, manifest)?.let { add(it) }
        }
    }

    private fun parseMeta(m: JSONObject, manifest: AddonManifest): MediaItem? {
        val id = m.optString("id").takeIf { it.isNotBlank() } ?: return null
        val kind = when (m.optString("type")) {
            "movie" -> MediaKind.MOVIE
            "series" -> MediaKind.SERIES
            "tv", "channel", "live" -> MediaKind.LIVE
            "episode" -> MediaKind.EPISODE
            else -> return null
        }
        val genres = mutableListOf<String>()
        when (val g = m.opt("genres")) {
            is JSONArray -> for (i in 0 until g.length()) genres += g.optString(i)
            is String -> genres += g.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
        val videos = mutableListOf<MediaItem>()
        m.optJSONArray("videos")?.let { arr ->
            for (i in 0 until arr.length()) {
                val v = arr.optJSONObject(i) ?: continue
                videos += MediaItem(
                    id = v.optString("id"), title = v.optString("title", "Bölüm"), kind = MediaKind.EPISODE,
                    poster = v.optString("thumbnail").takeIf { it.isNotBlank() } ?: m.optString("poster").takeIf { it.isNotBlank() },
                    backdrop = v.optString("thumbnail").takeIf { it.isNotBlank() }, overview = v.optString("overview"),
                    season = v.optInt("season", 0).takeIf { it > 0 }, episode = v.optInt("episode", 0).takeIf { it > 0 },
                    addonId = manifest.id, sourceId = v.optString("id")
                )
            }
        }
        return MediaItem(
            id = id, title = m.optString("name", "İçerik"), kind = kind,
            poster = m.optString("poster").takeIf { it.isNotBlank() },
            backdrop = m.optString("background").takeIf { it.isNotBlank() } ?: m.optString("poster").takeIf { it.isNotBlank() },
            overview = m.optString("description"), year = m.optString("releaseInfo").takeIf { it.isNotBlank() },
            rating = m.optDouble("imdbRating", 0.0), runtime = m.optInt("runtime", 0).takeIf { it > 0 },
            addonId = manifest.id, sourceId = id, channelGroup = m.optString("genres").takeIf { it.isNotBlank() },
            genres = genres, episodeCount = m.optInt("episodeCount", 0).takeIf { it > 0 }, videos = videos
        )
    }

    private fun http(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000; c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "HobiTV/2.0 AndroidTV")
        return try {
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code")
            body
        } finally { c.disconnect() }
    }

    private fun join(base: String, path: String) = base.trimEnd('/') + "/" + path.trimStart('/')
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
