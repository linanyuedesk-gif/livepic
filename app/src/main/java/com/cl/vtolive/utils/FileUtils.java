package com.cl.vtolive.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Utility class for file operations
 */
public class FileUtils {
    private static final String TAG = "FileUtils";
    
    /**
     * Copies file from URI to local storage
     */
    public static File copyUriToFile(Context context, Uri uri, String destinationPath) {
        try {
            ContentResolver resolver = context.getContentResolver();
            InputStream inputStream = resolver.openInputStream(uri);
            
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream for URI: " + uri);
                return null;
            }
            
            File destinationFile = new File(destinationPath);
            OutputStream outputStream = new FileOutputStream(destinationFile);
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            inputStream.close();
            outputStream.close();
            
            Log.d(TAG, "File copied successfully to: " + destinationPath);
            return destinationFile;
            
        } catch (Exception e) {
            Log.e(TAG, "Error copying file from URI", e);
            return null;
        }
    }
    
    /**
     * Gets file name from URI
     */
    public static String getFileName(Context context, Uri uri) {
        String fileName = null;
        
        try {
            ContentResolver resolver = context.getContentResolver();
            Cursor cursor = resolver.query(uri, null, null, null, null);
            
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex);
                }
                cursor.close();
            }
            
            // Fallback to last path segment
            if (fileName == null) {
                fileName = uri.getLastPathSegment();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting file name", e);
            fileName = "unknown_file";
        }
        
        return fileName != null ? fileName : "unknown_file";
    }
    
    /**
     * Gets file size from URI
     */
    public static long getFileSize(Context context, Uri uri) {
        try {
            ContentResolver resolver = context.getContentResolver();
            Cursor cursor = resolver.query(uri, null, null, null, null);
            
            if (cursor != null) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0 && cursor.moveToFirst()) {
                    long size = cursor.getLong(sizeIndex);
                    cursor.close();
                    return size;
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting file size", e);
        }
        
        return -1;
    }
    
    /**
     * Deletes file safely
     */
    public static boolean deleteFile(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                boolean deleted = file.delete();
                Log.d(TAG, "File deleted: " + filePath + " - " + deleted);
                return deleted;
            }
            return true; // File doesn't exist, consider as successful
        } catch (Exception e) {
            Log.e(TAG, "Error deleting file: " + filePath, e);
            return false;
        }
    }
    
    /**
     * Checks if file exists
     */
    public static boolean fileExists(String filePath) {
        try {
            File file = new File(filePath);
            return file.exists();
        } catch (Exception e) {
            Log.e(TAG, "Error checking file existence", e);
            return false;
        }
    }
    
    /**
     * Gets human-readable file size
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}