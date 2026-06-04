package com.survivordetection.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YoloDetector {

    private static final String TAG = "YoloDetector";
    private Interpreter tflite;
    private final int INPUT_SIZE = 640;
    private final float CONFIDENCE_THRESHOLD = 0.55f; // ✅ Higher = less false detections
    private final float IOU_THRESHOLD = 0.45f;

    // Output shape info
    private int outRows, outCols;
    private boolean isTransposed;

    public YoloDetector(Context context, String modelPath) {
        try {
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            MappedByteBuffer modelFile = loadModelFile(context, modelPath);
            tflite = new Interpreter(modelFile, options);

            // Log output shape
            int[] shape = tflite.getOutputTensor(0).shape();
            Log.d(TAG, "✅ Model loaded. Output shape: ["
                    + shape[0] + "][" + shape[1] + "][" + shape[2] + "]");

            // YOLOv8: either [1][84][8400] or [1][8400][84]
            isTransposed = shape[1] > shape[2]; // [1][8400][84]
            outRows = shape[1];
            outCols = shape[2];

            Log.d(TAG, "isTransposed=" + isTransposed
                    + " rows=" + outRows + " cols=" + outCols);

        } catch (Exception e) {
            Log.e(TAG, "❌ Model load failed: " + e.getMessage());
        }
    }

    private MappedByteBuffer loadModelFile(Context context, String modelPath)
            throws IOException {
        AssetFileDescriptor fd = context.getAssets().openFd(modelPath);
        FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
        FileChannel fc = fis.getChannel();
        return fc.map(FileChannel.MapMode.READ_ONLY,
                fd.getStartOffset(), fd.getDeclaredLength());
    }

    public List<Recognition> detectWithBoxes(Bitmap bitmap) {
        List<Recognition> results = new ArrayList<>();
        if (tflite == null) return results;

        // 1. Resize to 640x640
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);

        // 2. Convert to float ByteBuffer
        ByteBuffer inputBuffer = bitmapToByteBuffer(resized);

        // 3. Run inference
        float[][][] output = new float[1][outRows][outCols];
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, output);

        try {
            tflite.runForMultipleInputsOutputs(new Object[]{inputBuffer}, outputMap);
        } catch (Exception e) {
            Log.e(TAG, "Inference error: " + e.getMessage());
            return results;
        }

        // 4. Parse detections
        int numDetections = isTransposed ? outRows : outCols;

        // COCO dataset mein class index 0 = "person"
        final int PERSON_CLASS_INDEX = 0;

        for (int i = 0; i < numDetections; i++) {
            float cx, cy, w, h;
            float personScore; //

            if (isTransposed) {
                // [1][8400][84]
                cx          = output[0][i][0];
                cy          = output[0][i][1];
                w           = output[0][i][2];
                h           = output[0][i][3];
                // index 4 + PERSON_CLASS_INDEX = index 4 = person score
                personScore = output[0][i][4 + PERSON_CLASS_INDEX];
            } else {
                // [1][84][8400]
                cx          = output[0][0][i];
                cy          = output[0][1][i];
                w           = output[0][2][i];
                h           = output[0][3][i];
                // row 4 + PERSON_CLASS_INDEX = row 4 = person score
                personScore = output[0][4 + PERSON_CLASS_INDEX][i];
            }


            if (personScore >= CONFIDENCE_THRESHOLD) {
                float left   = cx - w / 2f;
                float top    = cy - h / 2f;
                float right  = cx + w / 2f;
                float bottom = cy + h / 2f;

                left   = Math.max(0f, Math.min(1f, left));
                top    = Math.max(0f, Math.min(1f, top));
                right  = Math.max(0f, Math.min(1f, right));
                bottom = Math.max(0f, Math.min(1f, bottom));

                results.add(new Recognition(
                        new RectF(left, top, right, bottom), personScore));
            }
        }

        return applyNMS(results);
    }

    private List<Recognition> applyNMS(List<Recognition> dets) {
        dets.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
        List<Recognition> kept = new ArrayList<>();
        boolean[] suppressed = new boolean[dets.size()];
        for (int i = 0; i < dets.size(); i++) {
            if (suppressed[i]) continue;
            kept.add(dets.get(i));
            for (int j = i + 1; j < dets.size(); j++) {
                if (!suppressed[j] && computeIoU(
                        dets.get(i).getLocation(),
                        dets.get(j).getLocation()) > IOU_THRESHOLD) {
                    suppressed[j] = true;
                }
            }
        }
        return kept;
    }

    private float computeIoU(RectF a, RectF b) {
        float iL = Math.max(a.left, b.left);
        float iT = Math.max(a.top, b.top);
        float iR = Math.min(a.right, b.right);
        float iB = Math.min(a.bottom, b.bottom);
        float inter = Math.max(0, iR - iL) * Math.max(0, iB - iT);
        float aA = (a.right - a.left) * (a.bottom - a.top);
        float bA = (b.right - b.left) * (b.bottom - b.top);
        return inter / (aA + bA - inter + 1e-6f);
    }

    private ByteBuffer bitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer buf = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3);
        buf.order(ByteOrder.nativeOrder());
        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
        for (int p : pixels) {
            buf.putFloat(((p >> 16) & 0xFF) / 255.0f);
            buf.putFloat(((p >> 8)  & 0xFF) / 255.0f);
            buf.putFloat((p         & 0xFF) / 255.0f);
        }
        return buf;
    }
}
