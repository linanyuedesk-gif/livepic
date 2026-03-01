package com.cl.vtolive.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.cl.vtolive.R;
import com.cl.vtolive.modules.video.VideoProcessor;
import com.cl.vtolive.utils.PermissionHelper;

/**
 * Home/Welcome screen - First page of the multi-page flow
 */
public class HomeActivity extends AppCompatActivity {
    private static final String TAG = "HomeActivity";
    private static final int VIDEO_PICKER_REQUEST_CODE = 1001;
    private static final int PERMISSION_REQUEST_CODE = 1002;
    
    private Button btnSelectVideo;
    private Button btnRecentConversions;
    private TextView tvWelcomeMessage;
    private VideoProcessor videoProcessor;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        
        initViews();
        initModules();
        checkPermissions();
        setupClickListeners();
    }
    
    private void initViews() {
        btnSelectVideo = findViewById(R.id.btnSelectVideo);
        btnRecentConversions = findViewById(R.id.btnRecentConversions);
        tvWelcomeMessage = findViewById(R.id.tvWelcomeMessage);
    }
    
    private void initModules() {
        videoProcessor = new VideoProcessor(this);
    }
    
    private void setupClickListeners() {
        btnSelectVideo.setOnClickListener(v -> selectVideo());
        btnRecentConversions.setOnClickListener(v -> showRecentConversions());
    }
    
    private void selectVideo() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("video/*");
        startActivityForResult(intent, VIDEO_PICKER_REQUEST_CODE);
    }
    
    private void showRecentConversions() {
        // TODO: Implement recent conversions feature
        Toast.makeText(this, "Recent conversions feature coming soon", Toast.LENGTH_SHORT).show();
    }
    
    private void checkPermissions() {
        if (!PermissionHelper.hasAllPermissions(this)) {
            showPermissionExplanation();
        }
    }
    
    private void showPermissionExplanation() {
        new AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("We need video access permission to select and convert your videos to Live Photos.")
            .setPositiveButton("Grant Permission", (dialog, which) -> requestPermissions())
            .setNegativeButton("Later", null)
            .show();
    }
    
    private void requestPermissions() {
        PermissionHelper.requestPermissions(this);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == VIDEO_PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            Uri selectedVideoUri = data.getData();
            if (selectedVideoUri != null) {
                // Navigate to interval selection page
                Intent intent = new Intent(this, IntervalSelectionActivity.class);
                intent.putExtra("VIDEO_URI", selectedVideoUri);
                startActivity(intent);
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (!allGranted) {
                showPermissionDeniedDialog();
            } else {
                Toast.makeText(this, "Permissions granted! You can now select videos.", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Permission Denied")
            .setMessage("Video access permission is required to select videos. Please enable it in Settings.")
            .setPositiveButton("Open Settings", (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}