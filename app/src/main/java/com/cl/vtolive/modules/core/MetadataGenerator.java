package com.cl.vtolive.modules.core;

import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Generates Apple Motion Photo metadata for Live Photos
 * Creates XMP metadata compliant with Apple's specifications
 */
public class MetadataGenerator {
    private static final String TAG = "MetadataGenerator";
    
    /**
     * Generates XMP metadata for Apple Motion Photo
     * @param motionPhotoUUID Unique identifier for the motion photo
     * @param duration Duration of motion sequence in milliseconds
     * @param frameCount Number of frames in motion sequence
     * @return XMP metadata string
     */
    public static String generateAppleMotionPhotoMetadata(String motionPhotoUUID, 
                                                         long duration, 
                                                         int frameCount) {
        return generateAppleMotionPhotoMetadata(motionPhotoUUID, duration, frameCount, -1);
    }

    /**
     * @param keyFrameOffsetMs optional time offset within motion clip to mark as
     *                         the still image (relative to start); pass -1 to leave at 0.
     */
    public static String generateAppleMotionPhotoMetadata(String motionPhotoUUID, 
                                                         long duration, 
                                                         int frameCount,
                                                         long keyFrameOffsetMs) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
            String timestamp = dateFormat.format(new Date());
            
            StringBuilder xmp = new StringBuilder();
            xmp.append("<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n");
            xmp.append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"Adobe XMP Core 5.6-c015 81.160520, 2019/09/16-16:00:00\">\n");
            xmp.append(" <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n");
            xmp.append("  <rdf:Description rdf:about=\"\" xmlns:GCamera=\"http://ns.google.com/photos/1.0/camera/\">\n");
            
            // Apple Motion Photo namespace
            xmp.append("   <GCamera:MTPieceInfo>\n");
            xmp.append("    <rdf:Seq>\n");
            xmp.append("     <rdf:li>\n");
            xmp.append("      <rdf:Description>\n");
            xmp.append("       <GCamera:MediaVersion>1</GCamera:MediaVersion>\n");
            xmp.append("       <GCamera:MotionPhoto>1</GCamera:MotionPhoto>\n");
            xmp.append("       <GCamera:MotionPhotoVersion>1</GCamera:MotionPhotoVersion>\n");
            xmp.append("       <GCamera:MotionPhotoPresentationTimestampUs>")
                .append(keyFrameOffsetMs > 0 ? (keyFrameOffsetMs * 1000) : 0)
                .append("</GCamera:MotionPhotoPresentationTimestampUs>\n");
            xmp.append("      </rdf:Description>\n");
            xmp.append("     </rdf:li>\n");
            xmp.append("    </rdf:Seq>\n");
            xmp.append("   </GCamera:MTPieceInfo>\n");
            
            // Motion photo metadata
            xmp.append("   <GCamera:MotionPhoto>1</GCamera:MotionPhoto>\n");
            xmp.append("   <GCamera:MotionPhotoVersion>1</GCamera:MotionPhotoVersion>\n");
            xmp.append("   <GCamera:MotionPhotoPresentationTimestampUs>")
                .append(keyFrameOffsetMs > 0 ? (keyFrameOffsetMs * 1000) : 0)
                .append("</GCamera:MotionPhotoPresentationTimestampUs>\n");
            
            xmp.append("  </rdf:Description>\n");
            
            // Add Apple-specific metadata
            xmp.append("  <rdf:Description rdf:about=\"\" xmlns:apple_desktop=\"http://ns.apple.com/adjustment-settings/1.0/\">\n");
            xmp.append("   <apple_desktop:MotionPhoto>1</apple_desktop:MotionPhoto>\n");
            xmp.append("   <apple_desktop:MotionPhotoUUID>").append(motionPhotoUUID).append("</apple_desktop:MotionPhotoUUID>\n");
            xmp.append("   <apple_desktop:MotionPhotoDuration>").append(duration).append("</apple_desktop:MotionPhotoDuration>\n");
            xmp.append("   <apple_desktop:MotionPhotoFrameCount>").append(frameCount).append("</apple_desktop:MotionPhotoFrameCount>\n");
            xmp.append("   <apple_desktop:MotionPhotoTimestamp>").append(timestamp).append("</apple_desktop:MotionPhotoTimestamp>\n");
            xmp.append("  </rdf:Description>\n");
            
            xmp.append(" </rdf:RDF>\n");
            xmp.append("</x:xmpmeta>\n");
            xmp.append("<?xpacket end=\"w\"?>\n");
            
            Log.d(TAG, "Generated metadata for motion photo: " + motionPhotoUUID);
            return xmp.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating metadata", e);
            return "";
        }
    }
    
    /**
     * Generates a unique UUID for the motion photo
     * @return UUID string
     */
    public static String generateMotionPhotoUUID() {
        return java.util.UUID.randomUUID().toString();
    }
    
    /**
     * Creates HEIF box structure for motion data
     * This is a simplified representation - real implementation
     * would require native HEIF library
     */
    public static byte[] createMotionDataBox(byte[] motionJpegData) {
        // Simplified box structure for demonstration
        // Real implementation would create proper HEIF auxiliary image sequence
        
        try {
            // Create a simple container structure
            byte[] boxHeader = new byte[8];
            int boxSize = 8 + motionJpegData.length;
            
            // Box size (4 bytes)
            boxHeader[0] = (byte) ((boxSize >> 24) & 0xFF);
            boxHeader[1] = (byte) ((boxSize >> 16) & 0xFF);
            boxHeader[2] = (byte) ((boxSize >> 8) & 0xFF);
            boxHeader[3] = (byte) (boxSize & 0xFF);
            
            // Box type "mobj" (motion object)
            boxHeader[4] = 'm';
            boxHeader[5] = 'o';
            boxHeader[6] = 'b';
            boxHeader[7] = 'j';
            
            // Combine header and data
            byte[] result = new byte[boxSize];
            System.arraycopy(boxHeader, 0, result, 0, 8);
            System.arraycopy(motionJpegData, 0, result, 8, motionJpegData.length);
            
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating motion data box", e);
            return new byte[0];
        }
    }
    
    /**
     * Validates metadata parameters
     */
    public static boolean validateMetadataParams(long duration, int frameCount) {
        if (duration <= 0) {
            Log.e(TAG, "Invalid duration: " + duration);
            return false;
        }
        
        if (frameCount <= 0) {
            Log.e(TAG, "Invalid frame count: " + frameCount);
            return false;
        }
        
        // Apple recommendations
        if (duration < 500 || duration > 3500) {
            Log.w(TAG, "Duration outside Apple recommendation (500-3500ms): " + duration);
        }
        
        if (frameCount < 15 || frameCount > 90) {
            Log.w(TAG, "Frame count outside typical range (15-90): " + frameCount);
        }
        
        return true;
    }
}