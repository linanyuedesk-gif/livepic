package com.cl.vtolive.activities;

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
import android.content.Intent;  // added for returning results
import android.app.Activity;    // added for Activity.RESULT_OK

import androidx.appcompat.app.AppCompatActivity;

import com.cl.vtolive.R;
import com.cl.vtolive.modules.video.VideoProcessor;

import java.util.Arrays;

/**
 * Third page: Preview selected interval with playback controls
 */
public class PreviewActivity extends AppCompatActivity {
    private static final String TAG = "PreviewActivity";
    
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
    
    private boolean isPlaying = false;
    private long currentTime = 0;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);
        
        initViews();
        initData();
        setupListeners();
        loadInitialFrame();
    }
    
    private void initViews() {
        ivPreview = findViewById(R.id.ivPreview);
        seekBarPreview = findViewById(R.id.seekBarPreview);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnBack = findViewById(R.id.btnBack);
        btnUseThisFrame = findViewById(R.id.btnUseThisFrame);
    }
    
    private void initData() {
        videoUri = getIntent().getParcelableExtra("VIDEO_URI");
        startTime = getIntent().getLongExtra("START_TIME", 0);
        endTime = getIntent().getLongExtra("END_TIME", 0);
        videoProcessor = new VideoProcessor(this);
        
        currentTime = startTime;
        updateSeekBar();
    }
    
    private void setupListeners() {
        seekBarPreview.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentTime = startTime + (progress * (endTime - startTime) / 100);
                    updateDisplay();
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (isPlaying) {
                    pausePlayback();
                }
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        btnPlayPause.setOnClickListener(v -> togglePlayback());
        btnBack.setOnClickListener(v -> finish());
        btnUseThisFrame.setOnClickListener(v -> useCurrentFrameAsKeyPhoto());
    }
    
    private void loadInitialFrame() {
        loadFrameAtTime(currentTime);
        tvTotalTime.setText(formatTime(endTime - startTime));
        updateDisplay();
    }
    
    private void loadFrameAtTime(long timestamp) {
        if (videoUri == null) return;
        new Thread(() -> {
            try {
                java.util.List<VideoProcessor.FrameInfo> frames = videoProcessor
                    .extractFramesAtTimestamps(videoUri, Arrays.asList(timestamp));
                if (frames == null || frames.isEmpty()) return;
                VideoProcessor.FrameInfo frameInfo = frames.get(0);
                runOnUiThread(() -> {
                    if (frameInfo != null && frameInfo.bitmap != null && !frameInfo.bitmap.isRecycled()) {
                        ivPreview.setImageBitmap(frameInfo.bitmap);
                    }
                    if (frameInfo != null) frameInfo.recycle();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading frame", e);
            }
        }).start();
    }
    
    private void togglePlayback() {
        if (isPlaying) {
            pausePlayback();
        } else {
            startPlayback();
        }
    }
    
    private void startPlayback() {
        isPlaying = true;
        btnPlayPause.setText("⏸️");
        
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPlaying) {
                    currentTime += 100; // Advance 100ms
                    if (currentTime >= endTime) {
                        currentTime = startTime; // Loop back to start
                    }
                    
                    updateDisplay();
                    loadFrameAtTime(currentTime);
                    handler.postDelayed(this, 100);
                }
            }
        };
        
        handler.post(updateRunnable);
    }
    
    private void pausePlayback() {
        isPlaying = false;
        btnPlayPause.setText("▶️");
        
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }
    
    private void updateDisplay() {
        updateSeekBar();
        tvCurrentTime.setText(formatTime(currentTime - startTime));
    }
    
    private void updateSeekBar() {
        int progress = (int) ((currentTime - startTime) * 100 / (endTime - startTime));
        seekBarPreview.setProgress(progress);
    }
    
    private void useCurrentFrameAsKeyPhoto() {
        // Return the current time as the key photo timestamp
        Intent result = new Intent();
        result.putExtra("KEY_FRAME_TIME", currentTime);
        setResult(Activity.RESULT_OK, result);
        Toast.makeText(this, "Using frame at " + formatTime(currentTime - startTime) + 
                      " as key photo", Toast.LENGTH_SHORT).show();
        finish();
    }
    
    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (isPlaying) {
            pausePlayback();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isPlaying) {
            pausePlayback();
        }
    }
}