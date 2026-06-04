package com.survivordetection.utils;

import android.graphics.RectF;

/**
 * Simple data class to hold detection results.
 * Moved to a separate file to avoid dependencies on TFLite during layout preview.
 */
public class Recognition {
    private final RectF location;
    private final float confidence;

    public Recognition(RectF location, float confidence) {
        this.location   = location;
        this.confidence = confidence;
    }

    public RectF getLocation()   { return location; }
    public float getConfidence() { return confidence; }
}
