package com.hobitv.app

enum class MediaKind { MOVIE, SERIES, LIVE, EPISODE }

data class MediaItem(
    val id: String,
    val title: String,
    val kind: MediaKind,
    val poster: String? = null,
    val backdrop: String? = null,
    val overview: String = "",
    val year: String? = null,
    val rating: Double = 0.0,
    val runtime: Int? = null,
    val addonId: String? = null,
    val sourceId: String? = null,
    val channelGroup: String? = null,
    val streamUrl: String? = null,
    val genres: List<String> = emptyList(),
    val season: Int? = null,
    val episode: Int? = null,
    val episodeCount: Int? = null,
    val videos: List<MediaItem> = emptyList()
)

data class AddonCatalog(
    val type: String,
    val id: String,
    val name: String,
    val extra: List<String> = emptyList()
)

data class AddonManifest(
    val id: String,
    val name: String,
    val version: String,
    val logo: String? = null,
    val description: String = "",
    val resources: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val catalogs: List<AddonCatalog> = emptyList(),
    val baseUrl: String
)

data class StreamSource(
    val addonId: String,
    val addonName: String,
    val title: String,
    val url: String,
    val quality: String? = null,
    val behaviorHints: Map<String, String> = emptyMap()
)

data class InstalledAddon(val manifestUrl: String, val manifest: AddonManifest)

data class WatchEntry(val media: MediaItem, val positionMs: Long, val durationMs: Long)
