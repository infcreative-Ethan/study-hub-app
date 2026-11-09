package com.infcreative.studyhub

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * 可以只播放一段（某一句）的播放器。位置用秒表示。
 */
class SegmentPlayer {
    private var mp: MediaPlayer? = null
    private var path: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null

    var playing by mutableStateOf(false); private set
    var position by mutableStateOf(0.0); private set
    var duration by mutableStateOf(0.0); private set
    var speed = 1.0f

    private fun ensure(file: File): MediaPlayer? {
        if (path == file.path && mp != null) return mp
        release()
        if (!file.exists()) return null
        return try {
            MediaPlayer().also {
                it.setDataSource(file.path)
                it.prepare()
                mp = it
                path = file.path
                duration = it.duration / 1000.0
            }
        } catch (e: Exception) { null }
    }

    private fun applySpeed(p: MediaPlayer) {
        try { p.playbackParams = PlaybackParams().setSpeed(speed) } catch (e: Exception) { }
    }

    /** 播放 [start, end)，end 为空表示播放到结尾。onEnd 在自然结束时回调 */
    fun play(file: File, start: Double = 0.0, end: Double? = null, onEnd: (() -> Unit)? = null) {
        val p = ensure(file) ?: return
        watcher?.cancel()
        try {
            p.seekTo((start * 1000).toInt())
            applySpeed(p)
            p.start()
        } catch (e: Exception) { return }
        playing = true
        watcher = scope.launch {
            while (isActive) {
                delay(40)
                val cur = try { p.currentPosition / 1000.0 } catch (e: Exception) { break }
                position = cur
                val finished = !p.isPlaying || (end != null && cur >= end)
                if (finished) {
                    if (p.isPlaying) p.pause()
                    playing = false
                    onEnd?.invoke()
                    break
                }
            }
        }
    }

    fun pause() {
        watcher?.cancel()
        try { mp?.let { if (it.isPlaying) it.pause() } } catch (e: Exception) { }
        playing = false
    }

    fun currentSeconds(): Double = try { (mp?.currentPosition ?: 0) / 1000.0 } catch (e: Exception) { 0.0 }

    fun release() {
        watcher?.cancel()
        try { mp?.release() } catch (e: Exception) { }
        mp = null
        path = null
        playing = false
        position = 0.0
    }
}

/** 系统语音合成，用来朗读没有原声的单词和知识点句子 */
object Tts {
    private var tts: TextToSpeech? = null
    private var ready = false

    fun init(ctx: Context) {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.85f)
                ready = true
            }
        }
    }

    fun speak(text: String) {
        if (!ready) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "s" + System.nanoTime())
    }

    fun stop() { tts?.stop() }
}
