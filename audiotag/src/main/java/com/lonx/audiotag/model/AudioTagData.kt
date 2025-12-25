package com.lonx.audiotag.model

import java.util.Arrays
import java.util.ArrayList

data class AudioTagData(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val date: String? = null,

    val lyrics: String? = null,

    val durationMilliseconds: Int = 0,
    val bitrate: Int = 0,
    val sampleRate: Int = 0,
    val channels: Int = 0,

    val rawProperties: Map<String, Array<String>>? = null,

    val pictures: List<AudioPicture> = ArrayList()
)

data class AudioPicture(
    val data: ByteArray,
    val mimeType: String = "image/jpeg",
    val description: String = "",
    val pictureType: String = "Front Cover"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioPicture) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }
}