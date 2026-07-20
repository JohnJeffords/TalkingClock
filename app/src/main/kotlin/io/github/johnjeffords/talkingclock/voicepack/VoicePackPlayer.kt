package io.github.johnjeffords.talkingclock.voicepack

import android.media.AudioAttributes
import android.media.SoundPool
import io.github.johnjeffords.talkingclock.domain.voicepack.PhraseComposer
import io.github.johnjeffords.talkingclock.speech.Utterance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Plays utterances from a voice pack's recorded clips: compose the token
 * list (PhraseComposer), then play each clip back-to-back with the pack's
 * configured gap. SoundPool gives the sub-100 ms start latency the spec
 * asks for; since it has no per-clip completion callback, stitching paces
 * itself with the durations measured at import time.
 *
 * Priority follows the same collision law as TtsSpeaker: equal-or-higher
 * replaces the running utterance, lower is dropped.
 */
class VoicePackPlayer(
    private val store: VoicePackStore,
    private val pack: VoicePackStore.InstalledPack,
    private val scope: CoroutineScope,
) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(1) // clips play strictly one at a time
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .build()

    /** token → loaded SoundPool sample id (loaded once, up front). */
    private val sampleIds = mutableMapOf<String, Int>()

    /** Tokens confirmed decodable by SoundPool's async load callback. */
    private val loadedSamples = mutableSetOf<Int>()

    private var playJob: Job? = null
    private var playingPriority = Int.MIN_VALUE

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(loadedSamples) { loadedSamples += sampleId }
        }
        // Load every clip once. Packs are small (≤ ~70 short clips); memory
        // cost is a few MB and playback becomes instant.
        pack.manifest.clips.keys.forEach { token ->
            store.clipFile(pack, token)?.takeIf { it.isFile }?.let { file ->
                sampleIds[token] = soundPool.load(file.absolutePath, 1)
            }
        }
    }

    /**
     * Try to voice [utterance] from clips. [PlayResult.Unsupported] means
     * the caller should fall back to TTS for the whole utterance;
     * [PlayResult.Dropped] means a higher-priority pack utterance is already
     * playing and the caller must stay silent.
     */
    fun tryPlay(utterance: Utterance, priority: Int): PlayResult {
        val tokens = PhraseComposer.compose(pack.manifest, utterance) ?: return PlayResult.Unsupported

        // Every needed clip must be loaded and decodable before we commit.
        val samples = tokens.map { token ->
            val id = sampleIds[token] ?: return PlayResult.Unsupported
            if (id !in synchronized(loadedSamples) { loadedSamples.toSet() }) {
                return PlayResult.Unsupported
            }
            token to id
        }

        if (playJob?.isActive == true && priority < playingPriority) return PlayResult.Dropped

        playJob?.cancel()
        playingPriority = priority
        playJob = scope.launch {
            try {
                samples.forEach { (token, sampleId) ->
                    soundPool.play(sampleId, 1f, 1f, 1, 0, 1f)
                    val duration = pack.durationsMs[token] ?: VoicePackStore.DEFAULT_CLIP_MS
                    delay(duration + pack.manifest.gapMs)
                }
            } finally {
                playingPriority = Int.MIN_VALUE
            }
        }
        return PlayResult.Played
    }

    /** Stop mid-utterance. */
    fun stop() {
        playJob?.cancel()
        playJob = null
        soundPool.autoPause()
        playingPriority = Int.MIN_VALUE
    }

    /** Release the SoundPool (when the pack is switched away or deleted). */
    fun release() {
        stop()
        soundPool.release()
    }

    /** Stop only when [priority] owns the utterance currently playing. */
    fun stop(priority: Int) {
        if (playingPriority == priority) stop()
    }

    enum class PlayResult {
        Played,
        Unsupported,
        Dropped,
    }
}
