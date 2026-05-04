package com.crowtheatron.app.enhancement

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.crowtheatron.app.data.EnhancementMode
import com.crowtheatron.app.databinding.ItemEnhancementRowBinding

class EnhancementListAdapter(
    private val onPick: (EnhancementMode) -> Unit,
) : RecyclerView.Adapter<EnhancementListAdapter.VH>() {

    private val rows: List<Pair<EnhancementMode, String>> = listOf(
        EnhancementMode.NONE            to "Neutral picture — no color matrix applied.",
        EnhancementMode.VIVID_HD        to "Higher saturation and gentle contrast for a crisp, vivid look.",
        EnhancementMode.CINEMA_CONTRAST to "Deeper shadows and brighter highlights for a cinematic grade.",
        EnhancementMode.WARM_FILM       to "Warm highlights reminiscent of print film stocks.",
        EnhancementMode.COOL_HDR_SIM    to "Cooler shadows with boosted blues for an HDR-style pop.",
        EnhancementMode.AMOLED          to "Crushed blacks and high contrast optimised for OLED screens.",
        EnhancementMode.NIGHT_MODE      to "Dimmed overall brightness for comfortable night viewing.",
        EnhancementMode.ANIME           to "Boosted saturation and cooler tones for animated content.",
        EnhancementMode.EYE_COMFORT     to "Reduced blue light with warm tones for extended viewing sessions.",
        EnhancementMode.VIVID_OUTDOOR   to "Extra brightness and saturation for viewing in bright environments.",
        EnhancementMode.CINEMATIC_DARK  to "Cross-processed cinematic look with lifted shadows and colour shift.",
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemEnhancementRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (mode, desc) = rows[position]
        holder.bind(mode, desc, onPick)
    }

    class VH(private val b: ItemEnhancementRowBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(mode: EnhancementMode, desc: String, onPick: (EnhancementMode) -> Unit) {
            b.modeTitle.text = mode.displayName
            b.modeDesc.text  = desc
            b.root.setOnClickListener { onPick(mode) }
        }
    }
}
