package com.survivordetection.activities;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.location.Geocoder;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.GeoPoint;
import com.survivordetection.R;
import com.survivordetection.utils.YoloDetector;
import com.survivordetection.utils.OverlayView;
import com.survivordetection.utils.Recognition;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

    // ── Notification ──────────────────────────────────────────
    private static final String CHANNEL_ID    = "rescue_ai_alerts";
    private static final String CHANNEL_NAME  = "RescueAI Alerts";
    private static final int    NOTIF_ID      = 1001;

    // ── UI ────────────────────────────────────────────────────
    private PreviewView previewView;
    private OverlayView overlayView;
    private TextView    tvDetectionStatus, tvConfidence;
    private View        vStatusDot;
    private ImageView   ivFlash;

    // ── Camera ────────────────────────────────────────────────
    private CameraControl cameraControl;
    private boolean       flashOn = false;

    // ── Detection ─────────────────────────────────────────────
    private YoloDetector      detector;
    private final Handler     mainHandler       = new Handler(Looper.getMainLooper());
    private final ExecutorService inferenceExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean  isDetecting  = false;
    private volatile boolean  isProcessing = false;

    // ── Alert cooldown ────────────────────────────────────────
    private long lastAlertTime = 0;
    private static final long ALERT_COOLDOWN_MS = 3000;

    // ── Firebase ──────────────────────────────────────────────
    private FirebaseFirestore db;
    private FirebaseAuth      mAuth;
    private FusedLocationProviderClient fusedLocationClient;
    private android.location.Location  lastKnownLocation;

    // ── Audio ─────────────────────────────────────────────────
    private SoundPool soundPool;
    private int       beepSoundId = -1;
    private boolean   soundLoaded = false;
    private int beepCount = 0;

    // ─────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Firebase setup
        db = FirebaseFirestore.getInstance();
        try {
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true).build();
            db.setFirestoreSettings(settings);
        } catch (Exception e) { Log.d("Firestore", "Settings already set"); }

        mAuth               = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        // Notification channel create karo
        createNotificationChannel();

        // ✅ SoundPool initialize karo
        initSoundPool();

        initViews();
        startLocationUpdates();

        // YOLOv8 model load karo background thread pe
        inferenceExecutor.execute(() -> {
            detector = new YoloDetector(this, "yolov8n_float32.tflite");
            mainHandler.post(() -> {
                if (tvDetectionStatus != null)
                    tvDetectionStatus.setText("👁 Scanning...");
                isDetecting = true;
                if (allPermissionsGranted()) {
                    startCamera();
                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[]{
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                            }, 101);
                }
            });
        });
    }

    // ── Init views ────────────────────────────────────────────
    private void initViews() {
        previewView       = findViewById(R.id.previewView);
        overlayView       = findViewById(R.id.overlayView);
        tvDetectionStatus = findViewById(R.id.tvDetectionStatus);
        tvConfidence      = findViewById(R.id.tvConfidence);
        vStatusDot        = findViewById(R.id.vStatusDot);
        ivFlash           = findViewById(R.id.ivFlash);

        if (tvDetectionStatus != null)
            tvDetectionStatus.setText("⏳ Loading AI...");

        // ✅ survivorPopup NAHI hai — remove kiya
        // Sirf ye buttons hain
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        View btnFlash = findViewById(R.id.btnFlash);
        if (btnFlash != null) btnFlash.setOnClickListener(v -> toggleFlash());

        View btnSendAlert = findViewById(R.id.btnSendAlert);
        if (btnSendAlert != null)
            btnSendAlert.setOnClickListener(v -> sendFirebaseAlert(1));
    }

    // ── Start Camera ──────────────────────────────────────────
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 640))
                        .setBackpressureStrategy(
                                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(inferenceExecutor, imageProxy -> {
                    if (!isDetecting || isProcessing) {
                        imageProxy.close();
                        return;
                    }
                    isProcessing = true;
                    Bitmap bitmap = imageProxyToBitmap(imageProxy);
                    imageProxy.close();

                    if (bitmap == null) { isProcessing = false; return; }

                    Bitmap rotated = rotateBitmap(bitmap, 90);
                    List<Recognition> results = detector.detectWithBoxes(rotated);

                    mainHandler.post(() -> {
                        isProcessing = false;
                        if (results != null && !results.isEmpty()) {
                            int count = results.size();
                            int conf  = (int)(results.get(0).getConfidence() * 100);

                            if (overlayView != null)
                                overlayView.setResults(results);

                            if (tvDetectionStatus != null)
                                tvDetectionStatus.setText("🚨 SURVIVOR DETECTED!");
                            if (tvConfidence != null)
                                tvConfidence.setText(conf + "%");
                            if (vStatusDot != null)
                                vStatusDot.setBackgroundResource(R.drawable.circle_red);

                            // ✅ Popup nahi — sirf notification + beep + alert
                            triggerAlert(count, conf);

                        } else {
                            if (overlayView != null) overlayView.setResults(null);
                            if (tvDetectionStatus != null)
                                tvDetectionStatus.setText("Scanning for survivors...");
                            if (tvConfidence != null) tvConfidence.setText("");
                            if (vStatusDot != null)
                                vStatusDot.setBackgroundResource(R.drawable.circle_green);
                        }
                    });
                });

                Camera camera = cameraProvider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis);
                cameraControl = camera.getCameraControl();

            } catch (Exception e) {
                Log.e("Camera", "Error starting camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // ── Trigger Alert ─────────────────────────────────────────
    // Flow: Detect → Beep × 3 → 3 sec wait → Notification + Firebase
    private void triggerAlert(int survivorCount, int confidence) {
        long now = System.currentTimeMillis();
        if (now - lastAlertTime < ALERT_COOLDOWN_MS) return;
        lastAlertTime = now;

        // ── Step 1: Turant 3 beeps ────────────────────────────
        playBeep(0);
        playBeep(500);
        playBeep(1000);

        // ── Step 2: Vibration ─────────────────────────────────
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null) {
            long[] pattern = {0, 300, 150, 300, 150, 300};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        }

        // ── Step 3: 3 second baad notification + Firebase ─────
        mainHandler.postDelayed(() -> {
            // ✅ Notification bina internet ke bhi aayegi
            showNotification(survivorCount, confidence);

            // ✅ Firebase offline support — internet aane pe auto send
            sendFirebaseAlert(survivorCount);

        }, 3000);
    }

    // ── SoundPool init ────────────────────────────────────────
    private void initSoundPool() {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(attrs)
                .build();

        // beep.wav load karo res/raw se
        beepSoundId = soundPool.load(this, R.raw.beep, 1);

        soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            if (status == 0) soundLoaded = true;
        });
    }

    // ── Play 3 beeps ──────────────────────────────────────────
    private void playBeep(int delayMs) {
        mainHandler.postDelayed(() -> {
            if (!soundLoaded || soundPool == null) return;
            try {
                // Alarm volume max karo
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                if (am != null) {
                    int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                    am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0);
                }
                // Play beep — volume 1.0, no loop, normal speed
                soundPool.play(beepSoundId, 1.0f, 1.0f, 1, 0, 1.0f);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, delayMs);
    }

    // ── Notification ──────────────────────────────────────────
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("RescueAI survivor detection alerts");
            channel.enableVibration(true);
            channel.enableLights(true);

            NotificationManager manager =
                    getSystemService(NotificationManager.class);
            if (manager != null)
                manager.createNotificationChannel(channel);
        }
    }

    private void showNotification(int count, int confidence) {
        // Tap karne pe UserDashboard khulega
        Intent intent = new Intent(this, UserDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = "🚨 Alert Sent — " + count + " Survivor(s) Detected!";
        String body  = "Confidence: " + confidence + "% · Rescue team ko alert bhej diya gaya";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        // Android 13+ notification permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                NotificationManagerCompat.from(this).notify(NOTIF_ID, builder.build());
            }
        } else {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, builder.build());
        }
    }

    // ── Firebase Alert ────────────────────────────────────────
    private void sendFirebaseAlert(int survivorCount) {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            saveAlertToFirestore(survivorCount);
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) lastKnownLocation = location;
                    saveAlertToFirestore(survivorCount);
                })
                .addOnFailureListener(e -> saveAlertToFirestore(survivorCount));
    }

    private void saveAlertToFirestore(int survivorCount) {
        String uid = mAuth.getCurrentUser() != null
                ? mAuth.getCurrentUser().getUid() : "unknown";
        String timestamp = new SimpleDateFormat(
                "dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(new Date());

        Map<String, Object> alert = new HashMap<>();
        alert.put("reportedBy",    uid);
        alert.put("survivorCount", survivorCount);
        alert.put("timestamp",     timestamp);
        alert.put("status",        "pending");
        alert.put("type",          "Survivor Detected");

        if (lastKnownLocation != null) {
            double lat = lastKnownLocation.getLatitude();
            double lng = lastKnownLocation.getLongitude();
            alert.put("location",  new GeoPoint(lat, lng));
            alert.put("latitude",  lat);
            alert.put("longitude", lng);
            alert.put("address",   lat + ", " + lng);
        }

        // ✅ Offline support — Firebase automatically queue karega
        // Internet aane pe apne aap send ho jayega
        db.collection("alerts")
                .add(alert)
                .addOnSuccessListener(ref -> {
                    // Internet tha — turant send ho gaya
                    Log.d("Alert", "✅ Alert sent: " + ref.getId());
                })
                .addOnFailureListener(e -> {
                    // ✅ Offline hai — Firebase ne local mein save kar liya
                    // Internet aane pe automatically send hoga
                    Log.d("Alert", "📶 Offline — alert queued, will send when online");
                });
    }

    // ── Location ──────────────────────────────────────────────
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) lastKnownLocation = location;
                    });
        }
    }

    // ── Flash toggle ──────────────────────────────────────────
    private void toggleFlash() {
        if (cameraControl == null) return;
        flashOn = !flashOn;
        cameraControl.enableTorch(flashOn);
        if (ivFlash != null)
            ivFlash.setAlpha(flashOn ? 1.0f : 0.5f);
    }

    // ── Image conversion ──────────────────────────────────────
    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();
            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();
            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);
            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                    image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(
                    new Rect(0, 0, image.getWidth(), image.getHeight()), 90, out);
            return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size());
        } catch (Exception e) { return null; }
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    // ── Permissions ───────────────────────────────────────────
    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────
    @Override
    protected void onDestroy() {
        isDetecting = false;
        if (soundPool != null) { soundPool.release(); soundPool = null; }
        mainHandler.removeCallbacksAndMessages(null);
        inferenceExecutor.shutdown();
        super.onDestroy();
    }
}