package com.lonx.lyrics.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class Source { KG, QM, NE, LRCLIB }

@Parcelize
data class SongSearchResult(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // 毫秒
    val source: Source,
    val hash: String? = null, // KG 特有
    val mid: String? = null,
    val date: String = "",
    val trackerNumber: String = "",
    val picUrl: String = "",
) : Parcelable

