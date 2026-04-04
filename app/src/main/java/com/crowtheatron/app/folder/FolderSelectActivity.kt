package com.crowtheatron.app.folder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.crowtheatron.app.data.FolderScanner
import com.crowtheatron.app.data.VideoRepository
import com.crowtheatron.app.databinding.ActivityFolderSelectBinding
import com.crowtheatron.app.ui.setContentWithCrowInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FolderSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFolderSelectBinding
    private val repo by lazy { VideoRepository(this) }

    private val openTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) {
            finish()
            return@registerForActivityResult
        }
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            Toast.makeText(this, "Could not keep folder access.", Toast.LENGTH_LONG).show()
            finish()
            return@registerForActivityResult
        }
        scanAndImport(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFolderSelectBinding.inflate(layoutInflater)
        setContentWithCrowInsets(binding.root)

        binding.btnChoose.setOnClickListener { openTree.launch(null) }
    }

    private fun scanAndImport(treeUri: Uri) {
        binding.progress.visibility = View.VISIBLE
        binding.status.text = getString(com.crowtheatron.app.R.string.scanning)
        binding.btnChoose.isEnabled = false
        lifecycleScope.launch {
            val videos = withContext(Dispatchers.IO) {
                FolderScanner.scanTreeUri(this@FolderSelectActivity, treeUri)
            }
            if (videos.isEmpty()) {
                binding.progress.visibility = View.GONE
                binding.btnChoose.isEnabled = true
                binding.status.text = "No video files found in that folder."
                Toast.makeText(this@FolderSelectActivity, binding.status.text, Toast.LENGTH_SHORT).show()
                return@launch
            }
            withContext(Dispatchers.IO) {
                repo.importScanResults(videos, withThumbnails = true)
            }
            Toast.makeText(
                this@FolderSelectActivity,
                "Imported ${videos.size} videos",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }
}
