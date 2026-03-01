package com.cl.vtolive.modules.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Encodes Bitmap frames to H.264/AVC MOV for Apple Live Photo pairing.
 * Uses "Most Compatible" format (H.264) for maximum iPhone/Photos/WeChat/AirDrop support.
 * Paired with key frame HEIC via ContentIdentifier.
 */
public class VideoSegmentEncoder {
    private final Context context;

    public VideoSegmentEncoder(Context context) {
        this.context = context;
    }
    private static final String TAG = "VideoSegmentEncoder";
    private static final int VIDEO_BITRATE = 4_000_000;
    private static final int FPS = 15;
    private static final int I_FRAME_INTERVAL = 1;

    /**
     * Encodes a list of Bitmap frames to MOV (MP4 container with .mov extension).
     *
     * @param frames     Ordered list of frames (will be encoded in sequence)
     * @param outputPath Full path for the output .mov file
     * @return true on success
     */
    public boolean encodeFrames(List<Bitmap> frames, String outputPath) {
        if (frames == null || frames.isEmpty()) {
            Log.e(TAG, "No frames to encode");
            return false;
        }

        Bitmap first = frames.get(0);
        int width = first.getWidth();
        int height = first.getHeight();
        if (width % 2 != 0) width--;
        if (height % 2 != 0) height--;

        MediaCodec encoder = null;
        MediaMuxer muxer = null;
        File tempFile = null;

        try {
            MediaFormat format = MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            format.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

            tempFile = File.createTempFile("lp_", ".mp4", context.getCacheDir());
            muxer = new MediaMuxer(tempFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            int muxerTrack = -1;
            boolean muxerStarted = false;
            long ptsUs = 0;
            long frameDurationUs = 1_000_000L / FPS;

            for (int i = 0; i < frames.size(); i++) {
                Bitmap bmp = frames.get(i);
                byte[] yuv = bitmapToNV12(bmp, width, height);
                if (yuv == null) continue;

                int inputIndex = encoder.dequeueInputBuffer(10000);
                if (inputIndex >= 0) {
                    ByteBuffer inputBuffer = encoder.getInputBuffer(inputIndex);
                    if (inputBuffer != null) {
                        inputBuffer.clear();
                        inputBuffer.put(yuv);
                        boolean isLast = (i == frames.size() - 1);
                        encoder.queueInputBuffer(inputIndex, 0, yuv.length, ptsUs,
                                isLast ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                        ptsUs += frameDurationUs;
                    }
                }

                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                int outputIndex = encoder.dequeueOutputBuffer(info, 10000);
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = encoder.getOutputFormat();
                    muxerTrack = muxer.addTrack(newFormat);
                    muxer.start();
                    muxerStarted = true;
                }
                while (outputIndex >= 0) {
                    if (muxerStarted && info.size > 0 
                            && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        ByteBuffer outputBuffer = encoder.getOutputBuffer(outputIndex);
                        if (outputBuffer != null) {
                            outputBuffer.position(info.offset);
                            outputBuffer.limit(info.offset + info.size);
                            muxer.writeSampleData(muxerTrack, outputBuffer, info);
                        }
                    }
                    encoder.releaseOutputBuffer(outputIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                    outputIndex = encoder.dequeueOutputBuffer(info, 10000);
                }
            }

            encoder.stop();
            encoder.release();
            encoder = null;
            muxer.stop();
            muxer.release();
            muxer = null;

            try (java.io.FileInputStream in = new java.io.FileInputStream(tempFile);
                 FileOutputStream out = new FileOutputStream(outputPath);
                 java.nio.channels.FileChannel inc = in.getChannel();
                 java.nio.channels.FileChannel outc = out.getChannel()) {
                inc.transferTo(0, inc.size(), outc);
            }
            if (tempFile.exists()) tempFile.delete();

            Log.d(TAG, "Encoded " + frames.size() + " frames to " + outputPath);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error encoding video", e);
            return false;
        } finally {
            try {
                if (encoder != null) encoder.release();
                if (muxer != null) muxer.release();
                if (tempFile != null && tempFile.exists()) tempFile.delete();
            } catch (Exception ignored) { }
        }
    }

    /**
     * Converts Bitmap to NV12 (YUV420 semi-planar) for encoder.
     */
    private byte[] bitmapToNV12(Bitmap bitmap, int outWidth, int outHeight) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        Bitmap toUse = bitmap;
        if (w != outWidth || h != outHeight) {
            toUse = Bitmap.createScaledBitmap(bitmap, outWidth, outHeight, true);
        }
        int[] pixels = new int[outWidth * outHeight];
        toUse.getPixels(pixels, 0, outWidth, 0, 0, outWidth, outHeight);
        if (toUse != bitmap) toUse.recycle();

        int ySize = outWidth * outHeight;
        int uvSize = ySize / 2;
        byte[] nv12 = new byte[ySize + uvSize];
        int yIdx = 0;
        int uvIdx = ySize;

        for (int j = 0; j < outHeight; j++) {
            for (int i = 0; i < outWidth; i++) {
                int pixel = pixels[j * outWidth + i];
                int R = (pixel >> 16) & 0xFF;
                int G = (pixel >> 8) & 0xFF;
                int B = pixel & 0xFF;
                int Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                nv12[yIdx++] = (byte) Math.max(0, Math.min(255, Y));
            }
        }
        for (int j = 0; j < outHeight; j += 2) {
            for (int i = 0; i < outWidth; i += 2) {
                int p00 = pixels[j * outWidth + i];
                int p01 = (i + 1 < outWidth) ? pixels[j * outWidth + i + 1] : p00;
                int p10 = (j + 1 < outHeight) ? pixels[(j + 1) * outWidth + i] : p00;
                int p11 = (i + 1 < outWidth && j + 1 < outHeight) 
                        ? pixels[(j + 1) * outWidth + i + 1] : p00;
                int R = ((p00 >> 16 & 0xFF) + (p01 >> 16 & 0xFF) + (p10 >> 16 & 0xFF) + (p11 >> 16 & 0xFF)) / 4;
                int G = ((p00 >> 8 & 0xFF) + (p01 >> 8 & 0xFF) + (p10 >> 8 & 0xFF) + (p11 >> 8 & 0xFF)) / 4;
                int B = ((p00 & 0xFF) + (p01 & 0xFF) + (p10 & 0xFF) + (p11 & 0xFF)) / 4;
                int Cb = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                int Cr = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;
                nv12[uvIdx++] = (byte) Math.max(0, Math.min(255, Cb));
                nv12[uvIdx++] = (byte) Math.max(0, Math.min(255, Cr));
            }
        }
        return nv12;
    }
}
