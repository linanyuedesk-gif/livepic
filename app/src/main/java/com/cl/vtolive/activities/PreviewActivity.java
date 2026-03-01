package com.cl.vtolive.activities;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cl.vtolive.R;
import com.cl.vtolive.modules.video.VideoProcessor;
import com.cl.vtolive.utils.FlowExtras;
import com.cl.vtolive.utils.TimeFormatUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Preview selected interval and choose key frame. Third page of the flow.
 */
public class PreviewActivity extends AppCompatActivity {

    private static final String TAG = "Preview";

    private ImageView ivPreview;
    private SeekBar seekBarPreview;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private Button btnPlayPause;
    private Button btnBack;
    private Button btnUseThisFrame;

    private Uri videoUri;
    private long startTime;
    private long endTime;
    private VideoProcessor videoProcessor;
    private long currentTime;

    private boolean isPlaying;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);
        bindViews();
        readExtras();
        currentTime = startTime;
        setupListeners();
        tvTotalTime.setText(TimeFormatUtils.formatTime(endTime - startTime));
        loadFrameAt(currentTime);
        updateDisplay();
    }

    private void bindViews() {
        ivPreview = findViewById(R.id.ivPreview);
        seekBarPreview = findViewById(R.id.seekBarPreview);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnBack = findViewById(R.id.btnBack);
        btnUseThisFrame = findViewById(R.id.btnUseThisFrame);
    }

    private void readExtras() {
        videoUri = FlowExtras.getVideoUri(getIntent());
        startTime = FlowExtras.getStartTime(getIntent(), 0);
        endTime = FlowExtras.getEndTime(getIntent(), 0);
        videoProcessor = new VideoProcessor(this);
    }

    private void setupListeners() {
        seekBarPreview.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentTime = startTime + (long) progress * (endTime - startTime) / 100;
                    updateDisplay();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (isPlaying) pausePlayback();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        btnPlayPause.setOnClickListener(v -> togglePlayback());
        btnBack.setOnClickListener(v -> finish());
        btnUseThisFrame.setOnClickListener(v -> useCurrentFrameAsKey());
    }

    private void loadFrameAt(long timestamp) {
        if (videoUri == null) return;
        new Thread(() -> {
            try {
                List<VideoProcessor.FrameInfo> frames = videoProcessor.extractFramesAtTimestamps(videoUri, Arrays.asList(timestamp));
                if (frames == null || frames.isEmpty()) return;
                VideoProcessor.FrameInfo fi = frames.get(0);
                runOnUiThread(() -> {
                    if (fi != null && fi.bitmap != null && !fi.bitmap.isRecycled()) {
                        ivPreview.setImageBitmap(fi.bitmap);
                    }
                    if (fi != null) fi.recycle();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading frame", e);
            }
        }).start();
    }

    private void togglePlayback() {
        if (isPlaying) pausePlayback(); else startPlayback();
    }

    private void startPlayback() {
        isPlaying = true;
        btnPlayPause.setText("⏸️");
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPlaying) return;
                currentTime += 100;
                if (currentTime >= endTime) currentTime = startTime;
                updateDisplay();
                loadFrameAt(currentTime);
                handler.postDelayed(this, 100);
            }
        };
        handler.post(updateRunnable);
    }

    private void pausePlayback() {
        isPlaying = false;
        btnPlayPause.setText("▶️");
        if (updateRunnable != null) handler.removeCallbacks(updateRunnable);
    }

    private void updateDisplay() {
        int progress = (endTime > startTime) ? (int) ((currentTime - startTime) * 100 / (endTime - startTime)) : 0;
        seekBarPreview.setProgress(progress);
        tvCurrentTime.setText(TimeFormatUtils.formatTime(currentTime - startTime));
    }

    private void useCurrentFrameAsKey() {
        setResult(Activity.RESULT_OK, FlowExtras.resultWithKeyFrameTime(currentTime));
        Toast.makeText(this, "Key frame: " + TimeFormatUtils.formatTime(currentTime - startTime), Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isPlaying) pausePlayback();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isPlaying) pausePlayback();
    }
}
