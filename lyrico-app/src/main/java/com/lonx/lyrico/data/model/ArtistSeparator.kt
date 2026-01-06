package com.lonx.lyrico.data.model

enum class ArtistSeparator {
    COMMA,
    SLASH,
    SEMICOLON,
    ENUMERATION_COMMA
}

fun ArtistSeparator.toChar(): String {
    return when (this) {
        ArtistSeparator.COMMA -> ","
        ArtistSeparator.SLASH -> "/"
        ArtistSeparator.SEMICOLON -> ";"
        ArtistSeparator.ENUMERATION_COMMA -> "、"
    }
}
fun String.toArtistSeparator(): ArtistSeparator {
    return when (this) {
        "," -> ArtistSeparator.COMMA
        "/" -> ArtistSeparator.SLASH
        ";" -> ArtistSeparator.SEMICOLON
        "、" -> ArtistSeparator.ENUMERATION_COMMA
        else -> ArtistSeparator.COMMA
    }
}