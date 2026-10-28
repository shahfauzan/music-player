package com.offline.player

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { PlayerApp() }
            }
        }
    }
}

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun Art(model: Any?, seed: String, size: Dp, radius: Dp = 10.dp) {
    val hue = seed.hashCode().mod(360).toFloat()
    Box(
        Modifier.size(size).clip(RoundedCornerShape(radius)).background(Color.hsv(hue, .55f, .75f)),
        contentAlignment = Alignment.Center
    ) {
        Text(seed.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
        AsyncImage(model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun PlayerApp() {
    val ctx = LocalContext.current
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var granted by remember { mutableStateOf(false) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var current by remember { mutableStateOf<MediaItem?>(null) }
    var playing by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var showNow by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }

    val perms = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    fun hasPerm() = ContextCompat.checkSelfPermission(ctx, perms[0]) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasPerm()
        if (granted) songs = loadSongs(ctx)
    }
    LaunchedEffect(Unit) {
        if (hasPerm()) {
            granted = true
            songs = withContext(Dispatchers.IO) { loadSongs(ctx) }
        } else launcher.launch(perms)
    }

    DisposableEffect(Unit) {
        val future = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            fun sync(p: Player) {
                current = p.currentMediaItem; playing = p.isPlaying
                shuffle = p.shuffleModeEnabled; repeat = p.repeatMode
            }
            c.addListener(object : Player.Listener {
                override fun onEvents(p: Player, e: Player.Events) = sync(p)
            })
            sync(c)
        }, ContextCompat.getMainExecutor(ctx))
        onDispose { MediaController.releaseFuture(future) }
    }
    LaunchedEffect(controller) {
        while (true) {
            controller?.let { pos = it.currentPosition; dur = it.duration.coerceAtLeast(0) }
            delay(500)
        }
    }

    val shown = songs.filter {
        query.isBlank() || "${it.title} ${it.artist} ${it.album}".contains(query, ignoreCase = true)
    }
    fun playList(i: Int) {
        val c = controller ?: return
        c.setMediaItems(shown.map { it.toItem() }, i, 0)
        c.prepare(); c.play()
    }
    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }

    BackHandler(showNow) { showNow = false }
    val item = current
    if (showNow && item != null) {
        NowPlaying(item, playing, pos, dur, shuffle, repeat, controller, ::toggle) { showNow = false }
        return
    }
    Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
        Text("Songs", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp))
        Text("${songs.size} songs on this phone", fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp))
        OutlinedTextField(
            query, { query = it }, singleLine = true, placeholder = { Text("Search title, artist or album") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
        )
        if (!granted) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Allow access to your music so the app can find your songs.")
                Spacer(Modifier.height(12.dp))
                Button({ launcher.launch(perms) }) { Text("Allow music access") }
            }
        } else if (songs.isEmpty()) {
            Text("No music found on this phone.", modifier = Modifier.weight(1f).padding(24.dp))
        } else LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(shown, key = { _, s -> s.id }) { i, s ->
                val isCur = item?.mediaId == s.id.toString()
                Row(
                    Modifier.fillMaxWidth().clickable { playList(i) }.padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Art(s.art, s.title, 52.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                            color = if (isCur) MaterialTheme.colorScheme.primary else Color.Unspecified)
                        Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                    }
                    Text(fmt(s.durationMs), fontSize = 12.sp)
                }
            }
        }
        if (item != null) Surface(tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth().clickable { showNow = true }) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(item.mediaMetadata.artworkUri, item.mediaMetadata.title?.toString() ?: "", 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.mediaMetadata.title?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(item.mediaMetadata.artist?.toString() ?: "", maxLines = 1, fontSize = 13.sp)
                }
                TextButton(::toggle) { Text(if (playing) "❚❚" else "▶", fontSize = 20.sp) }
            }
        }
    }
}

@Composable
fun NowPlaying(
    item: MediaItem, playing: Boolean, pos: Long, dur: Long, shuffle: Boolean, repeat: Int,
    c: MediaController?, toggle: () -> Unit, onBack: () -> Unit
) {
    val title = item.mediaMetadata.title?.toString() ?: ""
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) { TextButton(onBack) { Text("⌄ Back") } }
        Spacer(Modifier.height(8.dp))
        Art(item.mediaMetadata.artworkUri, title, 300.dp, 22.dp)
        Spacer(Modifier.height(24.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(item.mediaMetadata.artist, item.mediaMetadata.albumTitle).joinToString(" · "), fontSize = 14.sp, maxLines = 1)
        Spacer(Modifier.height(12.dp))
        Slider(
            value = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
            onValueChange = { c?.seekTo((it * dur).toLong()) }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(pos), fontSize = 12.sp); Text(fmt(dur), fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            TextButton({ c?.shuffleModeEnabled = !shuffle }) {
                Text("🔀", fontSize = 22.sp, color = if (shuffle) MaterialTheme.colorScheme.primary else Color.Gray)
            }
            TextButton({ c?.seekToPrevious() }) { Text("⏮", fontSize = 26.sp) }
            Button(toggle, Modifier.size(72.dp), shape = RoundedCornerShape(36.dp), contentPadding = PaddingValues(0.dp)) {
                Text(if (playing) "❚❚" else "▶", fontSize = 26.sp)
            }
            TextButton({ c?.seekToNext() }) { Text("⏭", fontSize = 26.sp) }
            TextButton({
                c?.repeatMode = when (repeat) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }) {
                Text(if (repeat == Player.REPEAT_MODE_ONE) "🔂" else "🔁", fontSize = 22.sp,
                    color = if (repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else Color.Gray)
            }
        }
    }
}
