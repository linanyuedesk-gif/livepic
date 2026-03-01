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
import com.cl.vtolive.utils.FlowExtras;
import com.cl.vtolive.utils.LivePhotoConstants;
import com.cl.vtolive.utils.TimeFormatUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Interval selection with timeline. Second page of the flow.
 */
public class IntervalSelectionActivity extends AppCompatActivity {

    private static final String TAG = "IntervalSelection";

    private TimelineView timelineView;
    private TextView tvVideoInfo;
    private TextView tvIntervalInfo;
    private Button btnConfirm;
    private Button btnCancel;
    private Button btnPreview;

    private Uri videoUri;
    private VideoProcessor videoProcessor;
    private VideoProcessor.VideoInfo videoInfo;
    private long selectedKeyFrameTime = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_interval_selection);
        bindViews();
        readExtras();
        timelineView.setInterval(LivePhotoConstants.DEFAULT_INTERVAL_START_MS, LivePhotoConstants.DEFAULT_INTERVAL_END_MS);
        setupListeners();
        if (videoUri != null) {
            loadVideoInfo();
        }
    }

    private void bindViews() {
        timelineView = findViewById(R.id.timelineView);
        tvVideoInfo = findViewById(R.id.tvVideoInfo);
        tvIntervalInfo = findViewById(R.id.tvIntervalInfo);
        btnConfirm = findViewById(R.id.btnConfirm);
        btnCancel = findViewById(R.id.btnCancel);
        btnPreview = findViewById(R.id.btnPreview);
    }

    private void readExtras() {
        videoUri = FlowExtras.getVideoUri(getIntent());
        videoProcessor = new VideoProcessor(this);
    }

    private void setupListeners() {
        timelineView.setOnIntervalChangeListener((startTime, endTime) -> updateIntervalInfo());
        btnConfirm.setOnClickListener(v -> confirmSelection());
        btnCancel.setOnClickListener(v -> finish());
        btnPreview.setOnClickListener(v -> openPreview());
    }

    private void loadVideoInfo() {
        new Thread(() -> {
            try {
                videoInfo = videoProcessor.getVideoInfo(videoUri);
                runOnUiThread(() -> {
                    if (videoInfo != null) {
                        updateVideoInfo();
                        loadThumbnails();
                    } else {
                        Toast.makeText(this, "Failed to load video information", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
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
        if (videoInfo == null) return;
        tvVideoInfo.setText(String.format("Video: %dx%d, %s\nDuration: %s",
                videoInfo.width, videoInfo.height, videoInfo.mimeType,
                TimeFormatUtils.formatDuration(videoInfo.duration)));
        timelineView.setTotalDuration(videoInfo.duration);
        updateIntervalInfo();
    }

    private void updateIntervalInfo() {
        long start = timelineView.getStartTime();
        long end = timelineView.getEndTime();
        long duration = timelineView.getDuration();
        String warning = LivePhotoConstants.isDurationInRange(duration) ? "" : "\n⚠️ Recommended: 1.5-3 seconds";
        tvIntervalInfo.setText(String.format("Selected: %s - %s\nDuration: %s%s",
                TimeFormatUtils.formatTime(start), TimeFormatUtils.formatTime(end),
                TimeFormatUtils.formatDuration(duration), warning));
        btnConfirm.setEnabled(LivePhotoConstants.isDurationInRange(duration));
        btnPreview.setEnabled(duration > 0);
    }

    private void loadThumbnails() {
        if (videoInfo == null) return;
        new Thread(() -> {
            try {
                int count = 8;
                long step = videoInfo.duration / (count + 1);
                List<Long> timestamps = new ArrayList<>();
                for (int i = 1; i <= count; i++) timestamps.add(i * step);
                List<VideoProcessor.FrameInfo> frames = videoProcessor.extractFramesAtTimestamps(videoUri, timestamps);
                List<TimelineView.ThumbnailInfo> thumbnails = new ArrayList<>();
                for (VideoProcessor.FrameInfo fi : frames) {
                    Bitmap thumb = Bitmap.createScaledBitmap(fi.bitmap, 80, 60, true);
                    thumbnails.add(new TimelineView.ThumbnailInfo(fi.timestamp, thumb));
                    fi.recycle();
                }
                runOnUiThread(() -> timelineView.setThumbnails(thumbnails));
            } catch (Exception e) {
                Log.e(TAG, "Error loading thumbnails", e);
            }
        }).start();
    }

    private void openPreview() {
        Intent i = FlowExtras.intentForPreview(this, videoUri, timelineView.getStartTime(), timelineView.getEndTime());
        startActivityForResult(i, FlowExtras.REQ_PREVIEW);
    }

    private void confirmSelection() {
        setResult(Activity.RESULT_OK, new Intent()
                .putExtra(FlowExtras.START_TIME, timelineView.getStartTime())
                .putExtra(FlowExtras.END_TIME, timelineView.getEndTime()));
        Intent i = FlowExtras.intentForExport(this, videoUri,
                timelineView.getStartTime(), timelineView.getEndTime(), selectedKeyFrameTime);
        startActivity(i);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FlowExtras.REQ_PREVIEW && resultCode == Activity.RESULT_OK && data != null) {
            selectedKeyFrameTime = FlowExtras.getKeyFrameTime(data, -1);
            if (selectedKeyFrameTime >= 0) {
                Toast.makeText(this, "Key frame at " +
                        TimeFormatUtils.formatTime(selectedKeyFrameTime - timelineView.getStartTime()), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
