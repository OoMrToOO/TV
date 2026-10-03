package com.hobitv.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class WatchStore(context: Context) {
    private val prefs = context.getSharedPreferences("hobi_watch", Context.MODE_PRIVATE)

    fun save(item: MediaItem, positionMs: Long, durationMs: Long) {
        val all = JSONArray(prefs.getString("items", "[]") ?: "[]")
        val out = JSONArray()
        for (i in 0 until all.length()) if (all.optJSONObject(i)?.optString("key") != key(item)) out.put(all.getJSONObject(i))
        out.put(JSONObject().apply {
            put("key", key(item)); put("id", item.id); put("title", item.title); put("kind", item.kind.name)
            put("poster", item.poster ?: JSONObject.NULL); put("position", positionMs); put("duration", durationMs)
            put("updated", System.currentTimeMillis())
        })
        prefs.edit().putString("items", out.toString()).apply()
    }

    fun position(item: MediaItem): Long {
        val all = JSONArray(prefs.getString("items", "[]") ?: "[]")
        for (i in 0 until all.length()) {
            val o = all.optJSONObject(i) ?: continue
            if (o.optString("key") == key(item)) return o.optLong("position")
        }
        return 0L
    }

    fun list(): List<WatchEntry> {
        val all = JSONArray(prefs.getString("items", "[]") ?: "[]")
        return buildList {
            for (i in 0 until all.length()) {
                val o = all.optJSONObject(i) ?: continue
                val kind = runCatching { MediaKind.valueOf(o.optString("kind")) }.getOrNull() ?: continue
                add(WatchEntry(MediaItem(o.optString("id"), o.optString("title"), kind, o.optString("poster").takeIf { it.isNotBlank() && it != "null" }), o.optLong("position"), o.optLong("duration")))
            }
        }.sortedByDescending { it.positionMs }.take(20)
    }

    private fun key(item: MediaItem) = "${item.kind}:${item.id}:${item.addonId.orEmpty()}"
}
