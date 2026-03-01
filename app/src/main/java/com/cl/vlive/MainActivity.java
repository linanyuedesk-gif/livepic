package com.cl.vlive;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_VIDEO_REQUEST = 1;
    private static final int REQUEST_PERMISSIONS = 2;
    
    private Button btnSelectVideo, btnConvert;
    private TextView tvStatus, tvStart, tvEnd;
    private SeekBar seekBarStart, seekBarEnd;
    
    private Uri videoUri;
    private long videoDuration = 0;
    private long startTime = 0;
    private long endTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnSelectVideo = findViewById(R.id.btnSelectVideo);
        btnConvert = findViewById(R.id.btnConvert);
        tvStatus = findViewById(R.id.tvStatus);
        tvStart = findViewById(R.id.tvStart);
        tvEnd = findViewById(R.id.tvEnd);
        seekBarStart = findViewById(R.id.seekBarStart);
        seekBarEnd = findViewById(R.id.seekBarEnd);

        btnSelectVideo.setOnClickListener(v -> selectVideo());
        btnConvert.setOnClickListener(v -> convertToLivePhoto());

        seekBarStart.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                startTime = (long) (progress / 100.0 * videoDuration);
                tvStart.setText("开始时间: " + formatTime(startTime));
                if (startTime >= endTime) {
                    endTime = startTime + 1000; // 确保结束时间大于开始时间
                    seekBarEnd.setProgress((int) (endTime * 100 / videoDuration));
                    tvEnd.setText("结束时间: " + formatTime(endTime));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekBarEnd.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                endTime = (long) (progress / 100.0 * videoDuration);
                tvEnd.setText("结束时间: " + formatTime(endTime));
                if (endTime <= startTime) {
                    startTime = endTime - 1000; // 确保开始时间小于结束时间
                    seekBarStart.setProgress((int) (startTime * 100 / videoDuration));
                    tvStart.setText("开始时间: " + formatTime(startTime));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 请求权限
        requestPermissions();
    }

    private void requestPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSIONS);
            }
        }
    }

    private void selectVideo() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_VIDEO_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_VIDEO_REQUEST && resultCode == RESULT_OK && data != null) {
            videoUri = data.getData();
            tvStatus.setText("已选择视频: " + getVideoName(videoUri));
            
            // 获取视频时长
            videoDuration = getVideoDuration(videoUri);
            if (videoDuration > 0) {
                // 显示时间选择控件
                tvStart.setVisibility(View.VISIBLE);
                tvEnd.setVisibility(View.VISIBLE);
                seekBarStart.setVisibility(View.VISIBLE);
                seekBarEnd.setVisibility(View.VISIBLE);
                
                // 设置默认值
                startTime = 0;
                endTime = videoDuration;
                seekBarStart.setProgress(0);
                seekBarEnd.setProgress(100);
                tvStart.setText("开始时间: " + formatTime(startTime));
                tvEnd.setText("结束时间: " + formatTime(endTime));
            }
        }
    }

    private String getVideoName(Uri uri) {
        String[] projection = {MediaStore.Video.Media.DISPLAY_NAME};
        android.database.Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            String name = cursor.getString(columnIndex);
            cursor.close();
            return name;
        }
        return "未知视频";
    }

    private long getVideoDuration(Uri uri) {
        String[] projection = {MediaStore.Video.Media.DURATION};
        android.database.Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
            long duration = cursor.getLong(columnIndex);
            cursor.close();
            return duration;
        }
        return 0;
    }

    private String formatTime(long milliseconds) {
        int seconds = (int) (milliseconds / 1000);
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void convertToLivePhoto() {
        if (videoUri == null) {
            Toast.makeText(this, "请先选择视频", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                // 创建输出目录
                File outputDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "LivePhotos");
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }

                // 生成文件名
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String fileName = "LIVE_" + timeStamp;

                // 提取视频片段
                File trimmedVideo = LivePhotoUtils.trimVideo(MainActivity.this, videoUri, outputDir, fileName, startTime, endTime);

                // 提取关键帧作为照片
                File photoFile = LivePhotoUtils.extractFrame(MainActivity.this, videoUri, outputDir, fileName);

                // 创建Live Photo包
                File livePhotoPackage = LivePhotoUtils.createLivePhotoPackage(outputDir, fileName, photoFile, trimmedVideo);

                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Live Photo 已生成: " + livePhotoPackage.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    tvStatus.setText("转换完成！");
                });

            } catch (Exception e) {
                Log.e("LivePic", "转换失败", e);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "转换失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // 权限授予成功
            } else {
                Toast.makeText(this, "需要存储权限才能操作", Toast.LENGTH_SHORT).show();
            }
        }
    }
}