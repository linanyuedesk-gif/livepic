package com.cl.vtolive.modules.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom view for timeline-based interval selection
 * Allows users to visually select start and end times for Live Photo creation
 */
public class TimelineView extends View {
    private static final String TAG = "TimelineView";
    
    // Paint objects for drawing
    private Paint backgroundPaint;
    private Paint timelinePaint;
    private Paint thumbPaint;
    private Paint selectedAreaPaint;
    private Paint textPaint;
    private Paint thumbnailPaint;
    
    // Timeline dimensions
    private float timelineHeight = 60f;
    private float thumbRadius = 20f;
    private float padding = 20f;
    
    // Time values
    private long totalDuration = 10000; // milliseconds
    private long startTime = 2000;      // milliseconds
    private long endTime = 5000;        // milliseconds
    
    // Touch handling
    private Thumb draggingThumb = null;
    private float lastTouchX;
    
    // Callbacks
    private OnIntervalChangeListener intervalListener;
    
    // Thumbnails
    private List<ThumbnailInfo> thumbnails = new ArrayList<>();
    private int thumbnailWidth = 40;
    private int thumbnailHeight = 30;
    
    // Enums
    private enum Thumb { START, END }
    
    public TimelineView(Context context) {
        super(context);
        init();
    }
    
    public TimelineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public TimelineView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        // Initialize paints
        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.parseColor("#2A2A3E"));
        backgroundPaint.setStyle(Paint.Style.FILL);
        
        timelinePaint = new Paint();
        timelinePaint.setColor(Color.parseColor("#4A4A5E"));
        timelinePaint.setStyle(Paint.Style.FILL);
        timelinePaint.setStrokeWidth(4f);
        
        thumbPaint = new Paint();
        thumbPaint.setColor(Color.parseColor("#64B5F6"));
        thumbPaint.setStyle(Paint.Style.FILL);
        
        selectedAreaPaint = new Paint();
        selectedAreaPaint.setColor(Color.parseColor("#64B5F6"));
        selectedAreaPaint.setAlpha(80);
        selectedAreaPaint.setStyle(Paint.Style.FILL);
        
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(24f);
        textPaint.setAntiAlias(true);
        
        thumbnailPaint = new Paint();
        thumbnailPaint.setAntiAlias(true);
        
        // Set up touch listener
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return handleTouchEvent(event);
            }
        });
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        float width = getWidth();
        float height = getHeight();
        
        // Draw background
        canvas.drawRect(0, 0, width, height, backgroundPaint);
        
        // Calculate timeline bounds
        float timelineY = height / 2 - timelineHeight / 2;
        float timelineStartX = padding;
        float timelineEndX = width - padding;
        float timelineWidth = timelineEndX - timelineStartX;
        
        // Draw timeline background
        RectF timelineRect = new RectF(timelineStartX, timelineY, timelineEndX, timelineY + timelineHeight);
        canvas.drawRoundRect(timelineRect, 10f, 10f, timelinePaint);
        
        // Draw selected area
        float startX = timelineStartX + (timelineWidth * startTime / totalDuration);
        float endX = timelineStartX + (timelineWidth * endTime / totalDuration);
        
        if (startX < endX) {
            RectF selectedRect = new RectF(startX, timelineY, endX, timelineY + timelineHeight);
            canvas.drawRoundRect(selectedRect, 10f, 10f, selectedAreaPaint);
        }
        
        // Draw thumbnails
        drawThumbnails(canvas, timelineStartX, timelineEndX, timelineY);
        
        // Draw thumbs
        canvas.drawCircle(startX, timelineY + timelineHeight / 2, thumbRadius, thumbPaint);
        canvas.drawCircle(endX, timelineY + timelineHeight / 2, thumbRadius, thumbPaint);
        
        // Draw time labels
        drawTimeLabels(canvas, timelineStartX, timelineEndX, timelineY, width);
    }
    
    private void drawThumbnails(Canvas canvas, float timelineStartX, float timelineEndX, float timelineY) {
        float timelineWidth = timelineEndX - timelineStartX;
        
        for (ThumbnailInfo thumbInfo : thumbnails) {
            if (thumbInfo.bitmap != null && !thumbInfo.bitmap.isRecycled()) {
                float x = timelineStartX + (timelineWidth * thumbInfo.timestamp / totalDuration);
                float y = timelineY - thumbnailHeight - 10;
                
                RectF dst = new RectF(x - thumbnailWidth/2, y, x + thumbnailWidth/2, y + thumbnailHeight);
                canvas.drawBitmap(thumbInfo.bitmap, null, dst, thumbnailPaint);
            }
        }
    }
    
    private void drawTimeLabels(Canvas canvas, float timelineStartX, float timelineEndX, 
                               float timelineY, float width) {
        // Draw start time
        String startLabel = formatTime(startTime);
        float startLabelWidth = textPaint.measureText(startLabel);
        canvas.drawText(startLabel, timelineStartX - startLabelWidth/2, timelineY - 20, textPaint);
        
        // Draw end time
        String endLabel = formatTime(endTime);
        float endLabelWidth = textPaint.measureText(endLabel);
        canvas.drawText(endLabel, timelineEndX - endLabelWidth/2, timelineY - 20, textPaint);
        
        // Draw duration
        long duration = endTime - startTime;
        String durationLabel = formatDuration(duration);
        float durationLabelWidth = textPaint.measureText(durationLabel);
        canvas.drawText(durationLabel, width/2 - durationLabelWidth/2, timelineY + timelineHeight + 40, textPaint);
    }
    
    private boolean handleTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                draggingThumb = getThumbAtPosition(x, y);
                lastTouchX = x;
                return draggingThumb != null;
                
            case MotionEvent.ACTION_MOVE:
                if (draggingThumb != null) {
                    float dx = x - lastTouchX;
                    moveThumb(draggingThumb, dx);
                    lastTouchX = x;
                    invalidate();
                    return true;
                }
                break;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (draggingThumb != null) {
                    draggingThumb = null;
                    if (intervalListener != null) {
                        intervalListener.onIntervalChanged(startTime, endTime);
                    }
                    return true;
                }
                break;
        }
        
        return false;
    }
    
    private Thumb getThumbAtPosition(float x, float y) {
        float width = getWidth();
        float timelineStartX = padding;
        float timelineEndX = width - padding;
        float timelineWidth = timelineEndX - timelineStartX;
        float timelineY = getHeight() / 2;
        
        float startX = timelineStartX + (timelineWidth * startTime / totalDuration);
        float endX = timelineStartX + (timelineWidth * endTime / totalDuration);
        
        // Check if touch is near start thumb
        if (Math.abs(x - startX) <= thumbRadius * 2 && 
            Math.abs(y - timelineY) <= thumbRadius * 2) {
            return Thumb.START;
        }
        
        // Check if touch is near end thumb
        if (Math.abs(x - endX) <= thumbRadius * 2 && 
            Math.abs(y - timelineY) <= thumbRadius * 2) {
            return Thumb.END;
        }
        
        return null;
    }
    
    private void moveThumb(Thumb thumb, float deltaX) {
        float width = getWidth();
        float timelineStartX = padding;
        float timelineEndX = width - padding;
        float timelineWidth = timelineEndX - timelineStartX;
        
        long timeDelta = (long) (deltaX * totalDuration / timelineWidth);
        
        if (thumb == Thumb.START) {
            long newStartTime = startTime + timeDelta;
            // Constrain to valid range
            newStartTime = Math.max(0, Math.min(newStartTime, endTime - 500));
            startTime = newStartTime;
        } else {
            long newEndTime = endTime + timeDelta;
            // Constrain to valid range
            newEndTime = Math.max(startTime + 500, Math.min(newEndTime, totalDuration));
            endTime = newEndTime;
        }
    }
    
    // Public methods
    public void setTotalDuration(long duration) {
        this.totalDuration = duration;
        constrainTimes();
        invalidate();
    }
    
    public void setInterval(long start, long end) {
        this.startTime = start;
        this.endTime = end;
        constrainTimes();
        invalidate();
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public long getEndTime() {
        return endTime;
    }
    
    public long getDuration() {
        return endTime - startTime;
    }
    
    public void setOnIntervalChangeListener(OnIntervalChangeListener listener) {
        this.intervalListener = listener;
    }
    
    public void setThumbnails(List<ThumbnailInfo> thumbnails) {
        this.thumbnails = thumbnails != null ? thumbnails : new ArrayList<ThumbnailInfo>();
        invalidate();
    }
    
    private void constrainTimes() {
        startTime = Math.max(0, Math.min(startTime, totalDuration - 500));
        endTime = Math.max(startTime + 500, Math.min(endTime, totalDuration));
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
    
    // Interfaces
    public interface OnIntervalChangeListener {
        void onIntervalChanged(long startTime, long endTime);
    }
    
    // Data classes
    public static class ThumbnailInfo {
        public long timestamp;
        public Bitmap bitmap;
        
        public ThumbnailInfo(long timestamp, Bitmap bitmap) {
            this.timestamp = timestamp;
            this.bitmap = bitmap;
        }
    }
}