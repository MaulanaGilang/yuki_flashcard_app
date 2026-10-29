package app.yuki.core.data.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import app.yuki.core.data.pack.PackRegistry

/**
 * Low-latency playback of word and sentence clips (ARCHITECTURE §2.6): clips are pre-decoded
 * by SoundPool when a card appears, so a tap plays them almost instantly.
 */
class AudioPlayer(context: Context, private val packs: PackRegistry) {
  private val audioManager = context.getSystemService(AudioManager::class.java)
  private val attributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()
  private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build()
  private val loaded = LinkedHashMap<String, Int>()
  private val ready = HashSet<Int>()
  private var playWhenReady: Int? = null

  init {
    pool.setOnLoadCompleteListener { _, soundId, status ->
      if (status == 0) {
        ready += soundId
        if (playWhenReady == soundId) {
          playWhenReady = null
          start(soundId)
        }
      }
    }
  }

  /** Decodes clips ahead of time; keeps the most recent [CACHE_SIZE]. Keys are "wordId|path". */
  fun preload(wordId: String, paths: List<String>) {
    val pack = packs.packForWord(wordId) ?: return
    for (path in paths) {
      val key = "${pack.entity.id}|$path"
      if (key in loaded) continue
      val fd = packs.openMedia(pack, path) ?: continue
      loaded[key] = fd.use { pool.load(it, 1) }
    }
    while (loaded.size > CACHE_SIZE) {
      val (key, id) = loaded.entries.first()
      pool.unload(id)
      ready -= id
      loaded.remove(key)
    }
  }

  fun play(wordId: String, path: String) {
    preload(wordId, listOf(path))
    val pack = packs.packForWord(wordId) ?: return
    val id = loaded["${pack.entity.id}|$path"] ?: return
    if (id in ready) start(id) else playWhenReady = id
  }

  private fun start(soundId: Int) {
    if (Build.VERSION.SDK_INT >= 26) {
      audioManager.requestAudioFocus(
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes).build(),
      )
    }
    pool.play(soundId, 1f, 1f, 1, 0, 1f)
  }

  fun release() = pool.release()

  private companion object {
    const val CACHE_SIZE = 12
  }
}
