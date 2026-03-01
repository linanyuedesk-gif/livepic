package com.cl.vtolive.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.cl.vtolive.R;
import com.cl.vtolive.modules.export.ExportManager;
import com.cl.vtolive.utils.FlowExtras;
import com.cl.vtolive.utils.PermissionHelper;

/**
 * Home: pick video or view recent conversions. First page of the flow.
 */
public class HomeActivity extends AppCompatActivity {

    private Button btnSelectVideo;
    private Button btnRecentConversions;
    private TextView tvWelcomeMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        bindViews();
        if (!PermissionHelper.hasAllPermissions(this)) {
            showPermissionExplanation();
        }
        setupClickListeners();
    }

    private void bindViews() {
        btnSelectVideo = findViewById(R.id.btnSelectVideo);
        btnRecentConversions = findViewById(R.id.btnRecentConversions);
        tvWelcomeMessage = findViewById(R.id.tvWelcomeMessage);
    }

    private void setupClickListeners() {
        btnSelectVideo.setOnClickListener(v -> selectVideo());
        btnRecentConversions.setOnClickListener(v -> showRecentConversions());
    }

    private void selectVideo() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("video/*");
        startActivityForResult(intent, FlowExtras.REQ_VIDEO_PICKER);
    }

    private void showRecentConversions() {
        ExportManager manager = new ExportManager(this);
        ExportManager.ExportStats stats = manager.getExportStats();
        String message = "Total exports: " + stats.totalExports +
                "\nTotal size: " + stats.getTotalSizeFormatted();
        new AlertDialog.Builder(this)
                .setTitle("Recent conversions")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showPermissionExplanation() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("We need video access permission to select and convert your videos to Live Photos.")
                .setPositiveButton("Grant Permission", (d, w) -> PermissionHelper.requestPermissions(this))
                .setNegativeButton("Later", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FlowExtras.REQ_VIDEO_PICKER || resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri != null) {
            startActivity(FlowExtras.intentForIntervalSelection(this, uri));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PermissionHelper.PERMISSION_REQUEST_CODE) return;
        boolean allGranted = true;
        for (int r : grantResults) {
            if (r != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        if (allGranted) {
            Toast.makeText(this, "Permissions granted! You can now select videos.", Toast.LENGTH_SHORT).show();
        } else {
            showPermissionDeniedDialog();
        }
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Denied")
                .setMessage("Video access permission is required. Please enable it in Settings.")
                .setPositiveButton("Open Settings", (d, w) -> {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivity(i);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
