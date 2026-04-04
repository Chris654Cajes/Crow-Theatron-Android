package com.crowtheatron.app.player

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.crowtheatron.app.data.EnhancementMode
import com.crowtheatron.app.data.VideoEntity
import com.crowtheatron.app.data.VideoRepository
import com.crowtheatron.app.databinding.ActivityPlayerBinding
import com.crowtheatron.app.ui.setContentWithCrowInsets
import com.crowtheatron.app.util.FormatUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private val repo by lazy { VideoRepository(this) }

    private var player: ExoPlayer? = null
    private lateinit var playlistIds: LongArray
    private var playlistIndex: Int = 0
    private lateinit var working: VideoEntity

    private val handler = Handler(Looper.getMainLooper())
    private var userScrubbingPlayback = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!userScrubbingPlayback) {
                updatePlaybackSeekFromPlayer()
            }
            handler.postDelayed(this, 450L)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                val loop = binding.switchLoop.isChecked
                val auto = binding.switchAutoNext.isChecked
                if (!loop && auto) {
                    playAdjacent(1)
                }
            }
            updatePlayPauseIcon()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseIcon()
        }

        override fun onPlayerError(error: PlaybackException) {
            val detail = error.message?.takeIf { it.isNotBlank() } ?: "code ${error.errorCode}"
            Toast.makeText(this@PlayerActivity, "Playback error: $detail", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentWithCrowInsets(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val startId = intent.getLongExtra(EXTRA_VIDEO_ID, -1L)
        var ids = intent.getLongArrayExtra(EXTRA_PLAYLIST_IDS)
        if ((ids == null || ids.isEmpty()) && startId > 0L) {
            ids = longArrayOf(startId)
        }
        playlistIds = ids ?: longArrayOf()
        if (playlistIds.isEmpty()) {
            Toast.makeText(this, "Missing video", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        playlistIndex = intent.getIntExtra(EXTRA_PLAYLIST_INDEX, 0).coerceIn(0, playlistIds.lastIndex)
        if (startId > 0L) {
            val i = playlistIds.indexOf(startId)
            if (i >= 0) playlistIndex = i
        }

        val initial = repo.getById(playlistIds[playlistIndex])
        if (initial == null) {
            Toast.makeText(this, "Video not in library", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        working = initial

        binding.playerView.useController = false

        setupEnhancementSpinner()
        bindUiFromWorking()
        setupControls()
        initPlayer()
        attachCurrentMedia(play = true)
        handler.post(tickRunnable)
    }

    override fun onStop() {
        persistProgress()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        player?.removeListener(playerListener)
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun initPlayer() {
        if (player != null) return
        val exo = ExoPlayer.Builder(this).build()
        exo.addListener(playerListener)
        player = exo
        binding.playerView.player = exo
    }

    private fun mediaItemFor(entity: VideoEntity): MediaItem {
        val startMs = entity.trimStartMs.coerceAtLeast(0L)
        val clipBuilder = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
        val endMs = entity.trimEndMs
        if (endMs > 0L && endMs > startMs) {
            clipBuilder.setEndPositionMs(endMs)
        } else {
            clipBuilder.setEndPositionMs(C.TIME_END_OF_SOURCE)
        }
        return MediaItem.Builder()
            .setUri(entity.contentUri)
            .setClippingConfiguration(clipBuilder.build())
            .build()
    }

    private fun fullDurationMs(): Long {
        val meta = working.durationMs
        if (meta > 0L) return meta
        val d = player?.duration ?: C.TIME_UNSET
        if (d != C.TIME_UNSET && d > 0L) return d
        return 1L
    }

    private fun attachCurrentMedia(play: Boolean) {
        val exo = player ?: return
        exo.stop()
        exo.setMediaItem(mediaItemFor(working))
        exo.prepare()
        val clipResume = (working.positionMs - working.trimStartMs).coerceAtLeast(0L)
        exo.seekTo(clipResume)
        applyPitch(working.pitchSemitones)
        exo.repeatMode = if (working.loopPlayback) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        exo.playWhenReady = play
        binding.playerView.postDelayed({ applyEnhancementOverlay() }, 120L)
        updatePlayPauseIcon()
    }

    private fun applyPitch(semitones: Int) {
        val pitch = 2.0.pow(semitones / 12.0).toFloat()
        player?.playbackParameters = PlaybackParameters(1f, pitch)
    }

    /** Lightweight on-screen tint to suggest grading (no GPU re-encode). */
    private fun applyEnhancementOverlay() {
        val overlay = binding.enhancementOverlay
        val mode = working.enhancement
        when (mode) {
            EnhancementMode.NONE -> overlay.visibility = View.GONE
            EnhancementMode.VIVID_HD -> {
                overlay.setBackgroundColor(Color.argb(45, 255, 240, 160))
                overlay.visibility = View.VISIBLE
            }
            EnhancementMode.CINEMA_CONTRAST -> {
                overlay.setBackgroundColor(Color.argb(55, 20, 20, 40))
                overlay.visibility = View.VISIBLE
            }
            EnhancementMode.WARM_FILM -> {
                overlay.setBackgroundColor(Color.argb(50, 255, 180, 80))
                overlay.visibility = View.VISIBLE
            }
            EnhancementMode.COOL_HDR_SIM -> {
                overlay.setBackgroundColor(Color.argb(48, 80, 140, 255))
                overlay.visibility = View.VISIBLE
            }
        }
    }

    private fun bindUiFromWorking() {
        binding.toolbar.title = working.title
        binding.videoTitle.text = working.title
        binding.pitchLabel.text = getString(com.crowtheatron.app.R.string.pitch_label, working.pitchSemitones)
        binding.seekPitch.progress = (working.pitchSemitones + 6).coerceIn(0, 12)

        binding.switchAutoNext.isChecked = working.autoPlayNext
        binding.switchLoop.isChecked = working.loopPlayback

        val idx = EnhancementMode.entries.indexOf(working.enhancement).coerceAtLeast(0)
        binding.spinnerEnhancement.setSelection(idx)

        binding.btnFavorite.text = getString(
            if (working.favorite) com.crowtheatron.app.R.string.favorite_off else com.crowtheatron.app.R.string.favorite_on
        )

        bindTrimSeekers()
        updateTimeLabel()
    }

    private fun bindTrimSeekers() {
        val full = max(fullDurationMs(), 1L)
        binding.seekTrimStart.progress = ((working.trimStartMs * 1000L) / full).toInt().coerceIn(0, 1000)
        val endP = if (working.trimEndMs <= 0L) {
            1000
        } else {
            ((working.trimEndMs * 1000L) / full).toInt().coerceIn(0, 1000)
        }
        binding.seekTrimEnd.progress = endP
    }

    private fun setupEnhancementSpinner() {
        val labels = EnhancementMode.entries.map { it.name.replace('_', ' ') }
        val ad = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        binding.spinnerEnhancement.adapter = ad
        binding.spinnerEnhancement.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val mode = EnhancementMode.entries.getOrNull(position) ?: return
                working = working.copy(enhancement = mode)
                applyEnhancementOverlay()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            val exo = player ?: return@setOnClickListener
            if (exo.isPlaying) {
                exo.pause()
            } else {
                exo.play()
            }
            updatePlayPauseIcon()
        }

        binding.btnStop.setOnClickListener {
            player?.pause()
            player?.seekTo(0L)
            persistProgressAtTrimStart()
            updatePlaybackSeekFromPlayer()
        }

        binding.btnRewind.setOnClickListener {
            jumpBy(-working.seekJumpSec)
        }
        binding.btnForward.setOnClickListener {
            jumpBy(working.seekJumpSec)
        }

        binding.btnPrev.setOnClickListener { playAdjacent(-1) }
        binding.btnNext.setOnClickListener { playAdjacent(1) }

        binding.seekPlayback.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val exo = player ?: return
                val dur = exo.duration
                if (dur == C.TIME_UNSET || dur <= 0L) return
                val pos = dur * progress / 1000L
                exo.seekTo(pos)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userScrubbingPlayback = true
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userScrubbingPlayback = false
                persistProgress()
            }
        })

        binding.seekPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val semis = (progress - 6).coerceIn(-6, 6)
                binding.pitchLabel.text = getString(com.crowtheatron.app.R.string.pitch_label, semis)
                working = working.copy(pitchSemitones = semis)
                applyPitch(semis)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.switchAutoNext.setOnCheckedChangeListener { _, checked ->
            working = working.copy(autoPlayNext = checked)
        }
        binding.switchLoop.setOnCheckedChangeListener { _, checked ->
            working = working.copy(loopPlayback = checked)
            player?.repeatMode = if (checked) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }

        binding.btnFavorite.setOnClickListener {
            val next = !working.favorite
            working = working.copy(favorite = next)
            repo.setFavorite(working.id, next)
            binding.btnFavorite.text = getString(
                if (next) com.crowtheatron.app.R.string.favorite_off else com.crowtheatron.app.R.string.favorite_on
            )
        }

        binding.btnSavePrefs.setOnClickListener {
            readTrimFromSeekBars()
            working = working.copy(
                pitchSemitones = binding.seekPitch.progress - 6,
                autoPlayNext = binding.switchAutoNext.isChecked,
                loopPlayback = binding.switchLoop.isChecked,
                enhancement = EnhancementMode.entries[binding.spinnerEnhancement.selectedItemPosition],
            )
            repo.savePreferences(working)
            val wasPlaying = player?.isPlaying == true
            attachCurrentMedia(play = wasPlaying)
            Toast.makeText(this, "Saved for this video", Toast.LENGTH_SHORT).show()
        }

        refreshNavButtons()
    }

    private fun readTrimFromSeekBars() {
        val full = max(fullDurationMs(), 1L)
        var start = binding.seekTrimStart.progress * full / 1000L
        var end = binding.seekTrimEnd.progress * full / 1000L
        if (end <= start + 500L) {
            end = min(start + 500L, full)
        }
        working = working.copy(
            trimStartMs = start,
            trimEndMs = if (end >= full - 250L) 0L else end
        )
    }

    private fun jumpBy(deltaSec: Int) {
        val exo = player ?: return
        val dur = exo.duration
        if (dur == C.TIME_UNSET || dur <= 0L) return
        val deltaMs = deltaSec * 1000L
        val next = (exo.currentPosition + deltaMs).coerceIn(0L, dur)
        exo.seekTo(next)
        persistProgress()
    }

    private fun playAdjacent(delta: Int) {
        persistProgress()
        val next = playlistIndex + delta
        if (next < 0 || next >= playlistIds.size) {
            Toast.makeText(this, "No adjacent video in this list", Toast.LENGTH_SHORT).show()
            return
        }
        playlistIndex = next
        val fresh = repo.getById(playlistIds[playlistIndex]) ?: run {
            Toast.makeText(this, "Missing video", Toast.LENGTH_SHORT).show()
            return
        }
        working = fresh
        bindUiFromWorking()
        attachCurrentMedia(play = true)
        refreshNavButtons()
    }

    private fun refreshNavButtons() {
        binding.btnPrev.isEnabled = playlistIndex > 0
        binding.btnNext.isEnabled = playlistIndex < playlistIds.lastIndex
    }

    private fun updatePlaybackSeekFromPlayer() {
        val exo = player ?: return
        val dur = exo.duration
        if (dur == C.TIME_UNSET || dur <= 0L) return
        val p = ((exo.currentPosition * 1000L) / dur).toInt().coerceIn(0, 1000)
        binding.seekPlayback.progress = p
        updateTimeLabel()
    }

    private fun updateTimeLabel() {
        val exo = player
        val pos = exo?.currentPosition ?: 0L
        val dur = exo?.duration ?: C.TIME_UNSET
        val durText = if (dur != C.TIME_UNSET && dur > 0L) FormatUtils.formatDuration(dur) else FormatUtils.formatDuration(working.durationMs)
        val absPos = working.trimStartMs + pos
        binding.timeLabel.text = "${FormatUtils.formatDuration(absPos)} / $durText"
    }

    private fun updatePlayPauseIcon() {
        val playing = player?.isPlaying == true
        binding.btnPlayPause.setImageResource(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
    }

    private fun persistProgress() {
        val exo = player ?: return
        if (working.id <= 0L) return
        val abs = working.trimStartMs + exo.currentPosition
        repo.savePlaybackPosition(working.id, abs)
    }

    private fun persistProgressAtTrimStart() {
        if (working.id <= 0L) return
        repo.savePlaybackPosition(working.id, working.trimStartMs)
    }

    companion object {
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_PLAYLIST_IDS = "playlist_ids"
        const val EXTRA_PLAYLIST_INDEX = "playlist_index"
    }
}
