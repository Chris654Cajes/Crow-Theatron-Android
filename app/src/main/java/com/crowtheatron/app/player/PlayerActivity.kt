package com.crowtheatron.app.player

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.crowtheatron.app.R
import com.crowtheatron.app.data.ChapterMarker
import com.crowtheatron.app.data.EnhancementMode
import com.crowtheatron.app.data.VideoEntity
import com.crowtheatron.app.data.VideoRepository
import com.crowtheatron.app.databinding.ActivityPlayerBinding
import com.crowtheatron.app.profile.PlaybackProfilesActivity
import com.crowtheatron.app.service.PlaybackService
import com.crowtheatron.app.ui.setContentWithCrowInsets
import com.crowtheatron.app.util.FormatUtils
import com.crowtheatron.app.util.VideoEnhancement
import kotlin.math.abs
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
    private var isBindingUi = false
    private var controlsVisible = true
    private var chapters = listOf<ChapterMarker>()

    private var currentSpeed  = 1.0f
    private var currentVolume = 1.0f

    // ── Gesture state ─────────────────────────────────────────────────────────
    private var isGestureSeeking = false
    private var gestureSeekStartPos = 0L
    private var gestureBrightnessStart = 0f
    private var gestureVolumeStart = 0f
    private var currentBrightness = 0.5f // 0..1, affects window brightness

    // ── Scale (pinch zoom) ────────────────────────────────────────────────────
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var currentZoom = 1f

    // ── Service binding ───────────────────────────────────────────────────────
    private var playbackService: PlaybackService? = null
    private var serviceBound = false
    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val lb = binder as PlaybackService.LocalBinder
            playbackService = lb.getService()
            serviceBound = true
            // Player already initialised locally - just register for notifications
        }
        override fun onServiceDisconnected(name: ComponentName) {
            serviceBound = false
            playbackService = null
        }
    }

    // ── Auto-hide controls ────────────────────────────────────────────────────
    private val hideControlsRunnable = Runnable { hideControls() }

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
                isBindingUi = true; bindTrimSeekers(); isBindingUi = false
                tickTimeline()
                applyZoom(working.zoomLevel)
            }
            if (state == Player.STATE_ENDED) {
                if (binding.switchLoop.isChecked) {
                    player?.seekTo(working.trimStartMs.coerceAtLeast(0L)); player?.play()
                } else if (binding.switchAutoNext.isChecked) {
                    playAdjacent(1)
                }
            }
            updatePlayPauseIcon()
            updateServiceNotification()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseIcon()
            if (isPlaying) scheduleHideControls() else showControls()
        }
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
        var ids = intent.getLongArrayExtra(EXTRA_PLAYLIST_IDS)
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
        currentZoom   = working.zoomLevel.coerceIn(1f, 3f)
        currentBrightness = 0.5f

        chapters = repo.listChapters(working.id)

        setupGestures()
        setupEnhancementSpinner()
        bindUiFromWorking()
        setupControls()
        startAndBindService()
        // Init player immediately (service binding may be async)
        initPlayerIfNeeded()
        handler.post(tickRunnable)
    }

    override fun onResume() {
        super.onResume()
        window.navigationBarColor = Color.BLACK
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        persistProgress()
    }

    override fun onStop() {
        persistProgress()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.removeListener(playerListener)
        if (!isInPictureInPictureMode) {
            player?.pause()
        }
        if (serviceBound) {
            unbindService(serviceConn)
            serviceBound = false
        }
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player?.isPlaying == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPiP()
        }
    }

    override fun onPictureInPictureModeChanged(isInPiPMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPiPMode, newConfig)
        if (isInPiPMode) {
            hideControls()
            binding.toolbar.visibility = View.GONE
            binding.controlsOverlay.visibility = View.GONE
        } else {
            binding.toolbar.visibility = View.VISIBLE
            showControls()
        }
    }

    // ── Service ───────────────────────────────────────────────────────────────

    private fun startAndBindService() {
        val intent = Intent(this, PlaybackService::class.java)
        startService(intent)
        bindService(intent, serviceConn, BIND_AUTO_CREATE)
    }

    // ── Player setup ──────────────────────────────────────────────────────────

    private fun initPlayerIfNeeded() {
        if (player != null) return
        val exo = ExoPlayer.Builder(this).build()
        exo.addListener(playerListener)
        player = exo
        binding.playerView.player = exo
        attachCurrentMedia(play = true)
    }

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

    private fun attachCurrentMedia(play: Boolean) {
        val exo = player ?: return
        playerReady = false
        exo.stop()
        exo.setMediaItem(mediaItemFor(working))
        exo.prepare()
        val trimLo = working.trimStartMs.coerceAtLeast(0L)
        val trimHi = if (working.trimEndMs > 0L) working.trimEndMs else Long.MAX_VALUE
        val resumeMs = working.positionMs.coerceIn(trimLo, trimHi)
        exo.seekTo(if (resumeMs > 0L) resumeMs else trimLo)
        applyPitchAndSpeed(working.pitchSemitones, currentSpeed)
        exo.volume = currentVolume * working.audioBoost.coerceIn(1f, 2f)
        exo.repeatMode = if (working.loopPlayback) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        exo.playWhenReady = play
        binding.playerView.postDelayed({ applyEnhancementMatrix() }, 120L)
        updatePlayPauseIcon()
        playbackService?.updateNotification(working.title,
            if (play) "Playing" else "Paused")
    }

    private fun applyPitchAndSpeed(semitones: Int, speed: Float) {
        val pitch = 2.0.pow(semitones / 12.0).toFloat()
        player?.playbackParameters = PlaybackParameters(speed.coerceIn(0.5f, 2.0f), pitch)
    }

    // ── Enhancement ───────────────────────────────────────────────────────────

    private fun applyEnhancementMatrix() {
        val matrix = VideoEnhancement.buildCombinedMatrix(
            mode       = working.enhancement,
            brightness = working.brightness,
            contrast   = working.contrast,
            saturation = working.saturation,
            hue        = working.hue,
        )
        // Apply to PlayerView's TextureView via a Paint
        try {
            val tv = binding.playerView.videoSurfaceView
            if (tv is android.view.TextureView) {
                // Use canvas layer paint for color filtering
                val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
                tv.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
            }
        } catch (_: Exception) {}

        // Simple overlay for ambient color
        val o = binding.enhancementOverlay
        when (working.enhancement) {
            EnhancementMode.NONE            -> o.visibility = View.GONE
            EnhancementMode.WARM_FILM       -> { o.setBackgroundColor(Color.argb(35,255,180,80)); o.visibility = View.VISIBLE }
            EnhancementMode.COOL_HDR_SIM    -> { o.setBackgroundColor(Color.argb(30,80,140,255)); o.visibility = View.VISIBLE }
            EnhancementMode.NIGHT_MODE      -> { o.setBackgroundColor(Color.argb(40,255,80,0)); o.visibility = View.VISIBLE }
            EnhancementMode.EYE_COMFORT     -> { o.setBackgroundColor(Color.argb(30,255,200,50)); o.visibility = View.VISIBLE }
            else                            -> o.visibility = View.GONE
        }
    }

    // ── Zoom ──────────────────────────────────────────────────────────────────

    private fun applyZoom(zoom: Float) {
        val z = zoom.coerceIn(1f, 3f)
        currentZoom = z
        binding.playerView.scaleX = z
        binding.playerView.scaleY = z
    }

    // ── Gestures ──────────────────────────────────────────────────────────────

    private fun setupGestures() {
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newZoom = (currentZoom * detector.scaleFactor).coerceIn(1f, 3f)
                applyZoom(newZoom)
                working = working.copy(zoomLevel = newZoom)
                return true
            }
        })

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleControlsVisibility()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val screenWidth = binding.playerView.width.toFloat()
                val tapX = e.x
                if (tapX < screenWidth / 3f) {
                    // Left third — rewind
                    jumpBy(-10)
                    showGestureHint("⏪ -10s")
                } else if (tapX > screenWidth * 2f / 3f) {
                    // Right third — fast forward
                    jumpBy(+10)
                    showGestureHint("⏩ +10s")
                } else {
                    // Centre — play/pause
                    player?.let { if (it.isPlaying) it.pause() else it.play() }
                }
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distX: Float, distY: Float): Boolean {
                val e1nn = e1 ?: return false
                val screenWidth = binding.playerView.width.toFloat()
                val dx = abs(distX)
                val dy = abs(distY)

                if (dx > dy) {
                    // Horizontal swipe = seek
                    val dur = liveDurationMs()
                    val seekDeltaMs = (-distX / screenWidth * dur * 0.3f).toLong()
                    val exo = player ?: return false
                    val newPos = (exo.currentPosition + seekDeltaMs)
                        .coerceIn(working.trimStartMs.coerceAtLeast(0L),
                            if (working.trimEndMs > 0L) working.trimEndMs else dur)
                    exo.seekTo(newPos)
                    showGestureHint(FormatUtils.formatDuration(newPos))
                } else {
                    // Vertical swipe
                    val swipeZone = e1nn.x / screenWidth
                    if (swipeZone < 0.5f) {
                        // Left half = brightness
                        val delta = -distY / binding.playerView.height
                        currentBrightness = (currentBrightness + delta * 0.5f).coerceIn(0.01f, 1f)
                        val lp = window.attributes
                        lp.screenBrightness = currentBrightness
                        window.attributes = lp
                        showGestureHint("☀ ${(currentBrightness * 100).toInt()}%")
                    } else {
                        // Right half = volume
                        val delta = -distY / binding.playerView.height
                        currentVolume = (currentVolume + delta * 0.5f).coerceIn(0f, 1f)
                        player?.volume = currentVolume * working.audioBoost.coerceIn(1f, 2f)
                        working = working.copy(volumeLevel = currentVolume)
                        binding.seekVolume.progress = (currentVolume * 100).toInt()
                        binding.tvVolumeValue.text = "${(currentVolume * 100).toInt()}%"
                        showGestureHint("🔊 ${(currentVolume * 100).toInt()}%")
                    }
                }
                return true
            }
        })

        binding.playerView.setOnTouchListener { _, event ->
            scaleGestureDetector?.onTouchEvent(event)
            if (scaleGestureDetector?.isInProgress == false) {
                gestureDetector.onTouchEvent(event)
            }
            true
        }
    }

    private fun showGestureHint(text: String) {
        binding.gestureHintText.text = text
        binding.gestureHintOverlay.visibility = View.VISIBLE
        binding.gestureHintOverlay.alpha = 1f
        handler.removeCallbacks(hideGestureHintRunnable)
        handler.postDelayed(hideGestureHintRunnable, 800L)
    }

    private val hideGestureHintRunnable = Runnable {
        binding.gestureHintOverlay.animate()
            .alpha(0f).setDuration(300L)
            .withEndAction { binding.gestureHintOverlay.visibility = View.GONE }
            .start()
    }

    // ── Controls visibility ───────────────────────────────────────────────────

    private fun toggleControlsVisibility() {
        if (controlsVisible) hideControls() else showControls()
    }

    private fun showControls() {
        controlsVisible = true
        binding.controlsOverlay.animate()
            .alpha(1f).setDuration(200L)
            .withStartAction { binding.controlsOverlay.visibility = View.VISIBLE }
            .start()
        binding.toolbar.animate().alpha(1f).setDuration(200L)
            .withStartAction { binding.toolbar.visibility = View.VISIBLE }.start()
        if (player?.isPlaying == true) scheduleHideControls()
    }

    private fun hideControls() {
        if (isFullscreen) {
            controlsVisible = false
            binding.controlsOverlay.animate()
                .alpha(0f).setDuration(300L)
                .withEndAction { binding.controlsOverlay.visibility = View.GONE }
                .start()
            binding.toolbar.animate().alpha(0f).setDuration(300L)
                .withEndAction { binding.toolbar.visibility = View.GONE }.start()
        }
    }

    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 3500L)
    }

    // ── Duration helpers ──────────────────────────────────────────────────────

    private fun liveDurationMs(): Long {
        val d = player?.duration ?: C.TIME_UNSET
        return if (d != C.TIME_UNSET && d > 0L) d else working.durationMs.takeIf { it > 0L } ?: 1L
    }

    private fun metaDurationMs(): Long = working.durationMs.takeIf { it > 0L } ?: 1L

    // ── Timeline ──────────────────────────────────────────────────────────────

    private fun tickTimeline() {
        val exo = player ?: return
        val dur = exo.duration
        if (dur == C.TIME_UNSET || dur <= 0L) return
        val pos = exo.currentPosition
        val progress = ((pos * 1000L) / dur).toInt().coerceIn(0, 1000)
        if (!userScrubbingPlayback) binding.seekPlayback.progress = progress
        updateTimeLabel(pos, dur)
        checkChapterTransitions(pos)
    }

    private fun updateTimeLabel(absPos: Long, dur: Long) {
        val label = "${FormatUtils.formatDuration(absPos)} / ${FormatUtils.formatDuration(dur)}"
        binding.timeLabel.text      = label
        binding.tvPlaybackTime.text = label
    }

    private fun checkChapterTransitions(posMs: Long) {
        // Highlight nearest chapter in timeline — future: show chapter name
    }

    private fun updatePlayPauseIcon() {
        binding.btnPlayPause.setImageResource(
            if (player?.isPlaying == true) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    private fun updateServiceNotification() {
        val state = when {
            player?.isPlaying == true -> "Playing"
            player?.playbackState == Player.STATE_BUFFERING -> "Buffering…"
            else -> "Paused"
        }
        playbackService?.updateNotification(working.title, state)
    }

    // ── UI binding ────────────────────────────────────────────────────────────

    private fun bindUiFromWorking() {
        isBindingUi = true
        binding.toolbar.title   = working.title
        binding.videoTitle.text = working.title
        val semis = working.pitchSemitones.coerceIn(-6, 6)
        binding.seekPitch.progress    = semis + 6
        binding.pitchLabel.text       = pitchLabel(semis)
        binding.seekSpeed.progress    = speedToProgress(currentSpeed)
        binding.tvSpeedValue.text     = speedLabel(currentSpeed)
        binding.seekVolume.progress   = (currentVolume * 100).toInt()
        binding.tvVolumeValue.text    = "${(currentVolume * 100).toInt()}%"
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
        val labels = EnhancementMode.entries.map { it.displayName }
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
                if (isBindingUi) return
                val mode = EnhancementMode.entries.getOrNull(position) ?: return
                working = working.copy(enhancement = mode)
                applyEnhancementMatrix()
                persistPrefs()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ── Controls setup ────────────────────────────────────────────────────────

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
        }
        binding.btnStop.setOnClickListener {
            player?.pause()
            player?.seekTo(working.trimStartMs.coerceAtLeast(0L))
            persistProgress(); tickTimeline()
        }
        binding.btnRewind.setOnClickListener  { jumpBy(-SEEK_JUMP_SECONDS) }
        binding.btnForward.setOnClickListener { jumpBy(SEEK_JUMP_SECONDS)  }
        binding.btnPrev.setOnClickListener    { playAdjacent(-1) }
        binding.btnNext.setOnClickListener    { playAdjacent(1) }
        binding.btnFullscreen.setOnClickListener { toggleFullscreen() }
        binding.btnPip.setOnClickListener { enterPiP() }

        // Profiles button
        binding.btnProfiles.setOnClickListener {
            startActivity(Intent(this, PlaybackProfilesActivity::class.java)
                .putExtra(PlaybackProfilesActivity.EXTRA_VIDEO_ID, working.id))
        }

        // Chapter bookmark
        binding.btnAddChapter.setOnClickListener {
            val pos = player?.currentPosition ?: 0L
            showAddChapterDialog(pos)
        }

        // Playback seekbar
        binding.seekPlayback.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || isBindingUi) return
                val exo = player ?: return
                val dur = liveDurationMs()
                val trimLo  = working.trimStartMs.coerceAtLeast(0L)
                val trimHi  = if (working.trimEndMs > 0L) working.trimEndMs else dur
                val seekTo  = (dur * progress / 1000L).coerceIn(trimLo, trimHi)
                exo.seekTo(seekTo)
                updateTimeLabel(seekTo, dur)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { userScrubbingPlayback = true }
            override fun onStopTrackingTouch(sb: SeekBar?)  {
                userScrubbingPlayback = false; persistProgress()
            }
        })

        // Pitch
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

        // Speed
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

        // Volume
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

        // Trim
        binding.seekTrimStart.setOnSeekBarChangeListener(seekBarListener(
            onStop = { if (!isBindingUi) { readTrimFromSeekBars(); persistPrefs() } }
        ))
        binding.seekTrimEnd.setOnSeekBarChangeListener(seekBarListener(
            onStop = { if (!isBindingUi) { readTrimFromSeekBars(); persistPrefs() } }
        ))

        // Switches
        binding.switchLoop.setOnCheckedChangeListener { _, checked ->
            if (isBindingUi) return@setOnCheckedChangeListener
            working = working.copy(loopPlayback = checked)
            player?.repeatMode = if (checked) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            persistPrefs()
        }
        binding.switchAutoNext.setOnCheckedChangeListener { _, checked ->
            if (isBindingUi) return@setOnCheckedChangeListener
            working = working.copy(autoPlayNext = checked); persistPrefs()
        }

        // Favourite
        binding.btnFavorite.setOnClickListener {
            val next = !working.favorite
            working = working.copy(favorite = next)
            repo.setFavorite(working.id, next)
            binding.btnFavorite.text = getString(
                if (next) R.string.favorite_off else R.string.favorite_on
            )
        }

        // Save prefs
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
            player?.volume = currentVolume * working.audioBoost.coerceIn(1f, 2f)
            player?.repeatMode = if (working.loopPlayback) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            applyEnhancementMatrix()
            Toast.makeText(this, "Saved for this video", Toast.LENGTH_SHORT).show()
        }

        refreshNavButtons()
    }

    // ── Chapter dialog ────────────────────────────────────────────────────────

    private fun showAddChapterDialog(posMs: Long) {
        val input = android.widget.EditText(this).apply {
            hint = "Chapter name"
            setTextColor(Color.WHITE)
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Add chapter at ${FormatUtils.formatDuration(posMs)}")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val label = input.text.toString().trim().ifBlank { "Chapter" }
                repo.addChapter(working.id, posMs, label)
                chapters = repo.listChapters(working.id)
                Toast.makeText(this, "Chapter added: $label", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        player?.volume = currentVolume * working.audioBoost.coerceIn(1f, 2f)
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun persistPrefs() {
        if (working.id <= 0L) return
        repo.savePreferences(working)
    }

    private fun persistProgress() {
        val exo = player ?: return
        if (working.id <= 0L) return
        repo.savePlaybackPosition(working.id, exo.currentPosition.coerceAtLeast(0L))
    }

    // ── Seek & navigation ─────────────────────────────────────────────────────

    private fun jumpBy(deltaSec: Int) {
        val exo = player ?: return
        val dur = liveDurationMs()
        val trimLo = working.trimStartMs.coerceAtLeast(0L)
        val trimHi = if (working.trimEndMs > 0L) working.trimEndMs else dur
        val newPos = (exo.currentPosition + deltaSec * 1000L).coerceIn(trimLo, trimHi)
        exo.seekTo(newPos)
        persistProgress(); tickTimeline()
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
        currentZoom   = working.zoomLevel.coerceIn(1f, 3f)
        chapters      = repo.listChapters(working.id)
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

    // ── Fullscreen & PiP ─────────────────────────────────────────────────────

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            supportActionBar?.hide()
            binding.playerSurface.layoutParams.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            scheduleHideControls()
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            supportActionBar?.show()
            binding.playerSurface.layoutParams.height = (220 * resources.displayMetrics.density).toInt()
            showControls()
        }
        binding.playerSurface.requestLayout()
    }

    private fun enterPiP() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val rational = Rational(16, 9)
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(rational)
            .build()
        enterPictureInPictureMode(params)
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun showAndFinish(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); finish()
    }

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
        private const val SEEK_JUMP_SECONDS   = 10
        const val EXTRA_VIDEO_ID              = "video_id"
        const val EXTRA_PLAYLIST_IDS          = "playlist_ids"
        const val EXTRA_PLAYLIST_INDEX        = "playlist_index"
    }
}
