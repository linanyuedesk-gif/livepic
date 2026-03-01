package com.cl.vtolive;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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
    private static final int PERMISSION_REQUEST_CODE = 1002;
    
    private ImageView ivPreview;
    private TextView tvInstruction;
    private TextView tvStatus;
    private ProgressBar progressBar;
    private Button btnSelectVideo;
    private Button btnConvert;
    private Button btnFrameSelector;
    private SeekBar seekBarFrame;
    private TextView tvFramePosition;
    private FrameLayout rootLayout;
    
    private Uri selectedVideoUri;
    private String convertedPhotoPath;
    private long videoDuration = 0;
    private long selectedFrameTime = 0;
    private Handler handler = new Handler(Looper.getMainLooper());
    
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
        loadSavedState();
        checkPermissions();
        setupClickListeners();
        
        // Initially disable frame selection
        btnFrameSelector.setEnabled(false);
        seekBarFrame.setVisibility(View.GONE);
        
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
        seekBarFrame = findViewById(R.id.seekBarFrame);
        tvFramePosition = findViewById(R.id.tvFramePosition);
        rootLayout = findViewById(R.id.rootLayout);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ - Check new media permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_IMAGES
                    },
                    PERMISSION_REQUEST_CODE);
            }
        } else {
            // Older Android versions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    PERMISSION_REQUEST_CODE);
            }
        }
    }
    
    private void setupClickListeners() {
        btnSelectVideo.setOnClickListener(v -> selectVideo());
        btnConvert.setOnClickListener(v -> convertToLivePhoto());
        btnFrameSelector.setOnClickListener(v -> showFrameSelectionDialog());
        
        seekBarFrame.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && videoDuration > 0) {
                    selectedFrameTime = (long) (progress * videoDuration / 100.0);
                    updateFramePositionText();
                    updatePreviewFrame();
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
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
        
        // Show progress
        progressBar.setVisibility(View.VISIBLE);
        btnConvert.setEnabled(false);
        tvStatus.setText(R.string.msg_processing);
        
        // Perform conversion in background thread
        new Thread(() -> {
            try {
                boolean success = performConversion();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnConvert.setEnabled(true);
                    
                    if (success) {
                        tvStatus.setText(R.string.msg_conversion_complete);
                        Toast.makeText(MainActivity.this, R.string.msg_conversion_complete, Toast.LENGTH_LONG).show();
                    } else {
                        tvStatus.setText(R.string.msg_conversion_error);
                        Toast.makeText(MainActivity.this, R.string.msg_conversion_error, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Conversion failed", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnConvert.setEnabled(true);
                    tvStatus.setText(R.string.msg_conversion_error);
                    Toast.makeText(MainActivity.this, R.string.msg_conversion_error, Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private boolean performConversion() {
        try {
            // Extract frame from video at selected time
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(this, selectedVideoUri);
            
            // Get frame at selected time
            Bitmap bitmap = retriever.getFrameAtTime(selectedFrameTime * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            retriever.release();
            
            if (bitmap == null) {
                return false;
            }
            
            // Save image to Pictures directory
            String fileName = "LivePhoto_" + UUID.randomUUID().toString() + ".jpg";
            File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File outputFile = new File(picturesDir, fileName);
            
            FileOutputStream fos = new FileOutputStream(outputFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();
            
            // Save the path
            convertedPhotoPath = outputFile.getAbsolutePath();
            saveState();
            
            // Add to media store so it appears in gallery
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            
            ContentResolver resolver = getContentResolver();
            Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri item = resolver.insert(collection, values);
            
            if (item != null) {
                try (OutputStream out = resolver.openOutputStream(item)) {
                    if (out != null) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                    }
                }
                
                // Mark as completed
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(item, values, null, null);
            }
            
            bitmap.recycle();
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error during conversion", e);
            return false;
        }
    }
    
    private void displayVideoPreview() {
        if (selectedVideoUri != null) {
            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(this, selectedVideoUri);
                
                // Get video duration
                String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (durationStr != null) {
                    videoDuration = Long.parseLong(durationStr);
                }
                
                // Set default frame time to middle of video
                selectedFrameTime = videoDuration / 2;
                
                // Get thumbnail
                Bitmap thumbnail = retriever.getFrameAtTime(selectedFrameTime * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (thumbnail != null) {
                    ivPreview.setImageBitmap(thumbnail);
                    ivPreview.setVisibility(View.VISIBLE);
                    tvInstruction.setText(getString(R.string.hint_select_video));
                }
                
                // Update frame position text
                updateFramePositionText();
                
                // Enable frame selection controls
                btnFrameSelector.setEnabled(true);
                seekBarFrame.setVisibility(View.VISIBLE);
                int progress = (int) (selectedFrameTime * 100 / videoDuration);
                seekBarFrame.setProgress(progress);
                
                retriever.release();
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
                selectedFrameTime = 0;
                seekBarFrame.setVisibility(View.GONE);
                btnFrameSelector.setEnabled(false);
                tvFramePosition.setText("00:00 / 00:00");
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
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
    
    private void showFrameSelectionDialog() {
        if (selectedVideoUri == null || videoDuration == 0) {
            Toast.makeText(this, "Please select a video first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Frame Position");
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);
        
        // Current position text
        TextView currentPositionText = new TextView(this);
        currentPositionText.setText("Selected frame: " + formatTime(selectedFrameTime));
        currentPositionText.setTextSize(16);
        currentPositionText.setPadding(0, 0, 0, 20);
        layout.addView(currentPositionText);
        
        // SeekBar for frame selection
        SeekBar dialogSeekBar = new SeekBar(this);
        dialogSeekBar.setMax(100);
        int progress = (int) (selectedFrameTime * 100 / videoDuration);
        dialogSeekBar.setProgress(progress);
        dialogSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    selectedFrameTime = (long) (progress * videoDuration / 100.0);
                    currentPositionText.setText("Selected frame: " + formatTime(selectedFrameTime));
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                updatePreviewFrame();
                updateFramePositionText();
            }
        });
        layout.addView(dialogSeekBar);
        
        builder.setView(layout);
        builder.setPositiveButton("OK", null);
        builder.show();
    }
    
    private void updatePreviewFrame() {
        if (selectedVideoUri != null && videoDuration > 0) {
            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(this, selectedVideoUri);
                
                Bitmap frame = retriever.getFrameAtTime(selectedFrameTime * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (frame != null) {
                    ivPreview.setImageBitmap(frame);
                }
                
                retriever.release();
            } catch (Exception e) {
                Log.e(TAG, "Error updating preview frame", e);
            }
        }
    }
    
    private void updateFramePositionText() {
        if (tvFramePosition != null && videoDuration > 0) {
            String text = formatTime(selectedFrameTime) + " / " + formatTime(videoDuration);
            tvFramePosition.setText(text);
        }
    }
    
    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hideSystemUI();
    }
}