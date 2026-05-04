package com.crowtheatron.app.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.crowtheatron.app.R
import com.crowtheatron.app.data.PlaybackProfile
import com.crowtheatron.app.data.VideoRepository
import com.crowtheatron.app.ui.setContentWithCrowInsets

/**
 * Full-screen activity for managing named playback profiles for a single video.
 * Profiles let users save and instantly recall complete sets of playback preferences:
 * speed, pitch, enhancement, EQ, subtitle settings, trim, zoom, etc.
 */
class PlaybackProfilesActivity : AppCompatActivity() {

    private val repo by lazy { VideoRepository(this) }
    private var videoId = 0L
    private var profiles = mutableListOf<PlaybackProfile>()
    private lateinit var recycler: RecyclerView
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        videoId = intent.getLongExtra(EXTRA_VIDEO_ID, 0L)
        if (videoId == 0L) { finish(); return }

        // Build a minimal layout programmatically so no XML is needed
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(resources.getColor(R.color.crow_bg, theme))
        }

        val toolbar = com.google.android.material.appbar.MaterialToolbar(this).apply {
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            title = "Playback Profiles"
            setTitleTextColor(resources.getColor(R.color.crow_accent_yellow, theme))
            setBackgroundColor(resources.getColor(R.color.crow_surface, theme))
            setNavigationIconTint(resources.getColor(R.color.crow_accent_yellow, theme))
            setNavigationOnClickListener { finish() }
        }
        root.addView(toolbar, android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // "New Profile" button
        val btnNew = com.google.android.material.button.MaterialButton(this).apply {
            text = "＋ New Profile"
            setTextColor(resources.getColor(R.color.crow_bg, theme))
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                resources.getColor(R.color.crow_accent_yellow, theme))
            setOnClickListener { showCreateDialog() }
        }
        val btnParams = android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(48, 32, 48, 8)
        }
        root.addView(btnNew, btnParams)

        emptyText = TextView(this).apply {
            text = "No profiles yet. Create one to save your current settings."
            setTextColor(resources.getColor(R.color.crow_on_muted, theme))
            textSize = 14f
            setPadding(48, 32, 48, 16)
            visibility = View.GONE
        }
        root.addView(emptyText, android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@PlaybackProfilesActivity)
        }
        root.addView(recycler, android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentWithCrowInsets(root)
        loadProfiles()
    }

    private fun loadProfiles() {
        profiles = repo.listProfilesForVideo(videoId).toMutableList()
        if (profiles.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            recycler.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            recycler.visibility = View.VISIBLE
        }
        recycler.adapter = ProfileAdapter(profiles,
            onActivate  = { p -> activateProfile(p) },
            onDuplicate = { p -> showDuplicateDialog(p) },
            onRename    = { p -> showRenameDialog(p) },
            onDelete    = { p -> confirmDelete(p) }
        )
    }

    private fun showCreateDialog() {
        val input = EditText(this).apply {
            hint = "Profile name (e.g. Cinema, Night, Anime)"
            setTextColor(android.graphics.Color.WHITE)
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("New Profile")
            .setMessage("This will save the video's CURRENT settings as a new profile.")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim().ifBlank { "Profile ${profiles.size + 1}" }
                val video = repo.getById(videoId) ?: return@setPositiveButton
                val profile = PlaybackProfile(
                    videoId                = videoId,
                    name                   = name,
                    isDefault              = profiles.isEmpty(),
                    playbackSpeed          = video.playbackSpeed,
                    volumeLevel            = video.volumeLevel,
                    audioBoost             = video.audioBoost,
                    eqPreset               = video.eqPreset,
                    loopPlayback           = video.loopPlayback,
                    autoPlayNext           = video.autoPlayNext,
                    pitchSemitones         = video.pitchSemitones,
                    trimStartMs            = video.trimStartMs,
                    trimEndMs              = video.trimEndMs,
                    enhancement            = video.enhancement,
                    brightness             = video.brightness,
                    contrast               = video.contrast,
                    saturation             = video.saturation,
                    hue                    = video.hue,
                    sharpness              = video.sharpness,
                    zoomLevel              = video.zoomLevel,
                    cropMode               = video.cropMode,
                    subtitleTrackIndex     = video.subtitleTrackIndex,
                    subtitleOffsetMs       = video.subtitleOffsetMs,
                    subtitleSizeSp         = video.subtitleSizeSp,
                    subtitleBold           = video.subtitleBold,
                    subtitleBackgroundAlpha = video.subtitleBackgroundAlpha,
                )
                repo.createProfile(profile)
                loadProfiles()
                Toast.makeText(this, "Profile \"$name\" created", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDuplicateDialog(profile: PlaybackProfile) {
        val input = EditText(this).apply {
            setText("${profile.name} (copy)")
            setTextColor(android.graphics.Color.WHITE)
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Duplicate Profile")
            .setView(input)
            .setPositiveButton("Duplicate") { _, _ ->
                val name = input.text.toString().trim().ifBlank { "${profile.name} (copy)" }
                repo.duplicateProfile(profile.id, name)
                loadProfiles()
                Toast.makeText(this, "Duplicated as \"$name\"", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameDialog(profile: PlaybackProfile) {
        val input = EditText(this).apply {
            setText(profile.name)
            setTextColor(android.graphics.Color.WHITE)
            setSingleLine(true)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle("Rename Profile")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val name = input.text.toString().trim().ifBlank { profile.name }
                repo.updateProfile(profile.copy(name = name))
                loadProfiles()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(profile: PlaybackProfile) {
        AlertDialog.Builder(this)
            .setTitle("Delete \"${profile.name}\"?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                repo.deleteProfile(profile.id)
                loadProfiles()
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun activateProfile(profile: PlaybackProfile) {
        val video = repo.getById(videoId) ?: return
        val updated = repo.applyProfileToEntity(video, profile)
        repo.savePreferences(updated)
        Toast.makeText(this, "\"${profile.name}\" activated — reopen video to apply", Toast.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_VIDEO_ID = "video_id"
    }
}

// ── RecyclerView adapter for profiles ────────────────────────────────────────

private class ProfileAdapter(
    private val profiles: List<PlaybackProfile>,
    private val onActivate: (PlaybackProfile) -> Unit,
    private val onDuplicate: (PlaybackProfile) -> Unit,
    private val onRename: (PlaybackProfile) -> Unit,
    private val onDelete: (PlaybackProfile) -> Unit,
) : RecyclerView.Adapter<ProfileAdapter.VH>() {

    inner class VH(val root: View) : RecyclerView.ViewHolder(root) {
        val name     = root.findViewWithTag<TextView>("name")
        val badge    = root.findViewWithTag<TextView>("badge")
        val btnAct   = root.findViewWithTag<com.google.android.material.button.MaterialButton>("activate")
        val btnDup   = root.findViewWithTag<com.google.android.material.button.MaterialButton>("dup")
        val btnRen   = root.findViewWithTag<com.google.android.material.button.MaterialButton>("ren")
        val btnDel   = root.findViewWithTag<com.google.android.material.button.MaterialButton>("del")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val root = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            setBackgroundColor(ctx.resources.getColor(R.color.crow_surface_elevated, ctx.theme))
            val lp = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(24, 8, 24, 8)
            layoutParams = lp
        }

        val headerRow = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val nameView = TextView(ctx).apply {
            tag = "name"
            textSize = 16f
            setTextColor(ctx.resources.getColor(R.color.crow_on_bg, ctx.theme))
            layoutParams = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val badgeView = TextView(ctx).apply {
            tag = "badge"
            textSize = 11f
            setTextColor(ctx.resources.getColor(R.color.crow_accent_yellow, ctx.theme))
            setPadding(12, 4, 12, 4)
        }
        headerRow.addView(nameView)
        headerRow.addView(badgeView)
        root.addView(headerRow)

        val btnRow = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }
        fun mkBtn(tag: String, label: String, colorRes: Int): com.google.android.material.button.MaterialButton {
            return com.google.android.material.button.MaterialButton(ctx,
                null, android.R.attr.borderlessButtonStyle).apply {
                this.tag = tag
                text = label
                textSize = 11f
                setTextColor(ctx.resources.getColor(colorRes, ctx.theme))
                setPadding(0, 0, 24, 0)
                minWidth = 0
                minHeight = 0
            }
        }
        btnRow.addView(mkBtn("activate", "▶ Activate",  R.color.crow_accent_green))
        btnRow.addView(mkBtn("dup",      "⧉ Duplicate", R.color.crow_accent_cyan))
        btnRow.addView(mkBtn("ren",      "✎ Rename",    R.color.crow_accent_yellow))
        btnRow.addView(mkBtn("del",      "✕ Delete",    R.color.crow_accent_red))
        root.addView(btnRow)

        return VH(root)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = profiles[position]
        holder.name.text  = p.name
        holder.badge.text = if (p.isDefault) "DEFAULT" else ""
        holder.btnAct.setOnClickListener { onActivate(p) }
        holder.btnDup.setOnClickListener { onDuplicate(p) }
        holder.btnRen.setOnClickListener { onRename(p) }
        holder.btnDel.setOnClickListener { onDelete(p) }
    }

    override fun getItemCount() = profiles.size
}
