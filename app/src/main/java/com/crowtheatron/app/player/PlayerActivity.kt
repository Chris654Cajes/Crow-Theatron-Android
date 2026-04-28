package com.crowtheatron.app.player

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
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
import com.crowtheatron.app.R
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
    private var playlistIndex = 0
    private lateinit var working: VideoEntity

    private val handler = Handler(Looper.getMainLooper())
    private var userScrubbingPlayback = false
    private var playerReady = false
    private var isFullscreen = false

    // True while we are programmatically setting seekbar values — suppresses listeners
    private var isBindingUi = false

    private var currentSpeed  = 1.0f
    private var currentVolume = 1.0f

    // ── Tick ──────────────────────────────────────────────────────────────────
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!userScrubbingPlayback && playerReady) tickTimeline()
            handler.postDelayed(this, 450L)
        }
    }

    // ── ExoPlayer listener ────────────────────────────────────────────────────
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            playerReady = state == Player.STATE_READY || state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY) {
                isBindingUi = true
                bindTrimSeekers()
                isBindingUi = false
                tickTimeline()
            }
            if (state == Player.STATE_ENDED) {
                if (binding.switchLoop.isChecked) {
                    // Seek to trim start, not file start
                    player?.seekTo(working.trimStartMs.coerceAtLeast(0L))
                    player?.play()
                } else if (binding.switchAutoNext.isChecked) {
                    playAdjacent(1)
                }
            }
            updatePlayPauseIcon()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) = updatePlayPauseIcon()
        override fun onPlayerError(error: PlaybackException) {
            Toast.makeText(this@PlayerActivity,
                "Playback error: ${error.message ?: "code ${error.errorCode}"}",
                Toast.LENGTH_LONG).show()
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentWithCrowInsets(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val startId = intent.getLongExtra(EXTRA_VIDEO_ID, -1L)
        var ids     = intent.getLongArrayExtra(EXTRA_PLAYLIST_IDS)
        if ((ids == null || ids.isEmpty()) && startId > 0L) ids = longArrayOf(startId)
        playlistIds = ids ?: longArrayOf()
        if (playlistIds.isEmpty()) { showAndFinish("Missing video"); return }

        playlistIndex = intent.getIntExtra(EXTRA_PLAYLIST_INDEX, 0).coerceIn(0, playlistIds.lastIndex)
        if (startId > 0L) { val i = playlistIds.indexOf(startId); if (i >= 0) playlistIndex = i }

        val initial = repo.getById(playlistIds[playlistIndex])
        if (initial == null) { showAndFinish("Video not in library"); return }
        working = initial
        binding.playerView.useController = false

        currentSpeed  = working.playbackSpeed.coerceIn(0.5f, 2.0f)
        currentVolume = working.volumeLevel.coerceIn(0f, 1f)

        setupEnhancementSpinner()
        bindUiFromWorking()
        setupControls()
        initPlayer()
        attachCurrentMedia(play = true)
        handler.post(tickRunnable)
    }

    override fun onResume() {
        super.onResume()
        window.navigationBarColor = Color.BLACK
    }
    override fun onStop()    { persistProgress(); super.onStop() }
    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        player?.removeListener(playerListener)
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun showAndFinish(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); finish()
    }

    // ── Player setup ──────────────────────────────────────────────────────────
    private fun initPlayer() {
        if (player != null) return
        val exo = ExoPlayer.Builder(this).build()
        exo.addListener(playerListener)
        player = exo
        binding.playerView.player = exo
    }

    /**
     * Build a MediaItem for [e].
     * We do NOT use ClippingConfiguration.setStartPositionMs because it remaps
     * the ExoPlayer timeline so currentPosition becomes clip-relative (0-based),
     * which breaks all absolute-position arithmetic for seek, rewind, and forward.
     *
     * Instead:
     *  - Trim start → seekTo(trimStartMs) after prepare()
     *  - Trim end   → ClippingConfiguration.setEndPositionMs only (safe upper bound,
     *                 does NOT remap currentPosition)
     */
    private fun mediaItemFor(e: VideoEntity): MediaItem {
        val builder = MediaItem.Builder().setUri(e.contentUri)
        val endMs = e.trimEndMs
        if (endMs > 0L && endMs > e.trimStartMs) {
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setEndPositionMs(endMs)
                    .build()
            )
        }
        return builder.build()
    }

    /**
     * Load [working] into the player.
     * After prepare(), seeks to the persisted absolute position (clamped to trim window).
     * All subsequent position values from exo.currentPosition are absolute file ms.
     */
    private fun attachCurrentMedia(play: Boolean) {
        val exo = player ?: return
        playerReady = false
        exo.stop()
        exo.setMediaItem(mediaItemFor(working))
        exo.prepare()

        // Resume at saved absolute position, clamped inside trim window
        val trimLo = working.trimStartMs.coerceAtLeast(0L)
        val trimHi = if (working.trimEndMs > 0L) working.trimEndMs else Long.MAX_VALUE
        val resumeMs = working.positionMs.coerceIn(trimLo, trimHi)
        // If no saved position, start at trim start
        exo.seekTo(if (resumeMs > 0L) resumeMs else trimLo)

        applyPitchAndSpeed(working.pitchSemitones, currentSpeed)
        exo.volume = currentVolume
        exo.repeatMode = if (working.loopPlayback) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        exo.playWhenReady = play
        binding.playerView.postDelayed({ applyEnhancementOverlay() }, 120L)
        updatePlayPauseIcon()
    }

    private fun applyPitchAndSpeed(semitones: Int, speed: Float) {
        val pitch = 2.0.pow(semitones / 12.0).toFloat()
        player?.playbackParameters = PlaybackParameters(speed.coerceIn(0.5f, 2.0f), pitch)
    }

    // ── Duration helpers ──────────────────────────────────────────────────────

    /** Absolute file duration from ExoPlayer when ready, else metadata. */
    private fun liveDurationMs(): Long {
        val d = player?.duration ?: C.TIME_UNSET
        return if (d != C.TIME_UNSET && d > 0L) d else working.durationMs.takeIf { it > 0L } ?: 1L
    }

    /** Metadata-only file duration (does not call ExoPlayer). */
    private fun metaDurationMs(): Long = working.durationMs.takeIf { it > 0L } ?: 1L

    // ── Timeline ──────────────────────────────────────────────────────────────
    private fun tickTimeline() {
        val exo = player ?: return
        val dur = exo.duration
        if (dur == C.TIME_UNSET || dur <= 0L) return
        val pos = exo.currentPosition          // absolute ms from file start
        val progress = ((pos * 1000L) / dur).toInt().coerceIn(0, 1000)
        if (!userScrubbingPlayback) binding.seekPlayback.progress = progress
        updateTimeLabel(pos, dur)
    }

    private fun updateTimeLabel(absPos: Long, dur: Long) {
        val label = "${FormatUtils.formatDuration(absPos)} / ${FormatUtils.formatDuration(dur)}"
        binding.timeLabel.text      = label
        binding.tvPlaybackTime.text = label
    }

    private fun updatePlayPauseIcon() {
        binding.btnPlayPause.setImageResource(
            if (player?.isPlaying == true) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    // ── UI binding ────────────────────────────────────────────────────────────
    private fun bindUiFromWorking() {
        isBindingUi = true

        binding.toolbar.title   = working.title
        binding.videoTitle.text = working.title

        val semis = working.pitchSemitones.coerceIn(-6, 6)
        binding.seekPitch.progress = semis + 6
        binding.pitchLabel.text    = pitchLabel(semis)

        binding.seekSpeed.progress = speedToProgress(currentSpeed)
        binding.tvSpeedValue.text  = speedLabel(currentSpeed)

        binding.seekVolume.progress = (currentVolume * 100).toInt()
        binding.tvVolumeValue.text  = "${(currentVolume * 100).toInt()}%"

        binding.switchAutoNext.isChecked = working.autoPlayNext
        binding.switchLoop.isChecked     = working.loopPlayback

        binding.btnFavorite.text = getString(
            if (working.favorite) R.string.favorite_off else R.string.favorite_on
        )

        val idx = EnhancementMode.entries.indexOf(working.enhancement).coerceAtLeast(0)
        binding.spinnerEnhancement.setSelection(idx)

        bindTrimSeekers()
        updateTimeLabel(working.positionMs.coerceAtLeast(0L), metaDurationMs())

        isBindingUi = false
    }

    private fun bindTrimSeekers() {
        val full = max(metaDurationMs(), 1L)
        binding.seekTrimStart.progress =
            ((working.trimStartMs * 1000L) / full).toInt().coerceIn(0, 1000)
        binding.seekTrimEnd.progress =
            if (working.trimEndMs <= 0L) 1000
            else ((working.trimEndMs * 1000L) / full).toInt().coerceIn(0, 1000)
    }

    private fun setupEnhancementSpinner() {
        val labels = EnhancementMode.entries.map { it.name.replace('_', ' ') }
        val ad = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            override fun getView(pos: Int, convert: View?, parent: android.view.ViewGroup) =
                super.getView(pos, convert, parent).also {
                    (it as? android.widget.TextView)
                        ?.setTextColor(resources.getColor(R.color.crow_on_bg, theme))
                }
            override fun getDropDownView(pos: Int, convert: View?, parent: android.view.ViewGroup) =
                super.getDropDownView(pos, convert, parent).also {
                    (it as? android.widget.TextView)?.apply {
                        setTextColor(resources.getColor(R.color.crow_on_bg, theme))
                        setBackgroundColor(resources.getColor(R.color.crow_surface_elevated, theme))
                        setPadding(32, 24, 32, 24)
                    }
                }
        }
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerEnhancement.adapter = ad
        binding.spinnerEnhancement.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isBindingUi) return          // suppress during bindUiFromWorking
                val mode = EnhancementMode.entries.getOrNull(position) ?: return
                working = working.copy(enhancement = mode)
                applyEnhancementOverlay()
                persistPrefs()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun applyEnhancementOverlay() {
        val o = binding.enhancementOverlay
        when (working.enhancement) {
            EnhancementMode.NONE            -> o.visibility = View.GONE
            EnhancementMode.VIVID_HD        -> { o.setBackgroundColor(Color.argb(45,255,240,160)); o.visibility = View.VISIBLE }
            EnhancementMode.CINEMA_CONTRAST -> { o.setBackgroundColor(Color.argb(55, 20, 20, 40)); o.visibility = View.VISIBLE }
            EnhancementMode.WARM_FILM       -> { o.setBackgroundColor(Color.argb(50,255,180, 80)); o.visibility = View.VISIBLE }
            EnhancementMode.COOL_HDR_SIM    -> { o.setBackgroundColor(Color.argb(48, 80,140,255)); o.visibility = View.VISIBLE }
        }
    }

    // ── Controls ──────────────────────────────────────────────────────────────
    private fun setupControls() {

        binding.btnPlayPause.setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
        }

        binding.btnStop.setOnClickListener {
            player?.pause()
            player?.seekTo(working.trimStartMs.coerceAtLeast(0L))
            persistProgress()
            tickTimeline()
        }

        // Rewind / forward: pure seekTo on the already-loaded player, no restart
        binding.btnRewind.setOnClickListener  { jumpBy(-working.seekJumpSec) }
        binding.btnForward.setOnClickListener { jumpBy( working.seekJumpSec) }

        binding.btnPrev.setOnClickListener { playAdjacent(-1) }
        binding.btnNext.setOnClickListener { playAdjacent(1) }
        binding.btnFullscreen.setOnClickListener { toggleFullscreen() }

        // ── Playback seekbar ──────────────────────────────────────────────────
        binding.seekPlayback.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || isBindingUi) return
                val exo = player ?: return
                val dur = liveDurationMs()
                // Map seekbar progress to absolute position clamped inside trim window
                val trimLo  = working.trimStartMs.coerceAtLeast(0L)
                val trimHi  = if (working.trimEndMs > 0L) working.trimEndMs else dur
                val seekTo  = (dur * progress / 1000L).coerceIn(trimLo, trimHi)
                exo.seekTo(seekTo)
                updateTimeLabel(seekTo, dur)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { userScrubbingPlayback = true }
            override fun onStopTrackingTouch(sb: SeekBar?)  {
                userScrubbingPlayback = false
                persistProgress()
            }
        })

        // ── Pitch ─────────────────────────────────────────────────────────────
        binding.seekPitch.setOnSeekBarChangeListener(seekBarListener(
            onChange = { p, fromUser -> if (fromUser) applyPitchStep(p) },
            onStop   = { persistPrefs() }
        ))
        binding.btnPitchDown.setOnClickListener {
            val np = (binding.seekPitch.progress - 1).coerceAtLeast(0)
            binding.seekPitch.progress = np; applyPitchStep(np); persistPrefs()
        }
        binding.btnPitchUp.setOnClickListener {
            val np = (binding.seekPitch.progress + 1).coerceAtMost(12)
            binding.seekPitch.progress = np; applyPitchStep(np); persistPrefs()
        }
        binding.btnPitchReset.setOnClickListener {
            binding.seekPitch.progress = 6; applyPitchStep(6); persistPrefs()
        }

        // ── Speed ─────────────────────────────────────────────────────────────
        binding.seekSpeed.setOnSeekBarChangeListener(seekBarListener(
            onChange = { p, fromUser -> if (fromUser) applySpeedStep(p) },
            onStop   = { persistPrefs() }
        ))
        binding.btnSpeedDown.setOnClickListener {
            val np = (binding.seekSpeed.progress - 1).coerceAtLeast(0)
            binding.seekSpeed.progress = np; applySpeedStep(np); persistPrefs()
        }
        binding.btnSpeedUp.setOnClickListener {
            val np = (binding.seekSpeed.progress + 1).coerceAtMost(30)
            binding.seekSpeed.progress = np; applySpeedStep(np); persistPrefs()
        }
        binding.btnSpeedReset.setOnClickListener {
            val np = speedToProgress(1.0f)
            binding.seekSpeed.progress = np; applySpeedStep(np); persistPrefs()
        }

        // ── Volume ────────────────────────────────────────────────────────────
        binding.seekVolume.setOnSeekBarChangeListener(seekBarListener(
            onChange = { p, fromUser -> if (fromUser) applyVolumeStep(p) },
            onStop   = { persistPrefs() }
        ))
        binding.btnVolumeDown.setOnClickListener {
            val np = ((currentVolume * 100).toInt() - 5).coerceIn(0, 100)
            binding.seekVolume.progress = np; applyVolumeStep(np); persistPrefs()
        }
        binding.btnVolumeUp.setOnClickListener {
            val np = ((currentVolume * 100).toInt() + 5).coerceIn(0, 100)
            binding.seekVolume.progress = np; applyVolumeStep(np); persistPrefs()
        }

        // ── Trim seekbars — only write on finger-lift, never during binding ───
        binding.seekTrimStart.setOnSeekBarChangeListener(seekBarListener(
            onStop = { if (!isBindingUi) { readTrimFromSeekBars(); persistPrefs() } }
        ))
        binding.seekTrimEnd.setOnSeekBarChangeListener(seekBarListener(
            onStop = { if (!isBindingUi) { readTrimFromSeekBars(); persistPrefs() } }
        ))

        // ── Switches ──────────────────────────────────────────────────────────
        binding.switchLoop.setOnCheckedChangeListener { _, checked ->
            if (isBindingUi) return@setOnCheckedChangeListener
            working = working.copy(loopPlayback = checked)
            player?.repeatMode = if (checked) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            persistPrefs()
        }
        binding.switchAutoNext.setOnCheckedChangeListener { _, checked ->
            if (isBindingUi) return@setOnCheckedChangeListener
            working = working.copy(autoPlayNext = checked)
            persistPrefs()
        }

        // ── Favourite ─────────────────────────────────────────────────────────
        binding.btnFavorite.setOnClickListener {
            val next = !working.favorite
            working = working.copy(favorite = next)
            repo.setFavorite(working.id, next)
            binding.btnFavorite.text = getString(
                if (next) R.string.favorite_off else R.string.favorite_on
            )
        }

        // ── Save prefs: apply in-place, no pipeline restart ───────────────────
        binding.btnSavePrefs.setOnClickListener {
            readTrimFromSeekBars()
            working = working.copy(
                pitchSemitones = (binding.seekPitch.progress - 6).coerceIn(-6, 6),
                autoPlayNext   = binding.switchAutoNext.isChecked,
                loopPlayback   = binding.switchLoop.isChecked,
                enhancement    = EnhancementMode.entries[binding.spinnerEnhancement.selectedItemPosition],
                playbackSpeed  = currentSpeed,
                volumeLevel    = currentVolume,
            )
            persistPrefs()
            applyPitchAndSpeed(working.pitchSemitones, currentSpeed)
            player?.volume = currentVolume
            player?.repeatMode = if (working.loopPlayback) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            applyEnhancementOverlay()
            Toast.makeText(this, "Saved for this video", Toast.LENGTH_SHORT).show()
        }

        refreshNavButtons()
    }

    // ── Apply helpers ─────────────────────────────────────────────────────────
    private fun applyPitchStep(progress: Int) {
        val semis = (progress - 6).coerceIn(-6, 6)
        binding.pitchLabel.text = pitchLabel(semis)
        working = working.copy(pitchSemitones = semis)
        applyPitchAndSpeed(semis, currentSpeed)
    }

    private fun applySpeedStep(progress: Int) {
        currentSpeed = progressToSpeed(progress)
        binding.tvSpeedValue.text = speedLabel(currentSpeed)
        working = working.copy(playbackSpeed = currentSpeed)
        applyPitchAndSpeed(working.pitchSemitones, currentSpeed)
    }

    private fun applyVolumeStep(progress: Int) {
        currentVolume = progress / 100f
        binding.tvVolumeValue.text = "$progress%"
        working = working.copy(volumeLevel = currentVolume)
        player?.volume = currentVolume
    }

    // ── Persistence ───────────────────────────────────────────────────────────
    private fun persistPrefs() {
        if (working.id <= 0L) return
        repo.savePreferences(working)
    }

    private fun persistProgress() {
        val exo = player ?: return
        if (working.id <= 0L) return
        // currentPosition is already absolute (file ms) — no offset needed
        repo.savePlaybackPosition(working.id, exo.currentPosition.coerceAtLeast(0L))
    }

    // ── Seek & navigation ─────────────────────────────────────────────────────
    /**
     * Jump [deltaSec] seconds from current absolute position.
     * Clamps within the trim window. Does NOT stop/prepare — pure seekTo.
     */
    private fun jumpBy(deltaSec: Int) {
        val exo    = player ?: return
        val pos    = exo.currentPosition                      // absolute ms
        val dur    = liveDurationMs()
        val trimLo = working.trimStartMs.coerceAtLeast(0L)
        val trimHi = if (working.trimEndMs > 0L) working.trimEndMs else dur
        val newPos = (pos + deltaSec * 1000L).coerceIn(trimLo, trimHi)
        exo.seekTo(newPos)
        persistProgress()
        tickTimeline()
    }

    private fun playAdjacent(delta: Int) {
        persistProgress()
        val next = playlistIndex + delta
        if (next < 0 || next >= playlistIds.size) {
            Toast.makeText(this, "No adjacent video", Toast.LENGTH_SHORT).show(); return
        }
        playlistIndex = next
        val fresh = repo.getById(playlistIds[playlistIndex]) ?: run {
            Toast.makeText(this, "Missing video", Toast.LENGTH_SHORT).show(); return
        }
        working = fresh
        currentSpeed  = working.playbackSpeed.coerceIn(0.5f, 2.0f)
        currentVolume = working.volumeLevel.coerceIn(0f, 1f)
        bindUiFromWorking()
        attachCurrentMedia(play = true)
        refreshNavButtons()
    }

    private fun refreshNavButtons() {
        binding.btnPrev.isEnabled = playlistIndex > 0
        binding.btnNext.isEnabled = playlistIndex < playlistIds.lastIndex
    }

    private fun readTrimFromSeekBars() {
        val full  = max(metaDurationMs(), 1L)
        val start = binding.seekTrimStart.progress * full / 1000L
        var end   = binding.seekTrimEnd.progress   * full / 1000L
        if (end <= start + 500L) end = min(start + 500L, full)
        working = working.copy(
            trimStartMs = start,
            trimEndMs   = if (end >= full - 250L) 0L else end,
        )
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            supportActionBar?.hide()
            binding.playerView.layoutParams.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            supportActionBar?.show()
            binding.playerView.layoutParams.height = (220 * resources.displayMetrics.density).toInt()
        }
        binding.playerView.requestLayout()
    }

    // ── Utilities ─────────────────────────────────────────────────────────────
    private fun seekBarListener(
        onChange: (Int, Boolean) -> Unit = { _, _ -> },
        onStop:   () -> Unit             = {},
    ) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) = onChange(p, fromUser)
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) = onStop()
    }

    private fun progressToSpeed(p: Int)    = (0.5f + p * 0.05f).coerceIn(0.5f, 2.0f)
    private fun speedToProgress(s: Float)  = ((s - 0.5f) / 0.05f).toInt().coerceIn(0, 30)
    private fun speedLabel(s: Float)       = "${"%.2f".format(s).trimEnd('0').trimEnd('.')}x"
    private fun pitchLabel(semis: Int)     = "${if (semis > 0) "+$semis" else "$semis"} st"

    companion object {
        const val EXTRA_VIDEO_ID       = "video_id"
        const val EXTRA_PLAYLIST_IDS   = "playlist_ids"
        const val EXTRA_PLAYLIST_INDEX = "playlist_index"
    }
}