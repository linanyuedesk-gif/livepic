package com.cl.vlive

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cl.vlive.databinding.ActivityMainBinding
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedVideoUri: Uri? = null
    private var durationMs: Long = 0L
    private var lastExportUris: ArrayList<Uri> = arrayListOf()

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedVideoUri = uri
            binding.videoView.setVideoURI(uri)
            binding.videoView.setOnPreparedListener { mp ->
                durationMs = mp.duration.toLong()
                updateRangeText()
                mp.isLooping = true
                binding.videoView.start()
            }
            binding.videoLabel.text = uri.toString()
            binding.exportButton.isEnabled = true
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // no-op: continue with scoped storage even if partial denied
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ensurePermissions()
        initRangeSeekBars()

        binding.pickButton.setOnClickListener {
            pickVideoLauncher.launch("video/*")
        }

        binding.exportButton.setOnClickListener {
            val uri = selectedVideoUri
            if (uri == null || durationMs <= 0) {
                toast("请先选择可用视频")
                return@setOnClickListener
            }
            exportLivePhoto(uri)
        }

        binding.shareButton.setOnClickListener {
            if (lastExportUris.isEmpty()) {
                toast("暂无可分享文件")
                return@setOnClickListener
            }
            shareLastExport()
        }

        updateRangeText()
    }

    private fun ensurePermissions() {
        val required = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            required += Manifest.permission.READ_MEDIA_VIDEO
            required += Manifest.permission.READ_MEDIA_IMAGES
        } else {
            required += Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun initRangeSeekBars() {
        binding.startSeek.progress = 0
        binding.endSeek.progress = 1000

        binding.startSeek.setOnSeekBarChangeListener(rangeListener)
        binding.endSeek.setOnSeekBarChangeListener(rangeListener)
    }

    private val rangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (!fromUser) return

            var start = binding.startSeek.progress
            var end = binding.endSeek.progress

            if (start >= end) {
                if (seekBar?.id == binding.startSeek.id) {
                    start = (end - 10).coerceAtLeast(0)
                    binding.startSeek.progress = start
                } else {
                    end = (start + 10).coerceAtMost(1000)
                    binding.endSeek.progress = end
                }
            }

            if (durationMs > 0L) {
                val minGapProgress = ((1500f / durationMs) * 1000f).toInt().coerceAtLeast(1)
                val maxGapProgress = ((3000f / durationMs) * 1000f).toInt().coerceAtLeast(minGapProgress)
                val gap = end - start

                if (gap < minGapProgress) {
                    if (seekBar?.id == binding.startSeek.id) {
                        start = (end - minGapProgress).coerceAtLeast(0)
                        binding.startSeek.progress = start
                    } else {
                        end = (start + minGapProgress).coerceAtMost(1000)
                        binding.endSeek.progress = end
                    }
                } else if (gap > maxGapProgress) {
                    if (seekBar?.id == binding.startSeek.id) {
                        start = (end - maxGapProgress).coerceAtLeast(0)
                        binding.startSeek.progress = start
                    } else {
                        end = (start + maxGapProgress).coerceAtMost(1000)
                        binding.endSeek.progress = end
                    }
                }
            }

            updateRangeText()
            seekPreview()
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun seekPreview() {
        if (durationMs <= 0) return
        val startMs = progressToMs(binding.startSeek.progress)
        binding.videoView.seekTo(startMs.toInt())
    }

    private fun progressToMs(progress: Int): Long {
        return (durationMs * progress / 1000f).toLong()
    }

    private fun currentRange(): Pair<Long, Long> {
        val start = progressToMs(binding.startSeek.progress)
        val end = progressToMs(binding.endSeek.progress)
        return start to end
    }

    private fun updateRangeText() {
        val (start, end) = currentRange()
        binding.rangeText.text = "${formatMs(start)} - ${formatMs(end)}"
    }

    private fun formatMs(ms: Long): String {
        val total = ms / 1000
        val m = total / 60
        val s = total % 60
        val msPart = (ms % 1000) / 10
        return String.format("%02d:%02d.%02d", m, s, msPart)
    }

    private fun exportLivePhoto(videoUri: Uri) {
        val (startMs, endMs) = currentRange()
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.exportButton.isEnabled = false
        binding.shareButton.isEnabled = false

        thread {
            try {
                val exporter = LivePhotoExporter(this)
                val artifacts = exporter.export(videoUri, startMs, endMs)

                val imageUri = MediaStoreSaver.saveImage(this, artifacts.imageFile, artifacts.baseName)
                val videoUriOut = MediaStoreSaver.saveVideo(this, artifacts.videoFile, artifacts.baseName)
                lastExportUris = arrayListOf(imageUri, videoUriOut)

                runOnUiThread {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.exportButton.isEnabled = true
                    binding.shareButton.isEnabled = true
                    toast("导出成功：${artifacts.baseName}.JPG + ${artifacts.baseName}.MOV")
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.exportButton.isEnabled = true
                    toast("导出失败: ${t.message}")
                }
            }
        }
    }

    private fun shareLastExport() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, lastExportUris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "分享 Live Photo 文件"))
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}

