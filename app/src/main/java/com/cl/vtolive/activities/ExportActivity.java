package com.cl.vtolive.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.cl.vtolive.R;
import com.cl.vtolive.modules.core.LivePhotoEncoder;
import com.cl.vtolive.modules.export.ExportManager;

import java.io.File;

/**
 * Fourth page: Export and save the Live Photo
 */
public class ExportActivity extends AppCompatActivity {
    private static final String TAG = "ExportActivity";
    
    private ImageView ivKeyPhoto;
    private TextView tvExportInfo;
    private TextView tvStatus;
    private Button btnExport;
    private Button btnShare;
    private Button btnBackToHome;
    
    private Uri videoUri;
    private long startTime;
    private long endTime;
    private long keyFrameTime = -1;
    private LivePhotoEncoder livePhotoEncoder;
    private ExportManager exportManager;
    private String exportedFilePath;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export);
        
        initViews();
        initData();
        setupListeners();
        showExportPreview();
    }
    
    private void initViews() {
        ivKeyPhoto = findViewById(R.id.ivKeyPhoto);
        tvExportInfo = findViewById(R.id.tvExportInfo);
        tvStatus = findViewById(R.id.tvStatus);
        btnExport = findViewById(R.id.btnExport);
        btnShare = findViewById(R.id.btnShare);
        btnBackToHome = findViewById(R.id.btnBackToHome);
    }
    
    private void initData() {
        videoUri = getIntent().getParcelableExtra("VIDEO_URI");
        startTime = getIntent().getLongExtra("START_TIME", 0);
        endTime = getIntent().getLongExtra("END_TIME", 0);
        keyFrameTime = getIntent().getLongExtra("KEY_FRAME_TIME", -1);
        
        livePhotoEncoder = new LivePhotoEncoder(this);
        exportManager = new ExportManager(this);
        
        // Disable share button until export is complete
        btnShare.setEnabled(false);
    }
    
    private void setupListeners() {
        btnExport.setOnClickListener(v -> startExport());
        btnShare.setOnClickListener(v -> shareLivePhoto());
        btnBackToHome.setOnClickListener(v -> backToHome());
    }
    
    private void showExportPreview() {
        String info = String.format("Video: %s\nInterval: %s - %s\nDuration: %s",
            getFileName(videoUri),
            formatTime(startTime),
            formatTime(endTime),
            formatDuration(endTime - startTime));
        if (keyFrameTime >= startTime && keyFrameTime <= endTime) {
            info += "\nKey frame: " + formatTime(keyFrameTime - startTime);
        }
        
        tvExportInfo.setText(info);
        tvStatus.setText("Ready to create Live Photo");
        
        // Load key photo preview (respect custom key frame if provided)
        loadKeyPhotoPreview();
    }
    
    private void loadKeyPhotoPreview() {
        new Thread(() -> {
            try {
                // Determine which frame to use for preview
                long previewTime = (keyFrameTime >= startTime && keyFrameTime <= endTime)
                        ? keyFrameTime
                        : (startTime + endTime) / 2;
                android.graphics.Bitmap keyPhoto = livePhotoEncoder
                    .extractKeyPhoto(videoUri, startTime, endTime, previewTime);
                
                runOnUiThread(() -> {
                    if (keyPhoto != null && !keyPhoto.isRecycled()) {
                        ivKeyPhoto.setImageBitmap(keyPhoto);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading key photo preview", e);
            }
        }).start();
    }
    
    private void startExport() {
        long videoApproxDuration = endTime - startTime + 1000; // rough
        if (!livePhotoEncoder.validateParameters(startTime, endTime, videoApproxDuration)) {
            Toast.makeText(this, "Invalid time interval", Toast.LENGTH_SHORT).show();
            return;
        }
        
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Creating your Live Photo...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        btnExport.setEnabled(false);
        tvStatus.setText("Exporting...");
        
        exportManager.exportToGalleryAsync(videoUri, startTime, endTime, keyFrameTime,
            new ExportManager.ExportCallback() {
                @Override
                public void onProgress(int percentage, String message) {
                    runOnUiThread(() -> {
                        progressDialog.setProgress(percentage);
                        progressDialog.setMessage(message);
                        tvStatus.setText(message);
                    });
                }
                
                @Override
                public void onSuccess(String filePath, Uri mediaStoreUri) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        exportedFilePath = filePath;
                        tvStatus.setText("✅ Live Photo created successfully!");
                        btnExport.setEnabled(false);
                        btnShare.setEnabled(true);
                        Toast.makeText(ExportActivity.this, 
                            "Live Photo saved to gallery!", Toast.LENGTH_LONG).show();
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        tvStatus.setText("❌ Export failed: " + error);
                        btnExport.setEnabled(true);
                        Toast.makeText(ExportActivity.this, 
                            "Export failed: " + error, Toast.LENGTH_LONG).show();
                    });
                }
            });
    }
    
    private void shareLivePhoto() {
        if (exportedFilePath == null) {
            Toast.makeText(this, "No exported file to share", Toast.LENGTH_SHORT).show();
            return;
        }
        
        exportManager.shareLivePhoto(exportedFilePath, new ExportManager.ShareCallback() {
            @Override
            public void onShareUriReady(Uri shareUri) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("image/jpeg");
                shareIntent.putExtra(Intent.EXTRA_STREAM, shareUri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, "Share Live Photo"));
            }
            
            @Override
            public void onError(String error) {
                Toast.makeText(ExportActivity.this, 
                    "Share failed: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void backToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
    
    private String getFileName(Uri uri) {
        String path = uri.getLastPathSegment();
        if (path != null && path.contains("/")) {
            String[] parts = path.split("/");
            return parts[parts.length - 1];
        }
        return path != null ? path : "Unknown file";
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
}