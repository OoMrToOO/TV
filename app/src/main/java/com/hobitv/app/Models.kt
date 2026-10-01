package com.hobitv.app

enum class MediaType { MOVIE, TV, LIVE }

data class Media(
    val id:Int,
    val title:String,
    val overview:String,
    val posterPath:String?=null,
    val backdropPath:String?=null,
    val releaseDate:String?=null,
    val rating:Double=0.0,
    val mediaType:MediaType=MediaType.MOVIE,
    val runtime:Int?=null,
    val trailerKey:String?=null,
    val streamUrl:String?=null,
    val season:Int?=null,
    val episode:Int?=null,
    val genres:List<String> = emptyList(),
    val status:String? = null
)
