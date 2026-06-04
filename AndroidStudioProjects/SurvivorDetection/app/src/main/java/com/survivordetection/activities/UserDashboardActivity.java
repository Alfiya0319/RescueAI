package com.survivordetection.activities;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.camera2.CameraManager;
import android.location.Address;
import android.location.Geocoder;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.survivordetection.R;
import com.survivordetection.adapters.RescueTeamAdapter;
import com.survivordetection.models.RescueTeam;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.UnitValue;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class UserDashboardActivity extends AppCompatActivity {

    // ── UI ────────────────────────────────────────────────────
    private TextView     tvUserName, tvLastAlertLocation, tvCoordinates;
    private RecyclerView rvRescueTeams;
    private View         radarRing1, radarRing2;

    // ── Firebase ──────────────────────────────────────────────
    private FirebaseAuth      mAuth;
    private FirebaseFirestore db;

    // ── Location ──────────────────────────────────────────────
    private FusedLocationProviderClient fusedLocation;
    private double currentUserLat = 0;
    private double currentUserLon = 0;

    // ── Siren ─────────────────────────────────────────────────
    private MediaPlayer   mediaPlayer;
    private CameraManager cameraManager;
    private String        cameraId;
    private boolean       isSirenOn    = false;
    private boolean       flashState   = false;
    private final Handler sirenHandler = new Handler();
    private int           sirenCycle   = 0;

    // ── Rescue Teams ──────────────────────────────────────────
    private List<RescueTeam>  teamList;
    private RescueTeamAdapter adapter;

    // ── User info ─────────────────────────────────────────────
    private String currentUserName = "User";
    private String currentUserUid  = "";
    private String currentLocation = "";
    private String currentCoords   = "";

    // ── SMS ───────────────────────────────────────────────────
    // Package name prefix lagao — conflicts avoid karne ke liye
    private static final String SMS_SENT      = "com.survivordetection.SMS_SENT";
    private static final String SMS_DELIVERED = "com.survivordetection.SMS_DELIVERED";
    private BroadcastReceiver   smsSentReceiver;
    private BroadcastReceiver   smsDeliveredReceiver;

    // ── Permission codes ──────────────────────────────────────
    private static final int REQ_SMS      = 101;
    private static final int REQ_LOCATION = 100;
    private static final int REQ_PHONE    = 102;

    // ─────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_dashboard);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        try {
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build();
            db.setFirestoreSettings(settings);
        } catch (Exception e) {
            Log.d("Firestore", "Settings initialized");
        }

        fusedLocation = LocationServices.getFusedLocationProviderClient(this);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        registerSmsReceivers();
        initViews();
        startRadarAnimations();
        loadUserProfile();
        loadGpsLocation();
        setupListeners();
    }

    // ══════════════════════════════════════════════════════════
    //  SMS RECEIVERS
    // ══════════════════════════════════════════════════════════
    private void registerSmsReceivers() {

        smsSentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (getResultCode()) {
                    case RESULT_OK:
                        Toast.makeText(context,
                                "✅ SOS SMS Sent Successfully!", Toast.LENGTH_LONG).show();
                        Log.d("SMS_STATUS", "SMS sent successfully");
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        Toast.makeText(context,
                                "❌ SMS Failed: Generic Error", Toast.LENGTH_LONG).show();
                        Log.e("SMS_STATUS", "Generic failure");
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        Toast.makeText(context,
                                "❌ SMS Failed: No Network Service", Toast.LENGTH_LONG).show();
                        Log.e("SMS_STATUS", "No service");
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        Toast.makeText(context,
                                "❌ SMS Failed: Null PDU", Toast.LENGTH_LONG).show();
                        Log.e("SMS_STATUS", "Null PDU");
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        Toast.makeText(context,
                                "❌ SMS Failed: Radio/SIM Off. Check SIM.",
                                Toast.LENGTH_LONG).show();
                        Log.e("SMS_STATUS", "Radio off");
                        break;
                }
            }
        };

        smsDeliveredReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (getResultCode()) {
                    case RESULT_OK:
                        Toast.makeText(context,
                                "📱 SOS SMS Delivered!", Toast.LENGTH_SHORT).show();
                        break;
                    case RESULT_CANCELED:
                        Toast.makeText(context,
                                "⚠️ SMS sent but delivery not confirmed",
                                Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        };

        // ContextCompat handles API 33+ flag automatically
        ContextCompat.registerReceiver(this, smsSentReceiver,
                new IntentFilter(SMS_SENT),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, smsDeliveredReceiver,
                new IntentFilter(SMS_DELIVERED),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    // ══════════════════════════════════════════════════════════
    //  TRIGGER EMERGENCY SOS — Dual SIM Fixed
    // ══════════════════════════════════════════════════════════
    private void triggerEmergencySOS() {
        // Check Permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, 103);
            return;
        }

        List<String> favoriteNumbers = getFavoriteContacts();

        if (favoriteNumbers.isEmpty()) {
            Toast.makeText(this, "⚠️ No Favorite Contacts found!", Toast.LENGTH_SHORT).show();
            sendFinalSOS("+919049312591"); // Default backup
            return;
        }

        for (String number : favoriteNumbers) {
            sendFinalSOS(number);
        }

        Toast.makeText(this, "SOS Sent to " + favoriteNumbers.size() + " Contacts", Toast.LENGTH_LONG).show();
    }

    private List<String> getFavoriteContacts() {
        List<String> favorites = new ArrayList<>();
        Uri uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String selection = android.provider.ContactsContract.Contacts.STARRED + "='1'";

        android.database.Cursor cursor = getContentResolver().query(uri, null, selection, null, null);

        if (cursor != null) {
            int numIdx = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER);
            while (cursor.moveToNext()) {
                String number = cursor.getString(numIdx);
                if (number != null) {
                    number = number.replaceAll("\\s+", ""); // Clean spaces
                    if (!favorites.contains(number)) {
                        favorites.add(number);
                    }
                }
            }
            cursor.close();
        }
        return favorites;
    }

    private void sendFinalSOS(String targetNumber) {
        String locationText = "http://maps.google.com/?q=" + currentUserLat + "," + currentUserLon;
        String message = "SOS! Survivor " + currentUserName + " needs help! Location: " + locationText;

        try {
            SmsManager smsManager = getSmsManagerForDefaultSim();
            int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
            PendingIntent sentPI = PendingIntent.getBroadcast(this, 0, new Intent(SMS_SENT), piFlags);
            PendingIntent deliveredPI = PendingIntent.getBroadcast(this, 0, new Intent(SMS_DELIVERED), piFlags);

            smsManager.sendTextMessage(targetNumber, null, message, sentPI, deliveredPI);
        } catch (Exception e) {
            Log.e("SOS_ERROR", "Failed for " + targetNumber + ": " + e.getMessage());
        }
    }

    // ── Correct SIM ke liye SmsManager lao ───────────────────
    private SmsManager getSmsManagerForDefaultSim() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_PHONE_STATE)
                        == PackageManager.PERMISSION_GRANTED) {

                    SubscriptionManager subManager =
                            (SubscriptionManager) getSystemService(
                                    Context.TELEPHONY_SUBSCRIPTION_SERVICE);

                    if (subManager != null) {
                        int defaultSubId =
                                SubscriptionManager.getDefaultSmsSubscriptionId();
                        Log.d("SIM_INFO", "Default SMS SubID: " + defaultSubId);

                        // Debug — active SIMs print karo
                        List<SubscriptionInfo> subs =
                                subManager.getActiveSubscriptionInfoList();
                        if (subs != null) {
                            for (SubscriptionInfo info : subs) {
                                Log.d("SIM_INFO", "Slot: " + info.getSimSlotIndex()
                                        + " SubID: " + info.getSubscriptionId()
                                        + " Carrier: " + info.getCarrierName());
                            }
                        }

                        if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                return getSystemService(SmsManager.class)
                                        .createForSubscriptionId(defaultSubId);
                            } else {
                                return SmsManager
                                        .getSmsManagerForSubscriptionId(defaultSubId);
                            }
                        }
                    }
                } else {
                    // Permission nahi — request karo
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.READ_PHONE_STATE},
                            REQ_PHONE);
                    Log.w("SIM_INFO", "READ_PHONE_STATE not granted, using default");
                }
            }
        } catch (Exception e) {
            Log.e("SIM_INFO", "getSmsManager error: " + e.getMessage());
        }

        // Fallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return getSystemService(SmsManager.class);
        } else {
            return SmsManager.getDefault();
        }
    }

    // ── Actual SMS send helper ────────────────────────────────
    private void sendSmsWithManager(SmsManager smsManager, String number,
                                    String message, PendingIntent sentPI, PendingIntent deliveredPI) {
        try {
            if (message.length() > 160) {
                ArrayList<String> parts           = smsManager.divideMessage(message);
                ArrayList<PendingIntent> sentList = new ArrayList<>();
                ArrayList<PendingIntent> dlvList  = new ArrayList<>();
                sentList.add(sentPI);
                dlvList.add(deliveredPI);
                for (int i = 1; i < parts.size(); i++) {
                    sentList.add(null);
                    dlvList.add(null);
                }
                smsManager.sendMultipartTextMessage(
                        number, null, parts, sentList, dlvList);
                Log.d("SOS_SMS", "Multipart SMS: " + parts.size() + " parts");
            } else {
                smsManager.sendTextMessage(number, null, message, sentPI, deliveredPI);
                Log.d("SOS_SMS", "SMS submitted to: " + number);
            }
            showEmergencyOverlay();
        } catch (Exception e) {
            Log.e("SOS_ERROR", "sendSmsWithManager: " + e.getMessage(), e);
            Toast.makeText(this,
                    "❌ Send failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ── Emergency overlay ─────────────────────────────────────
    private void showEmergencyOverlay() {
        View rootView = getWindow().getDecorView().findViewById(android.R.id.content);
        if (rootView != null) {
            ObjectAnimator colorAnim = ObjectAnimator.ofInt(rootView,
                    "backgroundColor",
                    Color.parseColor("#FF0000"),
                    Color.parseColor("#330000"));
            colorAnim.setDuration(400);
            colorAnim.setEvaluator(new android.animation.ArgbEvaluator());
            colorAnim.setRepeatCount(15);
            colorAnim.setRepeatMode(ObjectAnimator.REVERSE);
            colorAnim.start();
        }
        playSOSSound();
    }

    private void playSOSSound() {
        if (mediaPlayer != null) {
            try { mediaPlayer.release(); } catch (Exception ignored) {}
        }
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.beep);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(false);
                mediaPlayer.start();
            }
        } catch (Exception e) {
            Log.e("SOS_SOUND", "Sound error: " + e.getMessage());
        }
    }

    // ── Init views ────────────────────────────────────────────
    private void initViews() {
        tvUserName          = findViewById(R.id.tvUserName);
        tvLastAlertLocation = findViewById(R.id.tvLastAlertLocation);
        tvCoordinates       = findViewById(R.id.tvCoordinates);
        rvRescueTeams       = findViewById(R.id.rvRescueTeams);
        radarRing1          = findViewById(R.id.radarRing1);
        radarRing2          = findViewById(R.id.radarRing2);

        TextView tvAppName = findViewById(R.id.tvAppName);
        if (tvAppName != null) {
            String text = "Rescue AI";
            SpannableString ss = new SpannableString(text);
            ss.setSpan(new ForegroundColorSpan(Color.WHITE),
                    0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new ForegroundColorSpan(Color.parseColor("#FF4D6D")),
                    7, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            tvAppName.setText(ss);
        }

        teamList = new ArrayList<>();
        if (rvRescueTeams != null) {
            rvRescueTeams.setLayoutManager(new LinearLayoutManager(this));
            rvRescueTeams.setNestedScrollingEnabled(false);
            adapter = new RescueTeamAdapter(this, teamList);
            rvRescueTeams.setAdapter(adapter);
        }
    }

    // ── Listeners ─────────────────────────────────────────────
    private void setupListeners() {
        safeClick(R.id.btnStartDetection,
                v -> startActivity(new Intent(this, CameraActivity.class)));
        safeClick(R.id.btnUploadImage,
                v -> startActivity(new Intent(this, ImageUploadActivity.class)));
        safeClick(R.id.btnSOS,
                v -> triggerEmergencySOS());
        safeClick(R.id.btnPanicSiren,
                v -> togglePanicSiren());
        safeClick(R.id.btnGeneratePdf,
                v -> generateSafetyLog());
        safeClick(R.id.navHome,
                v -> toast("Already on Home"));
        safeClick(R.id.navMap,
                v -> startActivity(new Intent(this, MapActivity.class)));
        safeClick(R.id.navProfile,
                v -> startActivity(new Intent(this, ProfileUserActivity.class)));
        safeClick(R.id.navHistory,
                v -> startActivity(new Intent(this, HistoryActivity.class)));
        safeClick(R.id.navAbout,
                v -> startActivity(new Intent(this, MainActivity.class)));
    }

    // ── Load user profile ─────────────────────────────────────
    private void loadUserProfile() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;
        currentUserUid = user.getUid();

        com.google.firebase.database.FirebaseDatabase dbInstance =
                com.google.firebase.database.FirebaseDatabase.getInstance(
                        "https://ai-surviour-default-rtdb.asia-southeast1.firebasedatabase.app");
        com.google.firebase.database.DatabaseReference mDatabase =
                dbInstance.getReference("users");

        mDatabase.child(currentUserUid)
                .addValueEventListener(
                        new com.google.firebase.database.ValueEventListener() {
                            @Override
                            public void onDataChange(
                                    com.google.firebase.database.DataSnapshot snapshot) {
                                if (snapshot.exists()) {
                                    String name = snapshot.child("name").getValue(String.class);
                                    if (name != null && !name.isEmpty()) {
                                        currentUserName = name;
                                        if (tvUserName != null) tvUserName.setText(currentUserName);
                                    }
                                } else {
                                    if (tvUserName != null) tvUserName.setText("Guest User");
                                }
                            }

                            @Override
                            public void onCancelled(
                                    com.google.firebase.database.DatabaseError error) {
                                Log.e("UserDash", "DB Error: " + error.getMessage());
                            }
                        });
    }

    // ── Load GPS location ─────────────────────────────────────
    private void loadGpsLocation() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            return;
        }

        fusedLocation.getLastLocation().addOnSuccessListener(location -> {
            if (location == null) { Log.w("GPS", "Location null"); return; }
            currentUserLat = location.getLatitude();
            currentUserLon = location.getLongitude();
            currentCoords  = String.format(Locale.getDefault(),
                    "Lat: %.4f, Lon: %.4f", currentUserLat, currentUserLon);
            if (tvCoordinates != null) tvCoordinates.setText(currentCoords);
            loadRescueTeams();

            // Background thread pe Geocoder
            new Thread(() -> {
                try {
                    Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(
                            currentUserLat, currentUserLon, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        currentLocation = addresses.get(0).getAddressLine(0);
                        runOnUiThread(() -> {
                            if (tvLastAlertLocation != null)
                                tvLastAlertLocation.setText(currentLocation);
                        });
                    }
                } catch (Exception e) {
                    currentLocation = "Location Saved (Offline)";
                    runOnUiThread(() -> {
                        if (tvLastAlertLocation != null)
                            tvLastAlertLocation.setText(currentLocation);
                    });
                }
            }).start();
        });
    }

    // ── Load rescue teams ─────────────────────────────────────
    private void loadRescueTeams() {
        db.collection("users").whereEqualTo("role", "rescuer")
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshots, e) -> {
                    if (snapshots == null || teamList == null) return;
                    teamList.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        RescueTeam team = doc.toObject(RescueTeam.class);
                        if (currentUserLat != 0 && team.getLatitude() != 0) {
                            float[] results = new float[1];
                            android.location.Location.distanceBetween(
                                    currentUserLat, currentUserLon,
                                    team.getLatitude(), team.getLongitude(), results);
                            team.setDistance(results[0] / 1000);
                        } else {
                            team.setDistance(9999);
                        }
                        teamList.add(team);
                    }
                    java.util.Collections.sort(teamList,
                            (t1, t2) -> Float.compare(t1.getDistance(), t2.getDistance()));
                    if (adapter != null) adapter.notifyDataSetChanged();
                });
    }

    // ══ GENERATE SAFETY LOG PDF ═══════════════════════════════
    private void generateSafetyLog() {
        toast("Generating Safety Log...");
        db.collection("alerts")
                .whereEqualTo("reportedBy", currentUserUid)
                .orderBy("timestamp",
                        com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(q -> buildSafetyLogPdf(q.getDocuments()))
                .addOnFailureListener(e -> {
                    Log.d("SafetyLog", "Online fail: " + e.getMessage());
                    buildSafetyLogPdf(new ArrayList<>());
                });
    }

    private void buildSafetyLogPdf(
            List<com.google.firebase.firestore.DocumentSnapshot> docs) {
        String ts       = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        String fileName = "SafetyLog_" + ts + ".pdf";
        File   pdfFile  = new File(getExternalFilesDir(null), fileName);

        try {
            PdfWriter   writer   = new PdfWriter(pdfFile.getAbsolutePath());
            PdfDocument pdf      = new PdfDocument(writer);
            Document    document = new Document(pdf);
            DeviceRgb   pink     = new DeviceRgb(255, 77, 109);
            DeviceRgb   gray     = new DeviceRgb(100, 116, 139);

            document.add(new Paragraph("RESCUEAI — PERSONAL SAFETY LOG")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(18).setBold().setFontColor(pink));
            document.add(new Paragraph("Disaster Survivor Personal Activity Report")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(11).setFontColor(gray));
            document.add(new LineSeparator(new SolidLine()));
            document.add(new Paragraph("\n"));

            String reportTime = new SimpleDateFormat("dd MMM yyyy, HH:mm:ss",
                    Locale.getDefault()).format(new Date());

            document.add(new Paragraph("USER INFORMATION").setBold().setFontColor(pink));
            Table infoTable = new Table(new float[]{200f, 300f});
            infoTable.setWidth(UnitValue.createPercentValue(100));
            infoRow(infoTable, "Name", currentUserName);
            infoRow(infoTable, "User ID",
                    currentUserUid.length() > 12
                            ? currentUserUid.substring(0, 12) + "..." : currentUserUid);
            infoRow(infoTable, "Current Location",
                    currentLocation.isEmpty() ? "Fetching..." : currentLocation);
            infoRow(infoTable, "GPS Coordinates",
                    currentCoords.isEmpty() ? "Fetching..." : currentCoords);
            infoRow(infoTable, "Report Generated", reportTime);
            document.add(infoTable);
            document.add(new Paragraph("\n"));

            document.add(new Paragraph("ACTIVITY SUMMARY").setBold().setFontColor(pink));
            int totalAlerts = docs.size(), totalSurvivors = 0, sosCount = 0;
            for (com.google.firebase.firestore.DocumentSnapshot doc : docs) {
                Long count = doc.getLong("survivorCount");
                if (count != null) totalSurvivors += count.intValue();
                if ("SOS".equalsIgnoreCase(doc.getString("type"))) sosCount++;
            }
            Table summaryTable = new Table(new float[]{250f, 250f});
            summaryTable.setWidth(UnitValue.createPercentValue(100));
            infoRow(summaryTable, "Total Alerts Reported",    String.valueOf(totalAlerts));
            infoRow(summaryTable, "Total Survivors Detected", String.valueOf(totalSurvivors));
            infoRow(summaryTable, "SOS Alerts Sent",          String.valueOf(sosCount));
            document.add(summaryTable);
            document.add(new Paragraph("\n"));

            if (!docs.isEmpty()) {
                document.add(new Paragraph("ALERT HISTORY").setBold().setFontColor(pink));
                Table alertTable = new Table(new float[]{130f, 80f, 70f, 70f, 150f});
                alertTable.setWidth(UnitValue.createPercentValue(100));
                for (String h : new String[]{
                        "Timestamp","Type","Survivors","Status","Location"}) {
                    alertTable.addHeaderCell(new Cell()
                            .add(new Paragraph(h).setBold().setFontSize(8))
                            .setBackgroundColor(pink)
                            .setFontColor(ColorConstants.WHITE));
                }
                boolean alt = false;
                for (com.google.firebase.firestore.DocumentSnapshot doc : docs) {
                    DeviceRgb row = alt
                            ? new DeviceRgb(250, 250, 250)
                            : new DeviceRgb(255, 255, 255);
                    alt = !alt;
                    Long   survivors = doc.getLong("survivorCount");
                    String status    = doc.getString("status");
                    String address   = doc.getString("address");
                    
                    // Handle both String and Timestamp for timestamp field
                    String timestampStr = "";
                    Object tsObj = doc.get("timestamp");
                    if (tsObj instanceof com.google.firebase.Timestamp) {
                        timestampStr = new SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
                                .format(((com.google.firebase.Timestamp) tsObj).toDate());
                    } else {
                        timestampStr = doc.getString("timestamp");
                    }
                    
                    alertTable.addCell(pdfCell(timestampStr, row));
                    alertTable.addCell(pdfCell(doc.getString("type"), row));
                    alertTable.addCell(pdfCell(
                            survivors != null ? String.valueOf(survivors) : "0", row));
                    alertTable.addCell(pdfCell(
                            status != null ? status.toUpperCase() : "—", row));
                    alertTable.addCell(pdfCell(
                            address != null && address.length() > 25
                                    ? address.substring(0, 25) + "..." : address, row));
                }
                document.add(alertTable);
                document.add(new Paragraph("\n"));
            } else {
                document.add(new Paragraph(
                        "No alert history found. (Offline mode shows cached data only)")
                        .setFontColor(gray).setFontSize(10));
                document.add(new Paragraph("\n"));
            }

            document.add(new Paragraph("SAFETY GUIDELINES").setBold().setFontColor(pink));
            document.add(new Paragraph(
                    "1. Always stay in contact with the rescue team.\n" +
                            "2. Keep GPS on so your location can be tracked.\n" +
                            "3. Use the SOS button in emergencies.\n" +
                            "4. Detect nearby survivors using RescueAI.\n" +
                            "5. Share this report with the rescue team.")
                    .setFontSize(10).setFontColor(gray));
            document.add(new Paragraph("\n\n"));

            document.add(new LineSeparator(new SolidLine()));
            Table footer = new Table(2);
            footer.setWidth(UnitValue.createPercentValue(100));
            footer.addCell(new Cell()
                    .add(new Paragraph(
                            "\n\n__________________________\n" + currentUserName))
                    .setBorder(Border.NO_BORDER)
                    .setTextAlignment(TextAlignment.CENTER));
            footer.addCell(new Cell()
                    .add(new Paragraph("\n\n__________________________\nRescueAI System"))
                    .setBorder(Border.NO_BORDER)
                    .setTextAlignment(TextAlignment.CENTER));
            document.add(footer);
            document.add(new Paragraph("Generated by RescueAI · " + reportTime)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(8).setFontColor(gray));
            document.close();

            Uri    uri    = FileProvider.getUriForFile(this,
                    getPackageName() + ".provider", pdfFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
            toast("✅ Safety Log ready: " + fileName);

        } catch (Exception e) {
            Log.e("SafetyLog", "PDF Error: " + e.getMessage());
            toast("PDF generate fail: " + e.getMessage());
        }
    }

    private void infoRow(Table table, String label, String value) {
        table.addCell(new Cell()
                .add(new Paragraph(label).setBold().setFontSize(10)));
        table.addCell(new Cell()
                .add(new Paragraph(value != null ? value : "—").setFontSize(10)));
    }

    private Cell pdfCell(String text, DeviceRgb bg) {
        return new Cell()
                .add(new Paragraph(text != null ? text : "—").setFontSize(8))
                .setBackgroundColor(bg);
    }

    // ── Siren ─────────────────────────────────────────────────
    private void togglePanicSiren() {
        if (!isSirenOn) startSiren(); else stopSiren();
    }

    private void startSiren() {
        try {
            cameraId    = cameraManager.getCameraIdList()[0];
            mediaPlayer = MediaPlayer.create(this, R.raw.siren);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(true);
                mediaPlayer.start();
            }
            isSirenOn  = true;
            sirenCycle = 0;
            toast("🚨 Emergency Siren Activated!");
            updateSirenButton(true);
            startFlashBlink();
        } catch (Exception e) {
            toast("Siren error: " + e.getMessage());
        }
    }

    private void startFlashBlink() {
        sirenHandler.removeCallbacksAndMessages(null);
        Runnable blinkRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isSirenOn) return;
                try {
                    switch (sirenCycle % 5) {
                        case 0: setFlash(true);  sirenHandler.postDelayed(this, 100); break;
                        case 1: setFlash(false); sirenHandler.postDelayed(this, 100); break;
                        case 2: setFlash(true);  sirenHandler.postDelayed(this, 100); break;
                        case 3: setFlash(false); sirenHandler.postDelayed(this, 100); break;
                        case 4: setFlash(false); sirenHandler.postDelayed(this, 400); break;
                    }
                    sirenCycle++;
                } catch (Exception e) {
                    Log.e("Siren", "Flash error: " + e.getMessage());
                }
            }
        };
        sirenHandler.post(blinkRunnable);
    }

    private void setFlash(boolean on) {
        try {
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, on);
                flashState = on;
            }
        } catch (Exception ignored) {}
    }

    private void stopSiren() {
        isSirenOn = false;
        sirenHandler.removeCallbacksAndMessages(null);
        setFlash(false);
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); mediaPlayer.release(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        updateSirenButton(false);
        toast("Siren Deactivated");
    }

    private void updateSirenButton(boolean active) {
        View btn = findViewById(R.id.btnPanicSiren);
        if (btn instanceof android.widget.Button) {
            android.widget.Button b = (android.widget.Button) btn;
            if (active) {
                b.setText("DEACTIVATE SIREN 🔴");
                b.setTextColor(Color.parseColor("#FF4D6D"));
            } else {
                b.setText("ACTIVATE PANIC SIREN");
                b.setTextColor(Color.WHITE);
            }
        }
    }

    // ── Radar animations ──────────────────────────────────────
    private void startRadarAnimations() {
        animateRing(radarRing1, 2000, 1.5f);
        animateRing(radarRing2, 3000, 1.8f);
    }

    private void animateRing(View view, int duration, float scale) {
        if (view == null) return;
        ObjectAnimator p = ObjectAnimator.ofPropertyValuesHolder(view,
                PropertyValuesHolder.ofFloat("scaleX", 1.0f, scale),
                PropertyValuesHolder.ofFloat("scaleY", 1.0f, scale),
                PropertyValuesHolder.ofFloat("alpha",  0.4f, 0.0f));
        p.setDuration(duration);
        p.setRepeatCount(ObjectAnimator.INFINITE);
        p.start();
    }

    // ── Lifecycle ─────────────────────────────────────────────
    @Override
    protected void onPause() {
        super.onPause();
        if (isSirenOn) stopSiren();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (smsSentReceiver != null)     unregisterReceiver(smsSentReceiver);
            if (smsDeliveredReceiver != null) unregisterReceiver(smsDeliveredReceiver);
        } catch (Exception ignored) {}
        sirenHandler.removeCallbacksAndMessages(null);
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        setFlash(false);
    }

    // ── Helpers ───────────────────────────────────────────────
    private void safeClick(int id, View.OnClickListener l) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(l);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_SMS) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                triggerEmergencySOS();
            } else {
                Toast.makeText(this,
                        "❌ SMS Permission Denied! SOS can't be sent.",
                        Toast.LENGTH_LONG).show();
            }
        }
        if (requestCode == REQ_LOCATION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadGpsLocation();
            }
        }
        if (requestCode == REQ_PHONE) {
            // Permission mili — SOS dobara try karo
            triggerEmergencySOS();
        }
    }
}