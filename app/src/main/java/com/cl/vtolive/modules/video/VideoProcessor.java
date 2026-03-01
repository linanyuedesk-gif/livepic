package com.cl.vtolive.modules.video;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Video processor for extracting frames and analyzing video content
 * Handles precise frame extraction and video analysis
 */
public class VideoProcessor {
    private static final String TAG = "VideoProcessor";
    private Context context;
    
    public VideoProcessor(Context context) {
        this.context = context;
    }
    
    /**
     * Video information holder
     */
    public static class VideoInfo {
        public long duration; // milliseconds
        public int width;
        public int height;
        public int rotation;
        public String mimeType;
        public long bitrate;
        
        @Override
        public String toString() {
            return "VideoInfo{duration=" + duration + "ms, " + width + "x" + height + 
                   ", rotation=" + rotation + ", mime=" + mimeType + "}";
        }
    }
    
    /**
     * Gets comprehensive video information
     */
    public VideoInfo getVideoInfo(Uri videoUri) {
        VideoInfo info = new VideoInfo();
        
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(context, videoUri);
            
            // Get duration
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationStr != null) {
                info.duration = Long.parseLong(durationStr);
            }
            
            // Get dimensions
            String widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            if (widthStr != null && heightStr != null) {
                info.width = Integer.parseInt(widthStr);
                info.height = Integer.parseInt(heightStr);
            }
            
            // Get rotation
            String rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if (rotationStr != null) {
                info.rotation = Integer.parseInt(rotationStr);
            }
            
            // Get MIME type
            info.mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            
            // Get bitrate
            String bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            if (bitrateStr != null) {
                info.bitrate = Long.parseLong(bitrateStr);
            }
            
            retriever.release();
            
            Log.d(TAG, "Video info: " + info.toString());
            return info;
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting video info", e);
            return null;
        }
    }
    
    /**
     * Extracts frames at specific timestamps
     */
    public List<FrameInfo> extractFramesAtTimestamps(Uri videoUri, List<Long> timestamps) {
        List<FrameInfo> frames = new ArrayList<>();
        
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(context, videoUri);
            
            for (Long timestamp : timestamps) {
                Bitmap frame = retriever.getFrameAtTime(timestamp * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                
                if (frame != null) {
                    FrameInfo frameInfo = new FrameInfo();
                    frameInfo.timestamp = timestamp;
                    frameInfo.bitmap = frame;
                    frames.add(frameInfo);
                }
            }
            
            retriever.release();
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting frames", e);
        }
        
        Log.d(TAG, "Extracted " + frames.size() + " frames");
        return frames;
    }
    
    /**
     * Extracts key frames (I-frames) from video
     */
    public List<FrameInfo> extractKeyFrames(Uri videoUri, int maxFrames) {
        List<FrameInfo> keyFrames = new ArrayList<>();
        
        try {
            VideoInfo videoInfo = getVideoInfo(videoUri);
            if (videoInfo == null || videoInfo.duration <= 0) {
                return keyFrames;
            }
            
            // Sample frames at regular intervals
            long interval = videoInfo.duration / maxFrames;
            List<Long> timestamps = new ArrayList<>();
            
            for (int i = 0; i < maxFrames; i++) {
                timestamps.add(i * interval);
            }
            
            keyFrames = extractFramesAtTimestamps(videoUri, timestamps);
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting key frames", e);
        }
        
        return keyFrames;
    }
    
    /**
     * Analyzes motion in video segment
     */
    public MotionAnalysis analyzeMotion(Uri videoUri, long startTime, long endTime) {
        MotionAnalysis analysis = new MotionAnalysis();
        
        try {
            // Extract frames for analysis
            int sampleCount = 10; // Sample 10 frames for motion analysis
            long interval = (endTime - startTime) / sampleCount;
            
            List<Long> timestamps = new ArrayList<>();
            for (int i = 0; i < sampleCount; i++) {
                timestamps.add(startTime + (i * interval));
            }
            
            List<FrameInfo> frames = extractFramesAtTimestamps(videoUri, timestamps);
            
            if (frames.size() >= 2) {
                // Simple motion detection by comparing frame differences
                analysis.averageMotion = calculateAverageMotion(frames);
                analysis.frameCount = frames.size();
                analysis.duration = endTime - startTime;
                
                // Determine motion quality
                if (analysis.averageMotion > 0.3) {
                    analysis.quality = MotionQuality.HIGH;
                } else if (analysis.averageMotion > 0.1) {
                    analysis.quality = MotionQuality.MEDIUM;
                } else {
                    analysis.quality = MotionQuality.LOW;
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error analyzing motion", e);
        }
        
        return analysis;
    }
    
    /**
     * Calculates average motion between frames
     * Simple implementation - real implementation would use computer vision
     */
    private double calculateAverageMotion(List<FrameInfo> frames) {
        if (frames.size() < 2) return 0.0;
        
        double totalDifference = 0.0;
        int comparisonCount = 0;
        
        // Compare consecutive frames
        for (int i = 0; i < frames.size() - 1; i++) {
            FrameInfo frame1 = frames.get(i);
            FrameInfo frame2 = frames.get(i + 1);
            
            double difference = compareFrames(frame1.bitmap, frame2.bitmap);
            totalDifference += difference;
            comparisonCount++;
        }
        
        return comparisonCount > 0 ? totalDifference / comparisonCount : 0.0;
    }
    
    /**
     * Compares two frames and returns difference score (0.0 to 1.0)
     * Simple pixel-based comparison
     */
    private double compareFrames(Bitmap frame1, Bitmap frame2) {
        if (frame1 == null || frame2 == null) return 0.0;
        
        // Resize frames to same dimensions for comparison
        int width = Math.min(frame1.getWidth(), frame2.getWidth());
        int height = Math.min(frame1.getHeight(), frame2.getHeight());
        
        if (width <= 0 || height <= 0) return 0.0;
        
        // Sample pixels for comparison (every 10th pixel to improve performance)
        int sampleCount = 0;
        int differentPixels = 0;
        int sampleStep = 10;
        
        for (int y = 0; y < height; y += sampleStep) {
            for (int x = 0; x < width; x += sampleStep) {
                int pixel1 = frame1.getPixel(x, y);
                int pixel2 = frame2.getPixel(x, y);
                
                // Simple RGB difference
                int rDiff = Math.abs((pixel1 >> 16 & 0xFF) - (pixel2 >> 16 & 0xFF));
                int gDiff = Math.abs((pixel1 >> 8 & 0xFF) - (pixel2 >> 8 & 0xFF));
                int bDiff = Math.abs((pixel1 & 0xFF) - (pixel2 & 0xFF));
                
                // If significant difference in any channel
                if (rDiff > 30 || gDiff > 30 || bDiff > 30) {
                    differentPixels++;
                }
                sampleCount++;
            }
        }
        
        return sampleCount > 0 ? (double) differentPixels / sampleCount : 0.0;
    }
    
    /**
     * Frame information holder
     */
    public static class FrameInfo {
        public long timestamp; // milliseconds
        public Bitmap bitmap;
        
        public void recycle() {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
                bitmap = null;
            }
        }
    }
    
    /**
     * Motion analysis results
     */
    public static class MotionAnalysis {
        public double averageMotion; // 0.0 to 1.0
        public int frameCount;
        public long duration; // milliseconds
        public MotionQuality quality;
        
        @Override
        public String toString() {
            return "MotionAnalysis{motion=" + String.format("%.2f", averageMotion) + 
                   ", frames=" + frameCount + ", duration=" + duration + "ms, quality=" + quality + "}";
        }
    }
    
    /**
     * Motion quality levels
     */
    public enum MotionQuality {
        LOW("Low"),
        MEDIUM("Medium"),
        HIGH("High");
        
        private final String displayName;
        
        MotionQuality(String displayName) {
            this.displayName = displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
}