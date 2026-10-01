package com.hobitv.app

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

class ProviderClient(private val config: ProviderConfig) {
    fun resolve(media: Media): Media {
        if (!config.type.equals("STALKER", true) || media.streamUrl.isNullOrBlank()) return media
        val session = StalkerSession(config)
        session.handshake()
        session.authenticate()
        return media.copy(streamUrl = session.resolveStream(media))
    }

    fun load(): ProviderLibrary = when {
        config.type.equals("STALKER", true) -> loadStalker()
        config.type.equals("XTREAM", true) -> loadXtream()
        else -> loadM3u()
    }

    private fun loadM3u(): ProviderLibrary {
        if (config.url.isBlank()) return ProviderLibrary()
        val text = http(config.url).body
        val lines = text.lineSequence().toList()
        val live = mutableListOf<Media>(); val vod = mutableListOf<Media>(); val series = mutableListOf<Media>()
        var info = ""; var attrs = emptyMap<String, String>(); var id = 900000
        for (line in lines) {
            val s = line.trim()
            if (s.startsWith("#EXTINF", true)) {
                info = s.substringAfterLast(',').trim()
                attrs = Regex("([\\w-]+)=\"([^\"]*)\"").findAll(s).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            } else if (s.isNotBlank() && !s.startsWith("#")) {
                val group = attrs["group-title"].orEmpty()
                val type = when {
                    Regex("film|movie|vod", RegexOption.IGNORE_CASE).containsMatchIn(group) -> MediaType.MOVIE
                    Regex("series|dizi|serial", RegexOption.IGNORE_CASE).containsMatchIn(group) -> MediaType.TV
                    else -> MediaType.LIVE
                }
                val m = Media(id++, info.ifBlank { "Hobi TV" }, "", posterPath = attrs["tvg-logo"], mediaType = type, streamUrl = s)
                when (type) { MediaType.MOVIE -> vod += m; MediaType.TV -> series += m; MediaType.LIVE -> live += m }
                info = ""; attrs = emptyMap()
            }
        }
        return ProviderLibrary(live = live, movies = vod, series = series)
    }

    private fun loadXtream(): ProviderLibrary {
        val base = config.url.trimEnd('/')
        val user = URLEncoder.encode(config.username, "UTF-8")
        val pass = URLEncoder.encode(config.password, "UTF-8")
        val root = "$base/player_api.php?username=$user&password=$pass"
        val liveInfo = runCatching { JSONArray(http("$root&action=get_live_streams").body) }.getOrDefault(JSONArray())
        val vodInfo = runCatching { JSONArray(http("$root&action=get_vod_streams").body) }.getOrDefault(JSONArray())
        val seriesInfo = runCatching { JSONArray(http("$root&action=get_series").body) }.getOrDefault(JSONArray())
        val live = (0 until liveInfo.length()).map { i -> val o=liveInfo.getJSONObject(i); Media(o.optInt("stream_id"),o.optString("name"),"",o.optString("stream_icon").takeIf{it.isNotBlank()},mediaType=MediaType.LIVE,streamUrl="$base/live/$user/$pass/${o.optInt("stream_id")}.m3u8") }
        val movies = (0 until vodInfo.length()).map { i -> val o=vodInfo.getJSONObject(i); Media(o.optInt("stream_id"),o.optString("name"),"",o.optString("stream_icon").takeIf{it.isNotBlank()},mediaType=MediaType.MOVIE,streamUrl="$base/movie/$user/$pass/${o.optInt("stream_id")}.${o.optString("container_extension","mp4")}") }
        val series = (0 until seriesInfo.length()).map { i -> val o=seriesInfo.getJSONObject(i); Media(o.optInt("series_id"),o.optString("name"),o.optString("plot"),o.optString("cover").takeIf{it.isNotBlank()},mediaType=MediaType.TV) }
        return ProviderLibrary(live, movies, series)
    }

    private fun loadStalker(): ProviderLibrary {
        require(config.url.isNotBlank()) { "Stalker portal URL boş." }
        require(config.mac.isNotBlank()) { "Stalker MAC adresi boş." }
        val session = StalkerSession(config)
        session.handshake()
        session.authenticate()
        val live = session.list("itv").mapIndexed { i, o -> session.toMedia(o, MediaType.LIVE, i) }
        val vod = session.list("vod").mapIndexed { i, o -> session.toMedia(o, MediaType.MOVIE, 100000 + i) }
        val series = session.list("series").mapIndexed { i, o -> session.toMedia(o, MediaType.TV, 200000 + i) }
        return ProviderLibrary(live = live, movies = vod, series = series, stalker = session)
    }

    private fun http(url:String, headers: Map<String,String> = emptyMap()): HttpResult {
        val c=URL(url).openConnection() as HttpURLConnection
        c.connectTimeout=15000; c.readTimeout=30000; c.requestMethod="GET"; c.setRequestProperty("User-Agent","HobiTV/1.0")
        headers.forEach { (k,v) -> c.setRequestProperty(k,v) }
        return try {
            val code=c.responseCode; val stream=if(code in 200..299)c.inputStream else c.errorStream
            val body=stream?.bufferedReader()?.use{it.readText()}.orEmpty()
            if(code !in 200..299) error("Provider HTTP $code")
            HttpResult(code, body)
        } finally { c.disconnect() }
    }

    private data class HttpResult(val code: Int, val body: String)
}

class StalkerSession(private val config: ProviderConfig) {
    private var token = ""
    private val loadUrl: String
    private val referer: String

    init {
        val raw = config.url.trimEnd('/')
        loadUrl = when {
            raw.endsWith("/server/load.php") -> raw
            raw.contains("/stalker_portal") -> "$raw/server/load.php"
            raw.endsWith("/portal.php") -> raw
            else -> "$raw/stalker_portal/server/load.php"
        }
        referer = loadUrl.substringBefore("/server/load.php").let { "$it/c/" }
    }

    fun handshake() {
        val obj = request(mapOf("type" to "stb", "action" to "handshake", "token" to ""))
        token = obj.optJSONObject("js")?.optString("token").orEmpty()
        require(token.isNotBlank()) { "Stalker handshake başarısız: token alınamadı." }
    }

    fun authenticate() {
        val metrics = JSONObject().apply {
            put("mac", config.mac)
            put("model", config.model.ifBlank { "MAG250" })
            put("type", "STB")
            put("uid", "")
            put("device", config.deviceId)
            put("random", "")
        }
        val params = linkedMapOf(
            "type" to "stb", "action" to "get_profile", "hd" to "1",
            "ver" to "ImageDescription: 0.2.18-r14-pub-250; PORTAL version: 5.5.0; API Version: JS API version: 328; STB API version: 134",
            "num_banks" to "2", "sn" to config.serialNumber.ifBlank { serialFromMac() },
            "stb_type" to config.model.ifBlank { "MAG250" }, "image_version" to "218",
            "video_out" to "hdmi", "device_id" to config.deviceId,
            "device_id2" to config.deviceId2, "signature" to "", "auth_second_step" to "1",
            "hw_version" to "1.7-BD-00", "not_valid_token" to "0", "client_type" to "STB",
            "metrics" to metrics.toString()
        )
        request(params)
        // A second handshake is common after profile registration and refreshes the bearer token.
        val obj = request(mapOf("type" to "stb", "action" to "handshake", "token" to token))
        token = obj.optJSONObject("js")?.optString("token").takeIf { !it.isNullOrBlank() } ?: token
    }

    fun list(type: String): List<JSONObject> {
        val action = when (type) { "itv" -> "get_ordered_list"; "vod" -> "get_ordered_list"; else -> "get_ordered_list" }
        val page = request(mapOf("type" to type, "action" to action, "p" to "0", "JsHttpRequest" to "1-xml"))
        val js = page.optJSONObject("js") ?: return emptyList()
        val data = js.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { data.optJSONObject(it) }
    }

    fun resolveStream(media: Media): String {
        val cmd = media.streamUrl ?: "ffrt http://localhost/ch/${media.id}"
        val obj = request(mapOf("type" to if (media.mediaType == MediaType.LIVE) "itv" else "vod", "action" to "create_link", "cmd" to cmd, "forced_storage" to "undefined", "disable_ad" to "0", "download" to "0", "JsHttpRequest" to "1-xml"))
        val returned = obj.optJSONObject("js")?.optString("cmd").orEmpty()
        val parts = returned.trim().split(" ", limit = 2)
        return (parts.getOrNull(1) ?: parts.firstOrNull()).orEmpty()
    }

    fun toMedia(o: JSONObject, type: MediaType, fallbackId: Int): Media {
        val id = o.optInt("id", o.optInt("stream_id", fallbackId))
        val logo = o.optString("logo").takeIf { it.isNotBlank() } ?: o.optString("cover").takeIf { it.isNotBlank() }
        val cmd = o.optString("cmd").takeIf { it.isNotBlank() } ?: "ffrt http://localhost/ch/$id"
        return Media(id, o.optString("name", o.optString("title", "İçerik")), o.optString("description", o.optString("plot")), logo, mediaType = type, streamUrl = cmd)
    }

    private fun request(params: Map<String,String>): JSONObject {
        val query = params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val url = "$loadUrl?$query"
        val headers = mapOf(
            "Accept" to "*/*",
            "X-User-Agent" to "Model: ${config.model.ifBlank { "MAG250" }}; Link: WiFi",
            "Referer" to referer,
            "Authorization" to "Bearer $token",
            "Cookie" to "mac=${enc(config.mac)}; stb_lang=en; timezone=${enc(config.timezone.ifBlank { "Europe/Istanbul" })}"
        )
        val c=URL(url).openConnection() as HttpURLConnection
        c.connectTimeout=15000; c.readTimeout=30000; headers.forEach { (k,v)->c.setRequestProperty(k,v) }
        return try {
            val code=c.responseCode; val body=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
            if(code !in 200..299) error("Stalker HTTP $code")
            JSONObject(body.trim())
        } finally { c.disconnect() }
    }

    private fun enc(s:String) = URLEncoder.encode(s, "UTF-8")
    private fun serialFromMac() = config.mac.replace(":", "").padEnd(13, '0').take(13)
}

data class ProviderLibrary(
    val live: List<Media> = emptyList(),
    val movies: List<Media> = emptyList(),
    val series: List<Media> = emptyList(),
    val stalker: StalkerSession? = null
)
