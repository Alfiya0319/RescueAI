package com.survivordetection.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.survivordetection.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ImageUploadActivity extends AppCompatActivity {

    private SurvivorDetectionHelper detectionHelper;
    private LinearLayout layoutPickZone, layoutPreview, layoutProgress, layoutResult;
    private ImageView ivPreview, ivAnnotated, btnBack;
    private TextView tvSurvivorCount, tvProgressPct;
    private ProgressBar progressUpload;
    private Button btnDetectAndUpload, btnGenerateReport;

    private Uri selectedImageUri = null;
    private Bitmap resultBitmap = null; // AI Annotated Image stored here
    private int finalCount = 0;

    // --- Camera related new variables ---
    private static final int REQ_GALLERY = 101;
    private static final int REQ_CAMERA = 102;
    private String currentPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_image);

        detectionHelper = new SurvivorDetectionHelper(this);
        initViews();
    }

    private void initViews() {
        layoutPickZone = findViewById(R.id.layoutPickZone);
        layoutPreview = findViewById(R.id.layoutPreview);
        layoutProgress = findViewById(R.id.layoutProgress);
        layoutResult = findViewById(R.id.layoutResult);
        ivPreview = findViewById(R.id.ivPreview);
        ivAnnotated = findViewById(R.id.ivAnnotated);
        tvSurvivorCount = findViewById(R.id.tvSurvivorCount);
        tvProgressPct = findViewById(R.id.tvProgressPct);
        progressUpload = findViewById(R.id.progressUpload);
        btnDetectAndUpload = findViewById(R.id.btnDetectAndUpload);
        btnGenerateReport = findViewById(R.id.btnGenerateReport);
        btnBack = findViewById(R.id.btnBack);

        // 1. Back Button
        btnBack.setOnClickListener(v -> finish());

        // 2. Camera Button - Direct Camera
        findViewById(R.id.btnOpenCamera).setOnClickListener(v -> openCamera());

        // 3. Gallery Button - Direct Gallery
        findViewById(R.id.btnOpenGallery).setOnClickListener(v -> openGallery());

        // 4. Dash Zone - Show Both Options (Dialog)
        layoutPickZone.setOnClickListener(v -> showImagePickerOptions());

        // 5. Remove Image
        findViewById(R.id.btnRemoveImage).setOnClickListener(v -> resetScreen());

        // 6. Action Buttons
        btnDetectAndUpload.setOnClickListener(v -> startDetectionProcess());
        btnGenerateReport.setOnClickListener(v -> createPdfReport());
        findViewById(R.id.tvCancel).setOnClickListener(v -> finish());
    }
    private void showImagePickerOptions() {
        String[] options = {"Take Photo", "Choose from Gallery", "Cancel"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Image");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) openCamera();
            else if (which == 1) openGallery();
            else dialog.dismiss();
        });
        builder.show();
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQ_GALLERY);
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = null;
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
            Toast.makeText(this, "Error creating file", Toast.LENGTH_SHORT).show();
        }

        if (photoFile != null) {
            Uri photoURI = FileProvider.getUriForFile(this, getPackageName() + ".provider", photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            startActivityForResult(takePictureIntent, REQ_CAMERA);
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile("JPEG_" + timeStamp + "_", ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            layoutPickZone.setVisibility(View.GONE);
            layoutPreview.setVisibility(View.VISIBLE);

            if (requestCode == REQ_GALLERY && data != null) {
                selectedImageUri = data.getData();
            } else if (requestCode == REQ_CAMERA) {
                File f = new File(currentPhotoPath);
                selectedImageUri = Uri.fromFile(f);
            }

            Glide.with(this).load(selectedImageUri).into(ivPreview);
        }
    }

    private void startDetectionProcess() {
        if (selectedImageUri == null) {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
            return;
        }

        layoutProgress.setVisibility(View.VISIBLE);
        tvProgressPct.setText("AI Processing...");
        progressUpload.setIndeterminate(true);

        detectionHelper.detectSurvivors(selectedImageUri, new SurvivorDetectionHelper.DetectionCallback() {
            @Override
            public void onSuccess(int count, Bitmap bmp) {
                finalCount = count;
                resultBitmap = bmp;

                runOnUiThread(() -> {
                    layoutProgress.setVisibility(View.GONE);
                    layoutResult.setVisibility(View.VISIBLE);
                    ivAnnotated.setImageBitmap(bmp);
                    tvSurvivorCount.setText(count + " Survivor(s) Located");
                    btnGenerateReport.setVisibility(View.VISIBLE);

                    // Trigger Firebase Upload
                    uploadToFirebase(count, bmp);
                });
            }

            @Override
            public void onFailure(String err) {
                runOnUiThread(() -> {
                    layoutProgress.setVisibility(View.GONE);
                    Toast.makeText(ImageUploadActivity.this, "Detection Error: " + err, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void uploadToFirebase(int count, Bitmap bmp) {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        StorageReference storageRef = FirebaseStorage.getInstance().getReference().child("Detections/IMG_" + ts + ".jpg");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] data = baos.toByteArray();

        storageRef.putBytes(data).addOnSuccessListener(taskSnapshot -> {
            storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("RescueReports").push();
                Map<String, Object> reportData = new HashMap<>();
                reportData.put("survivor_count", count);
                reportData.put("image_url", uri.toString());
                reportData.put("timestamp", ts);
                reportData.put("status", "Active");
                dbRef.setValue(reportData);
                Toast.makeText(this, "Cloud Synced Successfully", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void createPdfReport() {
        if (resultBitmap == null) return;

        PdfDocument document = new PdfDocument();
        // A4 size PageInfo
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();

        String timestamp = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(new Date());
        String reportId = "AIDET-" + (System.currentTimeMillis() % 10000);

        // --- 1. HEADER (Branding) ---
        paint.setColor(Color.parseColor("#FF4D6D")); // Theme Pink
        paint.setFakeBoldText(true);
        paint.setTextSize(26f);
        canvas.drawText("RESCUE AI: SURVIVOR REPORT", 40, 60, paint);

        paint.setColor(Color.GRAY);
        paint.setTextSize(12f);
        paint.setFakeBoldText(false);
        canvas.drawText("Dept. of Computer Engineering | Disaster Response Unit", 40, 85, paint);

        paint.setColor(Color.LTGRAY);
        canvas.drawLine(40, 100, 555, 100, paint);

        // --- 2. REPORT METADATA ---
        paint.setColor(Color.BLACK);
        paint.setFakeBoldText(true);
        paint.setTextSize(14f);
        canvas.drawText("MISSION DATA", 40, 130, paint);

        paint.setFakeBoldText(false);
        paint.setTextSize(11f);
        canvas.drawText("Report ID: " + reportId, 40, 155, paint);
        canvas.drawText("Generation Date: " + timestamp, 40, 175, paint);
        canvas.drawText("Priority Level: HIGH", 350, 155, paint);
        canvas.drawText("AI Confidence: High Accuracy", 350, 175, paint);

        // --- 3. DETECTION SUMMARY BOX ---
        paint.setColor(Color.parseColor("#F8F9FA"));
        canvas.drawRect(40, 200, 555, 260, paint);

        paint.setColor(Color.parseColor("#FF4D6D"));
        paint.setFakeBoldText(true);
        paint.setTextSize(18f);
        canvas.drawText("TOTAL SURVIVORS FOUND: " + finalCount, 60, 238, paint);

        // --- 4. VISUAL PROOF (The Image) ---
        paint.setColor(Color.BLACK);
        paint.setTextSize(13f);
        canvas.drawText("AI ANNOTATED EVIDENCE:", 40, 290, paint);

        // Fit image within bounds
        int maxWidth = 515;
        int targetHeight = (resultBitmap.getHeight() * maxWidth) / resultBitmap.getWidth();
        if (targetHeight > 450) targetHeight = 450; // Cap height

        Bitmap scaledBmp = Bitmap.createScaledBitmap(resultBitmap, maxWidth, targetHeight, true);
        canvas.drawBitmap(scaledBmp, 40, 310, null);

        // --- 5. FOOTER ---
        int footerPos = 310 + targetHeight + 50;
        paint.setColor(Color.LTGRAY);
        canvas.drawLine(40, footerPos, 555, footerPos, paint);

        paint.setColor(Color.GRAY);
        paint.setTextSize(9f);
        canvas.drawText("Note: This is an automated AI report for emergency response.", 40, footerPos + 20, paint);
        canvas.drawText("Property of RescueAI Mobile Systems", 40, footerPos + 35, paint);

        document.finishPage(page);

        // Save File
        File file = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Report_" + reportId + ".pdf");
        try {
            document.writeTo(new FileOutputStream(file));
            Toast.makeText(this, "Report Generated & Saved", Toast.LENGTH_SHORT).show();
            openPdf(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        document.close();
    }

    private void openPdf(File file) {
        Uri path = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(path, "application/pdf");
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private void resetScreen() {
        selectedImageUri = null;
        resultBitmap = null;
        layoutPickZone.setVisibility(View.VISIBLE);
        layoutPreview.setVisibility(View.GONE);
        layoutResult.setVisibility(View.GONE);
        btnGenerateReport.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (detectionHelper != null) detectionHelper.close();
    }
}