package com.cl.vtolive.modules.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cl.vtolive.R;
import com.cl.vtolive.modules.video.VideoProcessor;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity for selecting video interval for Live Photo creation
 * Provides timeline view and preview functionality
 */
public class IntervalSelectorActivity extends AppCompatActivity {
    private static final String TAG = "IntervalSelectorActivity";
    public static final String EXTRA_VIDEO_URI = "video_uri";
    public static final String EXTRA_START_TIME = "start_time";
    public static final String EXTRA_END_TIME = "end_time";
    public static final String RESULT_START_TIME = "result_start_time";
    public static final String RESULT_END_TIME = "result_end_time";
    
    private TimelineView timelineView;
    private TextView tvVideoInfo;
    private TextView tvIntervalInfo;
    private Button btnConfirm;
    private Button btnCancel;
    
    private Uri videoUri;
    private VideoProcessor videoProcessor;
    private VideoProcessor.VideoInfo videoInfo;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_interval_selector);
        
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
    }
    
    private void initData() {
        videoUri = getIntent().getParcelableExtra(EXTRA_VIDEO_URI);
        videoProcessor = new VideoProcessor(this);
        
        // Set initial interval if provided
        long startTime = getIntent().getLongExtra(EXTRA_START_TIME, 2000);
        long endTime = getIntent().getLongExtra(EXTRA_END_TIME, 5000);
        timelineView.setInterval(startTime, endTime);
    }
    
    private void setupListeners() {
        timelineView.setOnIntervalChangeListener(new TimelineView.OnIntervalChangeListener() {
            @Override
            public void onIntervalChanged(long startTime, long endTime) {
                updateIntervalInfo();
            }
        });
        
        btnConfirm.setOnClickListener(v -> confirmSelection());
        btnCancel.setOnClickListener(v -> finish());
    }
    
    private void loadVideoInfo() {
        new LoadVideoInfoTask(this).execute(videoUri);
    }
    
    private void updateVideoInfo() {
        if (videoInfo != null) {
            String info = String.format("Video: %dx%d, %s, Duration: %s",
                videoInfo.width, videoInfo.height, videoInfo.mimeType,
                formatDuration(videoInfo.duration));
            tvVideoInfo.setText(info);
            
            timelineView.setTotalDuration(videoInfo.duration);
            updateIntervalInfo();
            
            // Load thumbnails
            loadThumbnails();
        }
    }
    
    private void updateIntervalInfo() {
        long startTime = timelineView.getStartTime();
        long endTime = timelineView.getEndTime();
        long duration = timelineView.getDuration();
        
        String info = String.format("Selected: %s - %s (%s)",
            formatTime(startTime), formatTime(endTime), formatDuration(duration));
        tvIntervalInfo.setText(info);
        
        // Validate interval
        boolean isValid = validateInterval(duration);
        btnConfirm.setEnabled(isValid);
        
        if (!isValid) {
            tvIntervalInfo.setTextColor(getResources().getColor(android.R.color.holo_orange_light));
        } else {
            tvIntervalInfo.setTextColor(getResources().getColor(android.R.color.white));
        }
    }
    
    private void loadThumbnails() {
        new LoadThumbnailsTask(this).execute();
    }
    
    private void confirmSelection() {
        Intent result = new Intent();
        result.putExtra(RESULT_START_TIME, timelineView.getStartTime());
        result.putExtra(RESULT_END_TIME, timelineView.getEndTime());
        setResult(Activity.RESULT_OK, result);
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
    
    // Async Tasks
    private static class LoadVideoInfoTask extends AsyncTask<Uri, Void, VideoProcessor.VideoInfo> {
        private WeakReference<IntervalSelectorActivity> activityRef;
        
        LoadVideoInfoTask(IntervalSelectorActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }
        
        @Override
        protected VideoProcessor.VideoInfo doInBackground(Uri... uris) {
            IntervalSelectorActivity activity = activityRef.get();
            if (activity == null || activity.isFinishing()) return null;
            
            return activity.videoProcessor.getVideoInfo(uris[0]);
        }
        
        @Override
        protected void onPostExecute(VideoProcessor.VideoInfo videoInfo) {
            IntervalSelectorActivity activity = activityRef.get();
            if (activity == null || activity.isFinishing()) return;
            
            activity.videoInfo = videoInfo;
            if (videoInfo != null) {
                activity.updateVideoInfo();
            } else {
                Toast.makeText(activity, "Failed to load video information", Toast.LENGTH_SHORT).show();
                activity.finish();
            }
        }
    }
    
    private static class LoadThumbnailsTask extends AsyncTask<Void, Void, List<TimelineView.ThumbnailInfo>> {
        private WeakReference<IntervalSelectorActivity> activityRef;
        
        LoadThumbnailsTask(IntervalSelectorActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }
        
        @Override
        protected List<TimelineView.ThumbnailInfo> doInBackground(Void... voids) {
            IntervalSelectorActivity activity = activityRef.get();
            if (activity == null || activity.isFinishing() || activity.videoInfo == null) {
                return new ArrayList<>();
            }
            
            List<TimelineView.ThumbnailInfo> thumbnails = new ArrayList<>();
            
            try {
                // Extract key frames for thumbnails
                int thumbnailCount = 8;
                long interval = activity.videoInfo.duration / (thumbnailCount + 1);
                
                List<Long> timestamps = new ArrayList<>();
                for (int i = 1; i <= thumbnailCount; i++) {
                    timestamps.add(i * interval);
                }
                
                List<VideoProcessor.FrameInfo> frames = activity.videoProcessor
                    .extractFramesAtTimestamps(activity.videoUri, timestamps);
                
                for (VideoProcessor.FrameInfo frameInfo : frames) {
                    // Scale down bitmap for thumbnail
                    Bitmap thumbnail = Bitmap.createScaledBitmap(
                        frameInfo.bitmap, 80, 60, true);
                    thumbnails.add(new TimelineView.ThumbnailInfo(
                        frameInfo.timestamp, thumbnail));
                    frameInfo.recycle(); // Recycle original frame
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading thumbnails", e);
            }
            
            return thumbnails;
        }
        
        @Override
        protected void onPostExecute(List<TimelineView.ThumbnailInfo> thumbnails) {
            IntervalSelectorActivity activity = activityRef.get();
            if (activity == null || activity.isFinishing()) return;
            
            activity.timelineView.setThumbnails(thumbnails);
        }
    }
}