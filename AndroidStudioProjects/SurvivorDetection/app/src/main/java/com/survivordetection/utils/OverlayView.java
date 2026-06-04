package com.survivordetection.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import java.util.List;

public class OverlayView extends View {

    private List<Recognition> results;

    private final Paint boxPaint  = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint bgPaint   = new Paint();

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        boxPaint.setColor(Color.parseColor("#FF4D6D"));
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(8f);
        boxPaint.setAntiAlias(true);
        bgPaint.setColor(Color.parseColor("#CCFF4D6D"));
        bgPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(42f);
        textPaint.setFakeBoldText(true);
        textPaint.setAntiAlias(true);
    }

    public void setResults(List<Recognition> results,
                           int imageWidth, int imageHeight) {
        this.results = results;
        postInvalidate();
    }

    public void setResults(List<Recognition> results) {
        this.results = results;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (results == null || results.isEmpty()) return;

        float viewW = getWidth();
        float viewH = getHeight();

        for (Recognition res : results) {
            RectF box = res.getLocation();
            if (box == null) continue;

            // ✅ Coordinates are 0-1 normalized → multiply by view size
            float left   = box.left   * viewW;
            float top    = box.top    * viewH;
            float right  = box.right  * viewW;
            float bottom = box.bottom * viewH;

            canvas.drawRect(left, top, right, bottom, boxPaint);

            String label = "Survivor " + (int)(res.getConfidence() * 100) + "%";
            float tw  = textPaint.measureText(label);
            float pad = 14f, lh = 55f;
            float lx  = Math.max(0, left);
            float ly  = Math.max(0, top - lh);

            canvas.drawRoundRect(lx, ly, lx + tw + pad * 2,
                    ly + lh, 8f, 8f, bgPaint);
            canvas.drawText(label, lx + pad, ly + lh - 12f, textPaint);
        }
    }
}
