package com.offline.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Song(
    val id: Long, val title: String, val artist: String, val album: String,
    val albumId: Long, val durationMs: Long, val uri: Uri
) {
    val art: Uri
        get() = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

    fun toItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title).setArtist(artist).setAlbumTitle(album).setArtworkUri(art).build()
        ).build()
}

fun loadSongs(ctx: Context): List<Song> {
    val out = ArrayList<Song>()
    val m = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val proj = arrayOf(
        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION
    )
    ctx.contentResolver.query(
        m, proj, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
    )?.use { c ->
        val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val iT = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val iAr = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val iAl = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val iAid = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val iD = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        while (c.moveToNext()) {
            val id = c.getLong(iId)
            out.add(
                Song(
                    id, c.getString(iT) ?: "Unknown",
                    c.getString(iAr)?.takeIf { it != "<unknown>" } ?: "Unknown artist",
                    c.getString(iAl) ?: "", c.getLong(iAid), c.getLong(iD),
                    ContentUris.withAppendedId(m, id)
                )
            )
        }
    }
    return out
}
