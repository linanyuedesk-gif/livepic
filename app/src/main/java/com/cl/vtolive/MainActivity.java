package com.cl.vtolive;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.cl.vtolive.modules.core.LivePhotoEncoder;
import com.cl.vtolive.modules.export.ExportManager;
import com.cl.vtolive.modules.ui.IntervalSelectorActivity;
import com.cl.vtolive.modules.video.VideoProcessor;
import com.cl.vtolive.utils.PermissionHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "VToLive";
    private static final String PREFS_NAME = "VToLivePrefs";
    private static final String KEY_SELECTED_VIDEO_URI = "selected_video_uri";
    private static final String KEY_CONVERTED_PHOTO_PATH = "converted_photo_path";
    
    private static final int VIDEO_PICKER_REQUEST_CODE = 1001;
    private static final int INTERVAL_SELECTOR_REQUEST_CODE = 1002;
    
    private ImageView ivPreview;
    private TextView tvInstruction;
    private TextView tvStatus;
    private ProgressBar progressBar;
    private Button btnSelectVideo;
    private Button btnConvert;
    private Button btnFrameSelector;
    private android.view.View intervalInfoCard;
    private TextView tvFramePosition;
    private FrameLayout rootLayout;
    
    private Uri selectedVideoUri;
    private String convertedPhotoPath;
    private long videoDuration = 0;
    private long selectedStartTime = 2000;
    private long selectedEndTime = 5000;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    // Module instances
    private VideoProcessor videoProcessor;
    private LivePhotoEncoder livePhotoEncoder;
    private ExportManager exportManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // Hide system UI for immersive experience
        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            decorView.getWindowInsetsController().hide(WindowInsets.Type.systemBars());
        } else {
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }

        setContentView(R.layout.activity_main);

        initViews();
        initModules();
        loadSavedState();
        checkPermissions();
        setupClickListeners();
        
        // Initially hide interval info
        intervalInfoCard.setVisibility(View.GONE);
        btnFrameSelector.setEnabled(false);
        
        hideSystemUI();
    }
    
    private void initViews() {
        ivPreview = findViewById(R.id.ivPreview);
        tvInstruction = findViewById(R.id.tvInstruction);
        tvStatus = findViewById(R.id.tvStatus);
        progressBar = findViewById(R.id.progressBar);
        btnSelectVideo = findViewById(R.id.btnSelectVideo);
        btnConvert = findViewById(R.id.btnConvert);
        btnFrameSelector = findViewById(R.id.btnFrameSelector);
        intervalInfoCard = findViewById(R.id.intervalInfoCard);
        tvFramePosition = findViewById(R.id.tvFramePosition);
        rootLayout = findViewById(R.id.rootLayout);
    }
    
    private void initModules() {
        videoProcessor = new VideoProcessor(this);
        livePhotoEncoder = new LivePhotoEncoder(this);
        exportManager = new ExportManager(this);
    }
    
    private void loadSavedState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String uriStr = prefs.getString(KEY_SELECTED_VIDEO_URI, null);
        if (uriStr != null) {
            selectedVideoUri = Uri.parse(uriStr);
            displayVideoPreview();
        }
        convertedPhotoPath = prefs.getString(KEY_CONVERTED_PHOTO_PATH, null);
    }
    
    private void checkPermissions() {
        if (!PermissionHelper.hasAllPermissions(this)) {
            PermissionHelper.requestPermissions(this);
        }
    }
    
    private void setupClickListeners() {
        btnSelectVideo.setOnClickListener(v -> selectVideo());
        btnConvert.setOnClickListener(v -> convertToLivePhoto());
        btnFrameSelector.setOnClickListener(v -> selectInterval());
        
        rootLayout.setOnClickListener(v -> {
            if (selectedVideoUri == null) {
                selectVideo();
            }
        });
    }
    
    private void selectVideo() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("video/*");
        startActivityForResult(intent, VIDEO_PICKER_REQUEST_CODE);
    }
    
    private void convertToLivePhoto() {
        if (selectedVideoUri == null) {
            Toast.makeText(this, R.string.msg_no_video_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Validate interval
        if (!livePhotoEncoder.validateParameters(selectedStartTime, selectedEndTime, videoDuration)) {
            Toast.makeText(this, "Invalid time interval", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show progress dialog
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Creating Live Photo...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // Perform export using ExportManager
        exportManager.exportToGalleryAsync(selectedVideoUri, selectedStartTime, selectedEndTime, 
            new ExportManager.ExportCallback() {
                @Override
                public void onProgress(int percentage, String message) {
                    runOnUiThread(() -> {
                        progressDialog.setProgress(percentage);
                        progressDialog.setMessage(message);
                    });
                }
                
                @Override
                public void onSuccess(String filePath, Uri mediaStoreUri) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        tvStatus.setText("Live Photo created successfully!");
                        Toast.makeText(MainActivity.this, "Live Photo saved to gallery", Toast.LENGTH_LONG).show();
                        
                        // Update UI
                        btnConvert.setEnabled(true);
                        convertedPhotoPath = filePath;
                        saveState();
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        tvStatus.setText("Error: " + error);
                        Toast.makeText(MainActivity.this, "Export failed: " + error, Toast.LENGTH_LONG).show();
                        btnConvert.setEnabled(true);
                    });
                }
            });
    }
    
    private void selectInterval() {
        if (selectedVideoUri == null) {
            Toast.makeText(this, "Please select a video first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Intent intent = new Intent(this, IntervalSelectorActivity.class);
        intent.putExtra(IntervalSelectorActivity.EXTRA_VIDEO_URI, selectedVideoUri);
        intent.putExtra(IntervalSelectorActivity.EXTRA_START_TIME, selectedStartTime);
        intent.putExtra(IntervalSelectorActivity.EXTRA_END_TIME, selectedEndTime);
        startActivityForResult(intent, INTERVAL_SELECTOR_REQUEST_CODE);
    }
    
    private void displayVideoPreview() {
        if (selectedVideoUri != null) {
            try {
                VideoProcessor.VideoInfo videoInfo = videoProcessor.getVideoInfo(selectedVideoUri);
                if (videoInfo != null) {
                    videoDuration = videoInfo.duration;
                    
                    // Get thumbnail from middle of video
                    long middleTime = videoDuration / 2;
                    Bitmap thumbnail = videoProcessor.extractFramesAtTimestamps(selectedVideoUri, 
                        java.util.Arrays.asList(middleTime)).get(0).bitmap;
                    
                    if (thumbnail != null) {
                        ivPreview.setImageBitmap(thumbnail);
                        ivPreview.setVisibility(View.VISIBLE);
                        tvInstruction.setText(getString(R.string.hint_select_video));
                    }
                }
                
                // Update frame position text
                updateFramePositionText();
                
                // Enable frame selection controls
                btnFrameSelector.setEnabled(true);
                intervalInfoCard.setVisibility(View.VISIBLE);
                updateFramePositionText();
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading video preview", e);
            }
        }
    }
    
    private void saveState() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        if (selectedVideoUri != null) {
            editor.putString(KEY_SELECTED_VIDEO_URI, selectedVideoUri.toString());
        }
        if (convertedPhotoPath != null) {
            editor.putString(KEY_CONVERTED_PHOTO_PATH, convertedPhotoPath);
        }
        editor.apply();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == VIDEO_PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            selectedVideoUri = data.getData();
            if (selectedVideoUri != null) {
                saveState();
                displayVideoPreview();
                btnConvert.setEnabled(true);
                tvStatus.setText("");
                
                // Reset frame selection
                videoDuration = 0;
                selectedStartTime = 2000;
                selectedEndTime = 5000;
                intervalInfoCard.setVisibility(View.GONE);
                btnFrameSelector.setEnabled(false);
                tvFramePosition.setText("00:00 - 00:00 (0s)");
            }
        } else if (requestCode == INTERVAL_SELECTOR_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            selectedStartTime = data.getLongExtra(IntervalSelectorActivity.RESULT_START_TIME, 2000);
            selectedEndTime = data.getLongExtra(IntervalSelectorActivity.RESULT_END_TIME, 5000);
            updateFramePositionText();
            Toast.makeText(this, "Interval selected: " + formatTime(selectedStartTime) + " - " + formatTime(selectedEndTime), 
                Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PermissionHelper.PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (!allGranted) {
                showPermissionDialog();
            }
        }
    }
    
    private void showPermissionDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Video access permission is required to select and convert videos.")
            .setPositiveButton("Settings", (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            final Window window = getWindow();
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }
    
    private void updateFramePositionText() {
        if (tvFramePosition != null && videoDuration > 0) {
            String text = formatTime(selectedStartTime) + " - " + formatTime(selectedEndTime) + 
                         " (" + formatDuration(selectedEndTime - selectedStartTime) + ")";
            tvFramePosition.setText(text);
            intervalInfoCard.setVisibility(View.VISIBLE);
        }
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
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hideSystemUI();
    }
}