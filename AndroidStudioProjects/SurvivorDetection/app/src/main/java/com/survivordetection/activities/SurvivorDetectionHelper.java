package com.survivordetection.activities;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SurvivorDetectionHelper {
    private static final String MODEL_FILE = "yolov8n_float32.tflite";
    private static final int INPUT_SIZE = 640;

    // Confidence as per your request (0.35f)
    private static final float CONFIDENCE_THRESHOLD = 0.35f;
    private static final float IOU_THRESHOLD = 0.45f;

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Interpreter interpreter;

    public SurvivorDetectionHelper(Context context) {
        this.context = context;
        try {
            interpreter = new Interpreter(loadModelFile());
        } catch (Exception e) {
            Log.e("Detection", "Error: " + e.getMessage());
        }
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        android.content.res.AssetFileDescriptor afd = context.getAssets().openFd(MODEL_FILE);
        FileInputStream fis = new FileInputStream(afd.getFileDescriptor());
        return fis.getChannel().map(FileChannel.MapMode.READ_ONLY, afd.getStartOffset(), afd.getDeclaredLength());
    }

    public void detectSurvivors(Uri imageUri, DetectionCallback callback) {
        executor.execute(() -> {
            try {
                Bitmap original = uriToBitmap(imageUri);
                if (original == null) return;

                float imgW = (float) original.getWidth();
                float imgH = (float) original.getHeight();

                Bitmap resized = Bitmap.createScaledBitmap(original, INPUT_SIZE, INPUT_SIZE, true);
                ByteBuffer inputBuffer = bitmapToByteBuffer(resized);

                float[][][] outputArr = new float[1][84][8400];
                interpreter.run(inputBuffer, outputArr);

                List<BoundingBox> boxes = new ArrayList<>();

                for (int i = 0; i < 8400; i++) {
                    float confidence = outputArr[0][4][i];

                    if (confidence > CONFIDENCE_THRESHOLD) {
                        float cx = outputArr[0][0][i];
                        float cy = outputArr[0][1][i];
                        float w  = outputArr[0][2][i];
                        float h  = outputArr[0][3][i];

                        // --- THE FIX: Multiply directly by original image dimensions ---
                        // Because cx, cy, w, h are already normalized (0 to 1)
                        float x1 = (cx - w / 2f) * imgW;
                        float y1 = (cy - h / 2f) * imgH;
                        float x2 = (cx + w / 2f) * imgW;
                        float y2 = (cy + h / 2f) * imgH;

                        boxes.add(new BoundingBox(x1, y1, x2, y2, confidence));
                    }
                }

                List<BoundingBox> finalBoxes = applyNMS(boxes);
                Bitmap annotated = drawBoxesOnBitmap(original, finalBoxes);

                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        callback.onSuccess(finalBoxes.size(), annotated));

            } catch (Exception e) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        callback.onFailure(e.getMessage()));
            }
        });
    }

    private List<BoundingBox> applyNMS(List<BoundingBox> boxes) {
        boxes.sort((a, b) -> Float.compare(b.confidence, a.confidence));
        List<BoundingBox> result = new ArrayList<>();
        while (!boxes.isEmpty()) {
            BoundingBox best = boxes.remove(0);
            result.add(best);
            boxes.removeIf(next -> computeIOU(best, next) > IOU_THRESHOLD);
        }
        return result;
    }

    private float computeIOU(BoundingBox a, BoundingBox b) {
        float x1 = Math.max(a.x1, b.x1);
        float y1 = Math.max(a.y1, b.y1);
        float x2 = Math.min(a.x2, b.x2);
        float y2 = Math.min(a.y2, b.y2);
        float inter = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        float areaA = (a.x2 - a.x1) * (a.y2 - a.y1);
        float areaB = (b.x2 - b.x1) * (b.y2 - b.y1);
        return inter / (areaA + areaB - inter + 1e-6f);
    }

    // --- DRAWING METHOD WITH HIGH VISIBILITY ---
    public Bitmap drawBoxesOnBitmap(Bitmap source, List<BoundingBox> boxes) {
        Bitmap mutable = source.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutable);

        // Box Paint (Thinner as requested)
        Paint boxPaint = new Paint();
        boxPaint.setColor(Color.BLUE);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(mutable.getWidth() / 150f);
        boxPaint.setAntiAlias(true);

        // Text Paint (For Confidence)
        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(mutable.getWidth() / 25f); // Large text
        textPaint.setFakeBoldText(true);
        textPaint.setAntiAlias(true);

        // Background Label Paint
        Paint labelBgPaint = new Paint();
        labelBgPaint.setColor(Color.BLUE);
        labelBgPaint.setStyle(Paint.Style.FILL);

        for (BoundingBox b : boxes) {
            // Draw Box
            canvas.drawRect(b.x1, b.y1, b.x2, b.y2, boxPaint);

            // Draw Confidence Label (e.g. 85%)
            String label = String.format("%.0f%%", b.confidence * 100);
            float textWidth = textPaint.measureText(label);

            // Text positioning needs a little bit of padding
            float textHeight = mutable.getWidth() / 20f;
            canvas.drawRect(b.x1, b.y1 - textHeight, b.x1 + textWidth + 10, b.y1, labelBgPaint);
            canvas.drawText(label, b.x1 + 5, b.y1 - (textHeight/4), textPaint);
        }
        return mutable;
    }

    private ByteBuffer bitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(1 * 640 * 640 * 3 * 4);
        buffer.order(ByteOrder.nativeOrder());
        int[] pixels = new int[640 * 640];
        bitmap.getPixels(pixels, 0, 640, 0, 0, 640, 640);
        for (int pixel : pixels) {
            buffer.putFloat(((pixel >> 16) & 0xFF) / 255.0f);
            buffer.putFloat(((pixel >> 8) & 0xFF) / 255.0f);
            buffer.putFloat((pixel & 0xFF) / 255.0f);
        }
        return buffer;
    }

    private Bitmap uriToBitmap(Uri uri) {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(is);
        } catch (Exception e) { return null; }
    }

    public void close() { if (interpreter != null) interpreter.close(); executor.shutdown(); }

    public interface DetectionCallback { void onSuccess(int count, Bitmap bmp); void onFailure(String err); }

    public static class BoundingBox {
        float x1, y1, x2, y2, confidence;
        BoundingBox(float x1, float y1, float x2, float y2, float c) {
            this.x1=x1; this.y1=y1; this.x2=x2; this.y2=y2; this.confidence=c;
        }
    }
}