package com.lonx.lyrics.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class Source(val sourceName: String) {
    KG("酷狗音乐"),
    QM("QQ音乐"),
    NE("网易云音乐")
}

@Parcelize
data class SongSearchResult(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // 毫秒
    val source: Source,
    val date: String = "",
    val trackerNumber: String = "",
    val picUrl: String = "",
    val extras: Map<String, String> = emptyMap()
) : Parcelable

