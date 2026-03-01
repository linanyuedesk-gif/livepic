package com.cl.vlive

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ExportArtifacts(
    val imageFile: File,
    val videoFile: File,
    val assetId: String,
    val baseName: String
)

class LivePhotoExporter(private val context: Context) {

    fun export(videoUri: Uri, startMs: Long, endMs: Long): ExportArtifacts {
        require(endMs > startMs) { "无效片段" }

        val workDir = File(context.cacheDir, "vlive_${System.currentTimeMillis()}").apply { mkdirs() }
        val baseName = "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val assetId = UUID.randomUUID().toString().uppercase(Locale.US)

        val stillFile = File(workDir, "$baseName.JPG")
        val motionFile = File(workDir, "$baseName.MOV")

        extractStillFrame(videoUri, (startMs + endMs) / 2, stillFile)
        trimVideo(videoUri, startMs, endMs, motionFile)
        writePhotoMetadata(stillFile, assetId)

        return ExportArtifacts(
            imageFile = stillFile,
            videoFile = motionFile,
            assetId = assetId,
            baseName = baseName
        )
    }

    private fun extractStillFrame(videoUri: Uri, frameMs: Long, outFile: File) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val frameUs = frameMs * 1000
            val bitmap = retriever.getFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: throw IllegalStateException("无法抽取封面帧")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
            }
            bitmap.recycle()
        } finally {
            retriever.release()
        }
    }

    private fun trimVideo(videoUri: Uri, startMs: Long, endMs: Long, outFile: File) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, videoUri, null)
            val mediaMuxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mediaMuxer

            val indexMap = mutableMapOf<Int, Int>()
            var videoRotation = 0
            var maxBufferSize = 2 * 1024 * 1024

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue

                extractor.selectTrack(i)
                val dstIndex = mediaMuxer.addTrack(format)
                indexMap[i] = dstIndex

                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    maxBufferSize = maxOf(maxBufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
                if (mime.startsWith("video/") && format.containsKey(MediaFormat.KEY_ROTATION)) {
                    videoRotation = format.getInteger(MediaFormat.KEY_ROTATION)
                }
            }

            if (indexMap.isEmpty()) {
                throw IllegalStateException("视频中未找到可导出的音视频轨道")
            }

            if (videoRotation != 0) {
                mediaMuxer.setOrientationHint(videoRotation)
            }

            val startUs = startMs * 1000
            val endUs = endMs * 1000
            val buffer = ByteBuffer.allocate(maxBufferSize)
            val info = MediaMuxer.BufferInfo()

            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            mediaMuxer.start()

            while (true) {
                val sampleTrackIndex = extractor.sampleTrackIndex
                if (sampleTrackIndex < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < startUs) {
                    extractor.advance()
                    continue
                }
                if (sampleTimeUs > endUs) {
                    break
                }

                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break

                val dstTrack = indexMap[sampleTrackIndex]
                if (dstTrack != null) {
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = sampleTimeUs - startUs
                    info.flags = extractor.sampleFlags
                    mediaMuxer.writeSampleData(dstTrack, buffer, info)
                }
                extractor.advance()
            }
        } finally {
            try {
                muxer?.stop()
            } catch (_: Throwable) {
            }
            try {
                muxer?.release()
            } catch (_: Throwable) {
            }
            extractor.release()
        }
    }

    private fun writePhotoMetadata(photoFile: File, assetId: String) {
        val exif = ExifInterface(photoFile)
        val xmpPacket = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description xmlns:AppleLivePhoto="http://apple.com/live-photo/1.0/" AppleLivePhoto:ContentIdentifier="$assetId"/>
              </rdf:RDF>
            </x:xmpmeta>
        """.trimIndent()

        exif.setAttribute(ExifInterface.TAG_IMAGE_UNIQUE_ID, assetId)
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, "ContentIdentifier=$assetId")
        exif.setAttribute(ExifInterface.TAG_XMP, xmpPacket)
        exif.saveAttributes()
    }
}

