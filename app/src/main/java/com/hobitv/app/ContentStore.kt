package com.hobitv.app

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.contentStore by preferencesDataStore("hobi_content")

class ContentStore(private val context: Context) {
    private val favoritesKey = stringPreferencesKey("favorites")
    private val recentKey = stringPreferencesKey("recent")
    private val providerUrlKey = stringPreferencesKey("provider_url")
    private val providerTypeKey = stringPreferencesKey("provider_type")
    private val usernameKey = stringPreferencesKey("provider_user")
    private val passwordKey = stringPreferencesKey("provider_pass")
    private val macKey = stringPreferencesKey("provider_mac")
    private val serialKey = stringPreferencesKey("provider_serial")
    private val deviceIdKey = stringPreferencesKey("provider_device_id")
    private val deviceId2Key = stringPreferencesKey("provider_device_id2")
    private val modelKey = stringPreferencesKey("provider_model")
    private val timezoneKey = stringPreferencesKey("provider_timezone")

    suspend fun favorites(): List<Media> = readMediaList(favoritesKey)
    suspend fun recents(): List<Media> = readMediaList(recentKey)

    suspend fun toggleFavorite(media: Media) {
        val current = favorites().toMutableList()
        val index = current.indexOfFirst { it.id == media.id && it.mediaType == media.mediaType }
        if (index >= 0) current.removeAt(index) else current.add(0, media)
        writeMediaList(favoritesKey, current.take(100))
    }

    suspend fun addRecent(media: Media) {
        val current = recents().filterNot { it.id == media.id && it.mediaType == media.mediaType }.toMutableList()
        current.add(0, media)
        writeMediaList(recentKey, current.take(20))
    }

    suspend fun isFavorite(media: Media): Boolean = favorites().any { it.id == media.id && it.mediaType == media.mediaType }

    suspend fun saveProvider(type: String, url: String, username: String, password: String, mac: String = "", serial: String = "", deviceId: String = "", deviceId2: String = "", model: String = "MAG250", timezone: String = "Europe/Istanbul") {
        context.contentStore.edit {
            it[providerTypeKey] = type
            it[providerUrlKey] = url
            it[usernameKey] = username
            it[passwordKey] = password
            it[macKey] = mac
            it[serialKey] = serial
            it[deviceIdKey] = deviceId
            it[deviceId2Key] = deviceId2
            it[modelKey] = model
            it[timezoneKey] = timezone
        }
    }

    suspend fun provider(): ProviderConfig = context.contentStore.data.first().let {
        ProviderConfig(
            type = it[providerTypeKey] ?: "M3U",
            url = it[providerUrlKey] ?: "",
            username = it[usernameKey] ?: "",
            password = it[passwordKey] ?: "",
            mac = it[macKey] ?: "",
            serialNumber = it[serialKey] ?: "",
            deviceId = it[deviceIdKey] ?: "",
            deviceId2 = it[deviceId2Key] ?: "",
            model = it[modelKey] ?: "MAG250",
            timezone = it[timezoneKey] ?: "Europe/Istanbul"
        )
    }

    private suspend fun readMediaList(key: androidx.datastore.preferences.core.Preferences.Key<String>): List<Media> {
        val raw = context.contentStore.data.first()[key] ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { parseMedia(array.optJSONObject(it)) }
    }

    private suspend fun writeMediaList(key: androidx.datastore.preferences.core.Preferences.Key<String>, list: List<Media>) {
        val array = JSONArray()
        list.forEach { array.put(serializeMedia(it)) }
        context.contentStore.edit { it[key] = array.toString() }
    }

    private fun serializeMedia(m: Media) = JSONObject().apply {
        put("id", m.id); put("title", m.title); put("overview", m.overview)
        put("poster", m.posterPath ?: JSONObject.NULL); put("backdrop", m.backdropPath ?: JSONObject.NULL)
        put("date", m.releaseDate ?: JSONObject.NULL); put("rating", m.rating); put("type", m.mediaType.name)
        put("runtime", m.runtime ?: JSONObject.NULL); put("trailer", m.trailerKey ?: JSONObject.NULL)
        put("stream", m.streamUrl ?: JSONObject.NULL); put("season", m.season ?: JSONObject.NULL); put("episode", m.episode ?: JSONObject.NULL)
        put("genres", JSONArray(m.genres)); put("status", m.status ?: JSONObject.NULL)
    }

    private fun parseMedia(o: JSONObject?): Media? {
        if (o == null) return null
        return runCatching {
            Media(
                id = o.optInt("id"), title = o.optString("title"), overview = o.optString("overview"),
                posterPath = o.optString("poster").takeIf { it.isNotBlank() && it != "null" },
                backdropPath = o.optString("backdrop").takeIf { it.isNotBlank() && it != "null" },
                releaseDate = o.optString("date").takeIf { it.isNotBlank() && it != "null" },
                rating = o.optDouble("rating", 0.0), mediaType = MediaType.valueOf(o.optString("type", "MOVIE")),
                runtime = o.optInt("runtime", 0).takeIf { it > 0 }, trailerKey = o.optString("trailer").takeIf { it.isNotBlank() && it != "null" },
                streamUrl = o.optString("stream").takeIf { it.isNotBlank() && it != "null" },
                season = o.optInt("season", 0).takeIf { it > 0 }, episode = o.optInt("episode", 0).takeIf { it > 0 },
                genres = o.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
                status = o.optString("status").takeIf { it.isNotBlank() && it != "null" }
            )
        }.getOrNull()
    }
}

data class ProviderConfig(
    val type: String,
    val url: String,
    val username: String,
    val password: String,
    val mac: String = "",
    val serialNumber: String = "",
    val deviceId: String = "",
    val deviceId2: String = "",
    val model: String = "MAG250",
    val timezone: String = "Europe/Istanbul"
)
