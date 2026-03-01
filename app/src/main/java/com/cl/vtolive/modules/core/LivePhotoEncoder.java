package com.cl.vtolive.modules.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;

import com.cl.vtolive.modules.core.MetadataGenerator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Core Live Photo encoder that creates Apple-compatible Live Photos
 * Generates HEIC containers with embedded motion data
 */
public class LivePhotoEncoder {
    private static final String TAG = "LivePhotoEncoder";

    static {
        System.loadLibrary("native-lib");
    }

    private static native boolean nativeEncodeHeif(String keyPath,
                                                   String[] motionPaths,
                                                   String outPath,
                                                   String xmp);
    private static final int TARGET_WIDTH = 1920;
    private static final int TARGET_HEIGHT = 1080;
    private static final int FPS = 15; // Frames per second for motion sequence
    
    private Context context;
    
    public LivePhotoEncoder(Context context) {
        this.context = context;
    }
    
    /**
     * Creates a Live Photo from video interval
     * @param videoUri Source video URI
     * @param startTime Start time in milliseconds
     * @param endTime End time in milliseconds
     * @param outputPath Output file path for the Live Photo
     * @return True if successful, false otherwise
     */
    /**
     * Convenience wrapper using middle frame as key photo
     */
    public boolean createLivePhoto(Uri videoUri, long startTime, long endTime, String outputPath) {
        return createLivePhoto(videoUri, startTime, endTime, outputPath, -1);
    }

    /**
     * Creates a Live Photo from video interval
     * @param videoUri Source video URI
     * @param startTime Start time in milliseconds
     * @param endTime End time in milliseconds
     * @param outputPath Output file path for the Live Photo
     * @param keyFrameTime Specific timestamp to use as key photo (within interval) or -1
     * @return True if successful, false otherwise
     */
    public boolean createLivePhoto(Uri videoUri, long startTime, long endTime, String outputPath,
                                   long keyFrameTime) {
        try {
            Log.d(TAG, "Creating Live Photo from " + startTime + "ms to " + endTime + "ms");
            
            // Extract key photo (use provided timestamp if valid)
            Bitmap keyPhoto = extractKeyPhoto(videoUri, startTime, endTime, keyFrameTime);
            if (keyPhoto == null) {
                Log.e(TAG, "Failed to extract key photo");
                return false;
            }
            
            // Extract motion frames
            List<Bitmap> motionFrames = extractMotionFrames(videoUri, startTime, endTime);
            if (motionFrames.isEmpty()) {
                Log.e(TAG, "Failed to extract motion frames");
                keyPhoto.recycle();
                return false;
            }
            
            // compute key frame offset relative to interval start
            long offset = (keyFrameTime >= startTime && keyFrameTime <= endTime)
                          ? keyFrameTime - startTime : 0;
            // Create HEIC container with motion data and metadata
            boolean success = createHeicContainer(keyPhoto, motionFrames, outputPath,
                                                  endTime - startTime, motionFrames.size(),
                                                  offset);
            
            // Clean up resources
            keyPhoto.recycle();
            for (Bitmap frame : motionFrames) {
                frame.recycle();
            }
            
            return success;
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating Live Photo", e);
            return false;
        }
    }
    
    /**
     * Extracts the key photo (still image) from the middle of the interval
     */
    /**
     * Extracts the key photo (still image) from the middle of the interval
     * or from a specified timestamp if valid.
     */
    public Bitmap extractKeyPhoto(Uri videoUri, long startTime, long endTime) {
        return extractKeyPhoto(videoUri, startTime, endTime, -1);
    }

    public Bitmap extractKeyPhoto(Uri videoUri, long startTime, long endTime, long keyFrameTime) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(context, videoUri);
            
            long timestamp;
            if (keyFrameTime >= startTime && keyFrameTime <= endTime) {
                timestamp = keyFrameTime;
            } else {
                timestamp = (startTime + endTime) / 2;
            }
            Bitmap frame = retriever.getFrameAtTime(timestamp * 1000, 
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            
            retriever.release();
            
            if (frame != null) {
                // Resize to target dimensions while maintaining aspect ratio
                return resizeBitmap(frame, TARGET_WIDTH, TARGET_HEIGHT);
            }
            
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error extracting key photo", e);
            return null;
        }
    }
    
    /**
     * Extracts motion frames for the Live Photo sequence
     */
    private List<Bitmap> extractMotionFrames(Uri videoUri, long startTime, long endTime) {
        List<Bitmap> frames = new ArrayList<>();
        long duration = endTime - startTime;
        
        // Apple recommends 1.5-3 seconds for Live Photos
        if (duration < 1500) {
            Log.w(TAG, "Interval too short for optimal Live Photo");
        } else if (duration > 3000) {
            Log.w(TAG, "Interval too long, will be truncated to 3 seconds");
            endTime = startTime + 3000;
            duration = 3000;
        }
        
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(context, videoUri);
            
            // Extract frames at target FPS
            long frameInterval = 1000 / FPS; // milliseconds between frames
            int frameCount = (int) (duration / frameInterval);
            
            // Limit to reasonable number of frames
            frameCount = Math.min(frameCount, 60); // Max 60 frames
            
            for (int i = 0; i < frameCount; i++) {
                long timestamp = startTime + (i * frameInterval);
                Bitmap frame = retriever.getFrameAtTime(timestamp * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                
                if (frame != null) {
                    Bitmap resizedFrame = resizeBitmap(frame, TARGET_WIDTH / 2, TARGET_HEIGHT / 2);
                    frames.add(resizedFrame);
                    frame.recycle(); // Recycle original frame
                }
            }
            
            retriever.release();
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting motion frames", e);
        }
        
        Log.d(TAG, "Extracted " + frames.size() + " motion frames");
        return frames;
    }
    
    /**
     * Creates HEIC container with embedded motion data
     * Note: This is a simplified implementation - real HEIC creation
     * would require native libraries or external tools
     */
    private boolean createHeicContainer(Bitmap keyPhoto, List<Bitmap> motionFrames, String outputPath,
                                         long durationMs, int frameCount, long keyFrameOffsetMs) {
        try {
            // write temporary files for key and motion frames
            File cacheDir = context.getCacheDir();
            File keyFile = new File(cacheDir, "key.jpg");
            try (FileOutputStream fos = new FileOutputStream(keyFile)) {
                keyPhoto.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            }

            String[] motionPaths = new String[motionFrames.size()];
            for (int i = 0; i < motionFrames.size(); i++) {
                File mf = new File(cacheDir, "motion_" + i + ".jpg");
                try (FileOutputStream fos = new FileOutputStream(mf)) {
                    motionFrames.get(i).compress(Bitmap.CompressFormat.JPEG, 80, fos);
                }
                motionPaths[i] = mf.getAbsolutePath();
            }

            String uuid = MetadataGenerator.generateMotionPhotoUUID();
            String xmp = MetadataGenerator.generateAppleMotionPhotoMetadata(uuid, durationMs, frameCount, keyFrameOffsetMs);

            boolean ok = nativeEncodeHeif(keyFile.getAbsolutePath(), motionPaths, outputPath, xmp);
            if (!ok) {
                Log.e(TAG, "nativeEncodeHeif failed");
                return false;
            }

            Log.d(TAG, "Live Photo saved to: " + outputPath + " (with metadata uuid=" + uuid + ")");
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error creating HEIC container", e);
            return false;
        }
    }

    /**
     * Appends XMP metadata string to the end of a JPEG file.  This is a
     * rudimentary approach and not fully compliant with the JPEG spec, but
     * works for simple proof‑of‑concept files.
     */
    private void appendXmpMetadata(File jpegFile, String xmp) throws IOException {
        if (xmp == null || xmp.isEmpty()) return;
        try (FileOutputStream fos = new FileOutputStream(jpegFile, true)) {
            fos.write(xmp.getBytes("UTF-8"));
        }
    }
    
    /**
     * Resizes bitmap while maintaining aspect ratio
     */
    private Bitmap resizeBitmap(Bitmap original, int targetWidth, int targetHeight) {
        float originalWidth = original.getWidth();
        float originalHeight = original.getHeight();
        
        float scaleX = (float) targetWidth / originalWidth;
        float scaleY = (float) targetHeight / originalHeight;
        float scale = Math.min(scaleX, scaleY);
        
        int newWidth = Math.round(originalWidth * scale);
        int newHeight = Math.round(originalHeight * scale);
        
        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
    }
    
    /**
     * Validates Live Photo parameters
     */
    public boolean validateParameters(long startTime, long endTime, long videoDuration) {
        if (startTime >= endTime) {
            Log.e(TAG, "Start time must be before end time");
            return false;
        }
        
        if (startTime < 0 || endTime > videoDuration) {
            Log.e(TAG, "Time interval out of video bounds");
            return false;
        }
        
        long duration = endTime - startTime;
        if (duration < 500) { // Minimum 0.5 seconds
            Log.e(TAG, "Interval too short (minimum 500ms)");
            return false;
        }
        
        return true;
    }
}