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
import com.cl.vtolive.utils.FileUtils;
import com.cl.vtolive.utils.FlowExtras;
import com.cl.vtolive.utils.TimeFormatUtils;

import java.util.ArrayList;

/**
 * Export and share Live Photo. Fourth page of the flow.
 */
public class ExportActivity extends AppCompatActivity {

    private static final String TAG = "Export";

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
        bindViews();
        readExtras();
        exportManager = new ExportManager(this);
        livePhotoEncoder = new LivePhotoEncoder(this);
        btnShare.setEnabled(false);
        setupListeners();
        showExportPreview();
    }

    private void bindViews() {
        ivKeyPhoto = findViewById(R.id.ivKeyPhoto);
        tvExportInfo = findViewById(R.id.tvExportInfo);
        tvStatus = findViewById(R.id.tvStatus);
        btnExport = findViewById(R.id.btnExport);
        btnShare = findViewById(R.id.btnShare);
        btnBackToHome = findViewById(R.id.btnBackToHome);
    }

    private void readExtras() {
        videoUri = FlowExtras.getVideoUri(getIntent());
        startTime = FlowExtras.getStartTime(getIntent(), 0);
        endTime = FlowExtras.getEndTime(getIntent(), 0);
        keyFrameTime = FlowExtras.getKeyFrameTime(getIntent(), -1);
    }

    private void setupListeners() {
        btnExport.setOnClickListener(v -> startExport());
        btnShare.setOnClickListener(v -> shareLivePhoto());
        btnBackToHome.setOnClickListener(v -> backToHome());
    }

    private void showExportPreview() {
        if (videoUri == null) {
            tvExportInfo.setText("No video selected");
            tvStatus.setText("Go back and select a video first");
            btnExport.setEnabled(false);
            return;
        }
        String info = String.format("Video: %s\nInterval: %s - %s\nDuration: %s",
                FileUtils.getFileName(this, videoUri),
                TimeFormatUtils.formatTime(startTime),
                TimeFormatUtils.formatTime(endTime),
                TimeFormatUtils.formatDuration(endTime - startTime));
        if (keyFrameTime >= startTime && keyFrameTime <= endTime) {
            info += "\nKey frame: " + TimeFormatUtils.formatTime(keyFrameTime - startTime);
        }
        tvExportInfo.setText(info);
        tvStatus.setText("Ready to create Live Photo");
        loadKeyPhotoPreview();
    }

    private void loadKeyPhotoPreview() {
        if (videoUri == null) return;
        long previewTime = (keyFrameTime >= startTime && keyFrameTime <= endTime) ? keyFrameTime : (startTime + endTime) / 2;
        new Thread(() -> {
            try {
                android.graphics.Bitmap keyPhoto = livePhotoEncoder.extractKeyPhoto(videoUri, startTime, endTime, previewTime);
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
        long approxDuration = endTime - startTime + 1000;
        if (!livePhotoEncoder.validateParameters(startTime, endTime, approxDuration)) {
            Toast.makeText(this, "Invalid time interval", Toast.LENGTH_SHORT).show();
            return;
        }
        ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Creating your Live Photo...");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setCancelable(false);
        progress.show();
        btnExport.setEnabled(false);
        tvStatus.setText("Exporting...");

        exportManager.exportToGalleryAsync(videoUri, startTime, endTime, keyFrameTime, new ExportManager.ExportCallback() {
            @Override
            public void onProgress(int percentage, String message) {
                runOnUiThread(() -> {
                    progress.setProgress(percentage);
                    progress.setMessage(message);
                    tvStatus.setText(message);
                });
            }

            @Override
            public void onSuccess(String filePath, Uri mediaStoreUri) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    exportedFilePath = filePath;
                    tvStatus.setText("✅ Live Photo created successfully!");
                    btnExport.setEnabled(false);
                    btnShare.setEnabled(true);
                    Toast.makeText(ExportActivity.this, "Live Photo saved to gallery!", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    tvStatus.setText("❌ Export failed: " + error);
                    btnExport.setEnabled(true);
                    Toast.makeText(ExportActivity.this, "Export failed: " + error, Toast.LENGTH_LONG).show();
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
            public void onShareUrisReady(ArrayList<Uri> uris) {
                Intent shareIntent = uris.size() > 1 ? new Intent(Intent.ACTION_SEND_MULTIPLE) : new Intent(Intent.ACTION_SEND);
                shareIntent.setType(uris.size() > 1 ? "*/*" : "image/heic");
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (uris.size() > 1) {
                    shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                } else {
                    shareIntent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
                }
                startActivity(Intent.createChooser(shareIntent, "Share Live Photo (HEIC + MOV)"));
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ExportActivity.this, "Share failed: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void backToHome() {
        Intent i = new Intent(this, HomeActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }
}
