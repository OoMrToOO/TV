package com.hobitv.app

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import org.json.JSONObject

class TmdbClient(private val token:String) {
    private val base="https://api.themoviedb.org/3"

    private fun get(path:String):JSONObject {
        val c=URL(base+path).openConnection() as HttpURLConnection
        c.requestMethod="GET"
        c.setRequestProperty("Authorization","Bearer $token")
        c.setRequestProperty("accept","application/json")
        c.connectTimeout=15000
        c.readTimeout=20000
        return try {
            val code=c.responseCode
            val stream=if(code in 200..299) c.inputStream else c.errorStream
            val body=stream?.bufferedReader()?.use{it.readText()}.orEmpty()
            if(code !in 200..299) throw IllegalStateException("TMDB HTTP $code: $body")
            JSONObject(body)
        } finally { c.disconnect() }
    }

    fun popularMovies(page:Int=1):List<Media> = list(get("/movie/popular?language=tr-TR&page=$page"), MediaType.MOVIE)
    fun popularTv(page:Int=1):List<Media> = list(get("/tv/popular?language=tr-TR&page=$page"), MediaType.TV)
    fun trending():List<Media> = list(get("/trending/all/day?language=tr-TR"), null)
    fun search(query:String):List<Media> {
        if(query.isBlank()) return emptyList()
        val q=URLEncoder.encode(query,"UTF-8")
        val obj=get("/search/multi?language=tr-TR&query=$q&page=1&include_adult=false")
        val a=obj.optJSONArray("results") ?: return emptyList()
        return (0 until a.length()).mapNotNull {
            val x=a.optJSONObject(it) ?: return@mapNotNull null
            when(x.optString("media_type")) {
                "movie" -> media(x,MediaType.MOVIE)
                "tv" -> media(x,MediaType.TV)
                else -> null
            }
        }
    }

    fun movie(id:Int):Media = details(get("/movie/$id?language=tr-TR&append_to_response=videos,images"),MediaType.MOVIE)
    fun tv(id:Int):Media = details(get("/tv/$id?language=tr-TR&append_to_response=videos,images"),MediaType.TV)

    private fun list(o:JSONObject,type:MediaType?):List<Media> {
        val a=o.optJSONArray("results") ?: return emptyList()
        return (0 until a.length()).mapNotNull {
            val x=a.optJSONObject(it) ?: return@mapNotNull null
            val t=type ?: when(x.optString("media_type")) { "movie"->MediaType.MOVIE; "tv"->MediaType.TV; else->return@mapNotNull null }
            media(x,t)
        }
    }

    private fun media(o:JSONObject,type:MediaType)=Media(
        id=o.optInt("id"),
        title=o.optString("title",o.optString("name")),
        overview=o.optString("overview"),
        posterPath=o.optString("poster_path").takeIf{it.isNotBlank() && it!="null"},
        backdropPath=o.optString("backdrop_path").takeIf{it.isNotBlank() && it!="null"},
        releaseDate=o.optString("release_date",o.optString("first_air_date")).takeIf{it.isNotBlank()},
        rating=o.optDouble("vote_average",0.0),
        mediaType=type
    )

    private fun details(o:JSONObject,type:MediaType):Media {
        val base=media(o,type)
        val videos=o.optJSONObject("videos")?.optJSONArray("results")
        var trailer:String?=null
        if(videos!=null) for(i in 0 until videos.length()) {
            val v=videos.optJSONObject(i) ?: continue
            if(v.optString("site")=="YouTube" && (v.optString("type")=="Trailer" || v.optString("type")=="Teaser")) {
                trailer=v.optString("key"); break
            }
        }
        val genres=o.optJSONArray("genres")?.let { a -> (0 until a.length()).mapNotNull{a.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank)} }.orEmpty()
        val runtime=when(type){MediaType.MOVIE->o.optInt("runtime",0);MediaType.TV->o.optInt("episode_run_time").takeIf{it>0} ?: 0;else->0}
        return base.copy(
            trailerKey=trailer,
            runtime=runtime.takeIf{it>0},
            genres=genres,
            status=o.optString("status").takeIf{it.isNotBlank()}
        )
    }

    fun imageUrl(path:String?,size:String="w500"):String? = path?.let { "https://image.tmdb.org/t/p/$size$it" }
}
