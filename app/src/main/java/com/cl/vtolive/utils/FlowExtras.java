package com.cl.vtolive.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.cl.vtolive.activities.ExportActivity;
import com.cl.vtolive.activities.IntervalSelectionActivity;
import com.cl.vtolive.activities.PreviewActivity;

/**
 * Central place for flow navigation: Intent extra keys and request codes.
 * Use when starting activities or reading results.
 */
public final class FlowExtras {

    private FlowExtras() {}

    // --- Extra keys ---
    public static final String VIDEO_URI = "VIDEO_URI";
    public static final String START_TIME = "START_TIME";
    public static final String END_TIME = "END_TIME";
    public static final String KEY_FRAME_TIME = "KEY_FRAME_TIME";

    // --- Request codes ---
    public static final int REQ_VIDEO_PICKER = 1001;
    public static final int REQ_PREVIEW = 2001;

    // --- Build intents for next screen ---

    public static Intent intentForIntervalSelection(Context context, Uri videoUri) {
        Intent i = new Intent(context, IntervalSelectionActivity.class);
        i.putExtra(VIDEO_URI, videoUri);
        return i;
    }

    public static Intent intentForPreview(Context context, Uri videoUri, long startTime, long endTime) {
        Intent i = new Intent(context, PreviewActivity.class);
        i.putExtra(VIDEO_URI, videoUri);
        i.putExtra(START_TIME, startTime);
        i.putExtra(END_TIME, endTime);
        return i;
    }

    public static Intent intentForExport(Context context, Uri videoUri, long startTime, long endTime, long keyFrameTime) {
        Intent i = new Intent(context, ExportActivity.class);
        i.putExtra(VIDEO_URI, videoUri);
        i.putExtra(START_TIME, startTime);
        i.putExtra(END_TIME, endTime);
        if (keyFrameTime >= 0) {
            i.putExtra(KEY_FRAME_TIME, keyFrameTime);
        }
        return i;
    }

    public static Intent resultWithKeyFrameTime(long keyFrameTime) {
        Intent i = new Intent();
        i.putExtra(KEY_FRAME_TIME, keyFrameTime);
        return i;
    }

    /** Read VIDEO_URI from intent (call from Activity.getIntent()). */
    public static Uri getVideoUri(Intent intent) {
        return intent != null ? intent.getParcelableExtra(VIDEO_URI) : null;
    }

    /** Read START_TIME from intent. */
    public static long getStartTime(Intent intent, long defaultValue) {
        return intent != null ? intent.getLongExtra(START_TIME, defaultValue) : defaultValue;
    }

    /** Read END_TIME from intent. */
    public static long getEndTime(Intent intent, long defaultValue) {
        return intent != null ? intent.getLongExtra(END_TIME, defaultValue) : defaultValue;
    }

    /** Read KEY_FRAME_TIME from intent. */
    public static long getKeyFrameTime(Intent intent, long defaultValue) {
        return intent != null ? intent.getLongExtra(KEY_FRAME_TIME, defaultValue) : defaultValue;
    }
}
