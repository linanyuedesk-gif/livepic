package com.cl.vlive;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class LivePhotoUtils {

    private static final String TAG = "LivePhotoUtils";
    private static final long MAX_LIVE_PHOTO_DURATION = 3000; // Live Photo最大时长3秒

    /**
     * 剪辑视频到指定长度
     */
    public static File trimVideo(Context context, Uri videoUri, File outputDir, String fileName, long startTime, long endTime) throws IOException {
        // 计算实际剪辑时长，确保不超过3秒
        long duration = endTime - startTime;
        if (duration > MAX_LIVE_PHOTO_DURATION) {
            endTime = startTime + MAX_LIVE_PHOTO_DURATION;
            duration = MAX_LIVE_PHOTO_DURATION;
        }

        // 使用MediaExtractor和MediaMuxer进行视频剪辑
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(context, videoUri, null);

        int videoTrackIndex = -1;
        int audioTrackIndex = -1;

        // 找到视频和音频轨道
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                videoTrackIndex = i;
            } else if (mime.startsWith("audio/")) {
                audioTrackIndex = i;
            }
        }

        if (videoTrackIndex == -1) {
            extractor.release();
            throw new IOException("No video track found");
        }

        // 创建输出文件
        File outputVideo = new File(outputDir, fileName + ".mov");
        MediaMuxer muxer = new MediaMuxer(outputVideo.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        // 选择视频轨道
        extractor.selectTrack(videoTrackIndex);
        MediaFormat videoFormat = extractor.getTrackFormat(videoTrackIndex);
        int videoTrack = muxer.addTrack(videoFormat);

        // 选择音频轨道（如果有）
        int audioTrack = -1;
        if (audioTrackIndex != -1) {
            extractor.selectTrack(audioTrackIndex);
            MediaFormat audioFormat = extractor.getTrackFormat(audioTrackIndex);
            audioTrack = muxer.addTrack(audioFormat);
        }

        // 开始混合
        muxer.start();

        // 定位到开始时间
        extractor.seekTo(startTime * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

        // 处理视频帧
        boolean isVideoDone = false;
        boolean isAudioDone = (audioTrackIndex == -1);

        while (!isVideoDone || !isAudioDone) {
            int trackIndex = extractor.getSampleTrackIndex();
            long sampleTime = extractor.getSampleTime();

            if (sampleTime > endTime * 1000) {
                if (trackIndex == videoTrackIndex) {
                    isVideoDone = true;
                } else if (trackIndex == audioTrackIndex) {
                    isAudioDone = true;
                }
                break;
            }

            if (trackIndex == videoTrackIndex || trackIndex == audioTrackIndex) {
                MediaMuxer.BufferInfo bufferInfo = new MediaMuxer.BufferInfo();
                bufferInfo.offset = 0;
                bufferInfo.size = extractor.readSampleData(new byte[1024 * 1024], 0);
                bufferInfo.presentationTimeUs = sampleTime;
                bufferInfo.flags = extractor.getSampleFlags();

                muxer.writeSampleData(trackIndex == videoTrackIndex ? videoTrack : audioTrack, new byte[1024 * 1024], bufferInfo);
                extractor.advance();
            }
        }

        // 释放资源
        muxer.stop();
        muxer.release();
        extractor.release();

        return outputVideo;
    }

    /**
     * 从视频中提取关键帧作为照片
     */
    public static File extractFrame(Context context, Uri videoUri, File outputDir, String fileName) throws IOException {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(context, videoUri);

        // 提取视频中间帧作为封面
        long duration = Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        long frameTime = duration / 2;

        Bitmap frame = retriever.getFrameAtTime(frameTime * 1000, MediaMetadataRetriever.OPTION_CLOSEST);
        retriever.release();

        if (frame == null) {
            throw new IOException("Failed to extract frame");
        }

        // 保存帧为JPEG文件
        File photoFile = new File(outputDir, fileName + ".jpg");
        FileOutputStream fos = new FileOutputStream(photoFile);
        frame.compress(Bitmap.CompressFormat.JPEG, 90, fos);
        fos.close();

        return photoFile;
    }

    /**
     * 创建符合iOS标准的Live Photo包
     */
    public static File createLivePhotoPackage(File outputDir, String fileName, File photoFile, File videoFile) throws IOException {
        // 创建Live Photo包目录
        File livePhotoDir = new File(outputDir, fileName + ".livephoto");
        if (!livePhotoDir.exists()) {
            livePhotoDir.mkdirs();
        }

        // 复制照片到包内
        File packagePhoto = new File(livePhotoDir, "IMG_" + fileName + ".jpg");
        copyFile(photoFile, packagePhoto);

        // 复制视频到包内
        File packageVideo = new File(livePhotoDir, "IMG_" + fileName + ".mov");
        copyFile(videoFile, packageVideo);

        // 创建Info.plist文件（iOS需要）
        createInfoPlist(livePhotoDir, fileName);

        return livePhotoDir;
    }

    /**
     * 创建Info.plist文件
     */
    private static void createInfoPlist(File livePhotoDir, String fileName) throws IOException {
        String plistContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
                "<plist version=\"1.0\">\n" +
                "<dict>\n" +
                "    <key>CFBundleIdentifier</key>\n" +
                "    <string>com.apple.livephoto</string>\n" +
                "    <key>CFBundleVersion</key>\n" +
                "    <string>1</string>\n" +
                "    <key>LivePhoto</key>\n" +
                "    <dict>\n" +
                "        <key>KeyFrameTime</key>\n" +
                "        <real>0.5</real>\n" +
                "        <key>Duration</key>\n" +
                "        <real>3.0</real>\n" +
                "        <key>MimeTypes</key>\n" +
                "        <dict>\n" +
                "            <key>Photo</key>\n" +
                "            <string>image/jpeg</string>\n" +
                "            <key>Video</key>\n" +
                "            <string>video/quicktime</string>\n" +
                "        </dict>\n" +
                "    </dict>\n" +
                "</dict>\n" +
                "</plist>";

        File infoPlist = new File(livePhotoDir, "Info.plist");
        FileOutputStream fos = new FileOutputStream(infoPlist);
        fos.write(plistContent.getBytes());
        fos.close();
    }

    /**
     * 复制文件
     */
    private static void copyFile(File source, File destination) throws IOException {
        java.io.FileInputStream fis = new java.io.FileInputStream(source);
        java.io.FileOutputStream fos = new java.io.FileOutputStream(destination);
        byte[] buffer = new byte[1024];
        int length;
        while ((length = fis.read(buffer)) > 0) {
            fos.write(buffer, 0, length);
        }
        fis.close();
        fos.close();
    }
}