package com.cl.vtolive.modules.export;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import com.cl.vtolive.modules.core.LivePhotoEncoder;
import com.cl.vtolive.modules.core.MetadataGenerator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Manages Live Photo export and system integration
 * Handles file creation, gallery integration, and sharing
 */
public class ExportManager {
    private static final String TAG = "ExportManager";
    private Context context;
    private LivePhotoEncoder encoder;
    
    public ExportManager(Context context) {
        this.context = context;
        this.encoder = new LivePhotoEncoder(context);
    }
    
    /**
     * Export result holder
     */
    public static class ExportResult {
        public boolean success;
        public String filePath;
        public String errorMessage;
        public Uri mediaStoreUri;
        
        public ExportResult(boolean success, String filePath, String errorMessage) {
            this.success = success;
            this.filePath = filePath;
            this.errorMessage = errorMessage;
        }
    }
    
    /**
     * Exports Live Photo to system gallery
     */
    /**
     * Export with optional key frame timestamp (pass -1 to use midpoint)
     */
    public ExportResult exportToGallery(Uri videoUri, long startTime, long endTime, long keyFrameTime) {
        try {
            Log.d(TAG, "Exporting Live Photo: " + startTime + "ms to " + endTime + "ms (key=" + keyFrameTime + ")");
            
            // Validate parameters
            if (!validateExportParameters(videoUri, startTime, endTime)) {
                return new ExportResult(false, null, "Invalid export parameters");
            }
            
            // Generate unique filename
            String filename = generateFilename();
            String filePath = getOutputPath(filename);
            
            // Create Live Photo
            boolean encoded = encoder.createLivePhoto(videoUri, startTime, endTime, filePath, keyFrameTime);
            if (!encoded) {
                return new ExportResult(false, null, "Failed to encode Live Photo");
            }
            
            // Add to MediaStore
            Uri mediaStoreUri = addToMediaStore(filename, filePath);
            
            // Scan for media availability
            scanMediaFile(filePath);
            
            Log.d(TAG, "Live Photo exported successfully: " + filePath);
            return new ExportResult(true, filePath, null);
            
        } catch (Exception e) {
            Log.e(TAG, "Error exporting Live Photo", e);
            return new ExportResult(false, null, e.getMessage());
        }
    }
    
    /**
     * Exports with progress callback
     */
    public void exportToGalleryAsync(Uri videoUri, long startTime, long endTime, long keyFrameTime,
                                   ExportCallback callback) {
        new Thread(() -> {
            try {
                callback.onProgress(0, "Starting export...");
                
                // Validate parameters
                if (!validateExportParameters(videoUri, startTime, endTime)) {
                    callback.onError("Invalid export parameters");
                    return;
                }
                
                callback.onProgress(20, "Generating filename...");
                String filename = generateFilename();
                String filePath = getOutputPath(filename);
                
                callback.onProgress(40, "Encoding Live Photo...");
                boolean encoded = encoder.createLivePhoto(videoUri, startTime, endTime, filePath, keyFrameTime);
                if (!encoded) {
                    callback.onError("Failed to encode Live Photo");
                    return;
                }
                
                callback.onProgress(80, "Adding to gallery...");
                Uri mediaStoreUri = addToMediaStore(filename, filePath);
                
                callback.onProgress(90, "Scanning media...");
                scanMediaFile(filePath);
                
                callback.onProgress(100, "Complete!");
                callback.onSuccess(filePath, mediaStoreUri);
                
            } catch (Exception e) {
                Log.e(TAG, "Error in async export", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }
    
    /**
     * Shares Live Photo file
     */
    public void shareLivePhoto(String filePath, ShareCallback callback) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                callback.onError("File not found: " + filePath);
                return;
            }
            
            // Create content URI for sharing
            Uri contentUri = createShareUri(file);
            callback.onShareUriReady(contentUri);
            
        } catch (Exception e) {
            Log.e(TAG, "Error sharing Live Photo", e);
            callback.onError(e.getMessage());
        }
    }
    
    /**
     * Gets export statistics
     */
    public ExportStats getExportStats() {
        ExportStats stats = new ExportStats();
        
        try {
            File outputDir = new File(getBaseOutputPath());
            if (outputDir.exists() && outputDir.isDirectory()) {
                File[] files = outputDir.listFiles((dir, name) -> 
                    name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".heic"));
                
                if (files != null) {
                    stats.totalExports = files.length;
                    long totalSize = 0;
                    for (File file : files) {
                        totalSize += file.length();
                    }
                    stats.totalSizeBytes = totalSize;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting export stats", e);
        }
        
        return stats;
    }
    
    // Private helper methods
    private boolean validateExportParameters(Uri videoUri, long startTime, long endTime) {
        // Check if video URI is valid
        if (videoUri == null) {
            Log.e(TAG, "Invalid video URI");
            return false;
        }
        
        // Validate time parameters
        return startTime >= 0 && endTime > startTime;
    }
    
       private String generateFilename() {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        // use .heic extension to signal proper container
        return "LivePhoto_" + uuid + "_" + System.currentTimeMillis() + ".heic";
    }
    
    private String getBaseOutputPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) 
               + "/LivePhotos";
    }
    
    private String getOutputPath(String filename) {
        File dir = new File(getBaseOutputPath());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, filename).getAbsolutePath();
    }
    
    private Uri addToMediaStore(String displayName, String filePath) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
            // HEIC container created by native encoder
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/heic");
            values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
            values.put(MediaStore.Images.Media.DATA, filePath);
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LivePhotos");
            
            ContentResolver resolver = context.getContentResolver();
            return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            
        } catch (Exception e) {
            Log.e(TAG, "Error adding to MediaStore", e);
            return null;
        }
    }
    
    private void scanMediaFile(String filePath) {
        try {
            MediaScannerConnection.scanFile(context, new String[]{filePath}, null, 
                (path, uri) -> Log.d(TAG, "Media scan completed: " + path));
        } catch (Exception e) {
            Log.e(TAG, "Error scanning media file", e);
        }
    }
    
    private Uri createShareUri(File file) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, file.getName());
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            
            ContentResolver resolver = context.getContentResolver();
            Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri item = resolver.insert(collection, values);
            
            if (item != null) {
                try (OutputStream out = resolver.openOutputStream(item)) {
                    if (out != null) {
                        // Copy file content to the new URI
                        java.nio.file.Files.copy(
                            java.nio.file.Paths.get(file.getAbsolutePath()),
                            out);
                    }
                }
                
                // Mark as completed
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(item, values, null, null);
            }
            
            return item;
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating share URI", e);
            return null;
        }
    }
    
    // Interfaces
    public interface ExportCallback {
        void onProgress(int percentage, String message);
        void onSuccess(String filePath, Uri mediaStoreUri);
        void onError(String error);
    }
    
    public interface ShareCallback {
        void onShareUriReady(Uri shareUri);
        void onError(String error);
    }
    
    // Data classes
    public static class ExportStats {
        public int totalExports = 0;
        public long totalSizeBytes = 0;
        
        public String getTotalSizeFormatted() {
            if (totalSizeBytes < 1024) {
                return totalSizeBytes + " B";
            } else if (totalSizeBytes < 1024 * 1024) {
                return String.format("%.1f KB", totalSizeBytes / 1024.0);
            } else if (totalSizeBytes < 1024 * 1024 * 1024) {
                return String.format("%.1f MB", totalSizeBytes / (1024.0 * 1024.0));
            } else {
                return String.format("%.1f GB", totalSizeBytes / (1024.0 * 1024.0 * 1024.0));
            }
        }
        
        @Override
        public String toString() {
            return "ExportStats{exports=" + totalExports + ", size=" + getTotalSizeFormatted() + "}";
        }
    }
}