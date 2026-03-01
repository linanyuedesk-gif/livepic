package com.cl.vtolive.activities;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cl.vtolive.R;
import com.cl.vtolive.modules.ui.TimelineView;
import com.cl.vtolive.modules.video.VideoProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Second page: Interval selection with timeline view
 */
public class IntervalSelectionActivity extends AppCompatActivity {
    private static final String TAG = "IntervalSelectionActivity";
    public static final String EXTRA_VIDEO_URI = "VIDEO_URI";
    public static final String EXTRA_START_TIME = "START_TIME";
    public static final String EXTRA_END_TIME = "END_TIME";
    
    private TimelineView timelineView;
    private TextView tvVideoInfo;
    private TextView tvIntervalInfo;
    private Button btnConfirm;
    private Button btnCancel;
    private Button btnPreview;
    
    private Uri videoUri;
    private VideoProcessor videoProcessor;
    private VideoProcessor.VideoInfo videoInfo;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_interval_selection);
        
        initViews();
        initData();
        setupListeners();
        
        if (videoUri != null) {
            loadVideoInfo();
        }
    }
    
    private void initViews() {
        timelineView = findViewById(R.id.timelineView);
        tvVideoInfo = findViewById(R.id.tvVideoInfo);
        tvIntervalInfo = findViewById(R.id.tvIntervalInfo);
        btnConfirm = findViewById(R.id.btnConfirm);
        btnCancel = findViewById(R.id.btnCancel);
        btnPreview = findViewById(R.id.btnPreview);
    }
    
    private void initData() {
        videoUri = getIntent().getParcelableExtra(EXTRA_VIDEO_URI);
        videoProcessor = new VideoProcessor(this);
        
        // Set initial interval (2-5 seconds is good for Live Photos)
        long startTime = 2000;
        long endTime = 5000;
        timelineView.setInterval(startTime, endTime);
    }
    
    private static final int PREVIEW_REQUEST_CODE = 2001;

    private long selectedKeyFrameTime = -1;

    private void setupListeners() {
        timelineView.setOnIntervalChangeListener(new TimelineView.OnIntervalChangeListener() {
            @Override
            public void onIntervalChanged(long startTime, long endTime) {
                updateIntervalInfo();
            }
        });
        
        btnConfirm.setOnClickListener(v -> confirmSelection());
        btnCancel.setOnClickListener(v -> finish());
        btnPreview.setOnClickListener(v -> previewSelection());
    }
    
    private void loadVideoInfo() {
        new Thread(() -> {
            try {
                videoInfo = videoProcessor.getVideoInfo(videoUri);
                if (videoInfo != null) {
                    runOnUiThread(() -> {
                        updateVideoInfo();
                        loadThumbnails();
                    });
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to load video information", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading video info", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading video", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }).start();
    }
    
    private void updateVideoInfo() {
        if (videoInfo != null) {
            String info = String.format("Video: %dx%d, %s\nDuration: %s",
                videoInfo.width, videoInfo.height, videoInfo.mimeType,
                formatDuration(videoInfo.duration));
            tvVideoInfo.setText(info);
            
            timelineView.setTotalDuration(videoInfo.duration);
            updateIntervalInfo();
        }
    }
    
    private void updateIntervalInfo() {
        long startTime = timelineView.getStartTime();
        long endTime = timelineView.getEndTime();
        long duration = timelineView.getDuration();
        
        String info = String.format("Selected: %s - %s\nDuration: %s%s",
            formatTime(startTime), formatTime(endTime), formatDuration(duration),
            validateInterval(duration) ? "" : "\n⚠️ Recommended: 1.5-3 seconds");
        
        tvIntervalInfo.setText(info);
        
        // Update button states
        boolean isValid = validateInterval(duration);
        btnConfirm.setEnabled(isValid);
        btnPreview.setEnabled(duration > 0);
    }
    
    private void loadThumbnails() {
        new Thread(() -> {
            try {
                // Extract thumbnails for timeline
                int thumbnailCount = 8;
                long interval = videoInfo.duration / (thumbnailCount + 1);
                
                List<Long> timestamps = new ArrayList<>();
                for (int i = 1; i <= thumbnailCount; i++) {
                    timestamps.add(i * interval);
                }
                
                List<VideoProcessor.FrameInfo> frames = videoProcessor
                    .extractFramesAtTimestamps(videoUri, timestamps);
                
                List<TimelineView.ThumbnailInfo> thumbnails = new ArrayList<>();
                for (VideoProcessor.FrameInfo frameInfo : frames) {
                    // Scale down for thumbnails
                    Bitmap thumbnail = Bitmap.createScaledBitmap(
                        frameInfo.bitmap, 80, 60, true);
                    thumbnails.add(new TimelineView.ThumbnailInfo(
                        frameInfo.timestamp, thumbnail));
                    frameInfo.recycle();
                }
                
                runOnUiThread(() -> timelineView.setThumbnails(thumbnails));
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading thumbnails", e);
            }
        }).start();
    }
    
    private void previewSelection() {
        long startTime = timelineView.getStartTime();
        long endTime = timelineView.getEndTime();
        
        Intent intent = new Intent(this, PreviewActivity.class);
        intent.putExtra(EXTRA_VIDEO_URI, videoUri);
        intent.putExtra(EXTRA_START_TIME, startTime);
        intent.putExtra(EXTRA_END_TIME, endTime);
        startActivityForResult(intent, PREVIEW_REQUEST_CODE);
    }
    
    private void confirmSelection() {
        Intent result = new Intent();
        result.putExtra(EXTRA_START_TIME, timelineView.getStartTime());
        result.putExtra(EXTRA_END_TIME, timelineView.getEndTime());
        setResult(Activity.RESULT_OK, result);
        
        // Navigate to export page
        Intent intent = new Intent(this, ExportActivity.class);
        intent.putExtra(EXTRA_VIDEO_URI, videoUri);
        intent.putExtra(EXTRA_START_TIME, timelineView.getStartTime());
        intent.putExtra(EXTRA_END_TIME, timelineView.getEndTime());
        if (selectedKeyFrameTime >= 0) {
            intent.putExtra("KEY_FRAME_TIME", selectedKeyFrameTime);
        }
        startActivity(intent);
        
        finish();
    }
    
    private boolean validateInterval(long duration) {
        // Apple recommends 1.5-3 seconds for Live Photos
        return duration >= 1500 && duration <= 3500;
    }
    
    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    private String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        if (seconds < 60) {
            return seconds + "s";
        } else {
            long minutes = seconds / 60;
            seconds = seconds % 60;
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PREVIEW_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            selectedKeyFrameTime = data.getLongExtra("KEY_FRAME_TIME", -1);
            if (selectedKeyFrameTime >= 0) {
                Toast.makeText(this, "Key frame chosen at " +
                        formatTime(selectedKeyFrameTime - timelineView.getStartTime()),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}