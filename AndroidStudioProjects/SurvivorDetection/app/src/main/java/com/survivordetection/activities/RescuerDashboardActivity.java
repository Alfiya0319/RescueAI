package com.survivordetection.activities;

import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.survivordetection.R;
import com.survivordetection.adapters.AlertAdapter;
import com.survivordetection.models.AlertModel;

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
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RescuerDashboardActivity extends AppCompatActivity {

    // ── Views — exact XML IDs ─────────────────────────────────
    private TextView     tvRescuerName, tvTotalAlerts, tvRescued, tvNotifBadge;
    private RecyclerView rvLiveAlerts;
    private LinearLayout emptyState;
    private Button       btnNavigate, btnMarkRescued;
    private CardView     btnProfile;

    // ── Firebase ──────────────────────────────────────────────
    private FirebaseAuth      mAuth;
    private FirebaseFirestore db;

    // ── Data ──────────────────────────────────────────────────
    private List<AlertModel> alertList      = new ArrayList<>();
    private AlertAdapter     alertAdapter;
    private long             lastAlertCount = 0;

    // ── Selected alert for navigate/rescue ───────────────────
    private AlertModel selectedAlert = null;

    // ─────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rescuer_dashboard);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        // Offline persistence
        try {
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true).build();
            db.setFirestoreSettings(settings);
        } catch (Exception e) {
            Log.d("Firebase", "Settings already initialized");
        }

        initViews();
        setupRecyclerView();
        loadRescuerProfile();
        listenToLiveAlerts();
        fetchRescuedStats();
        setupListeners();
    }

    // ── Init views ────────────────────────────────────────────
    private void initViews() {
        tvRescuerName  = findViewById(R.id.tvRescuerName);
        tvTotalAlerts  = findViewById(R.id.tvTotalAlerts);
        tvRescued      = findViewById(R.id.tvRescued);
        tvNotifBadge   = findViewById(R.id.tvNotifBadge);
        rvLiveAlerts   = findViewById(R.id.rvLiveAlerts);
        emptyState     = findViewById(R.id.emptyState);
        btnNavigate    = findViewById(R.id.btnNavigate);
        btnMarkRescued = findViewById(R.id.btnMarkRescued);
        btnProfile     = findViewById(R.id.btnProfile);
    }

    // ── RecyclerView setup ────────────────────────────────────
    private void setupRecyclerView() {
        alertAdapter = new AlertAdapter(alertList, this, alert -> {
            // Alert tap pe dialog + navigate/rescue buttons dikhao
            selectedAlert = alert;
            showAlertActionDialog(alert);
        });
        rvLiveAlerts.setLayoutManager(new LinearLayoutManager(this));
        rvLiveAlerts.setAdapter(alertAdapter);
        rvLiveAlerts.setNestedScrollingEnabled(false);
        rvLiveAlerts.setHasFixedSize(false);
    }

    // ── Live alerts ───────────────────────────────────────────
    private void listenToLiveAlerts() {
        db.collection("alerts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshots, e) -> {
                    if (e != null) { Log.e("Firestore", "Error", e); return; }
                    if (snapshots == null) return;

                    int count = snapshots.size();
                    if (tvTotalAlerts != null) tvTotalAlerts.setText(String.valueOf(count));
                    if (tvNotifBadge  != null) tvNotifBadge.setText(String.valueOf(count));

                    // New alert sound
                    if (count > lastAlertCount && lastAlertCount != 0)
                        playAlertSound();
                    lastAlertCount = count;

                    alertList.clear();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        AlertModel alert = doc.toObject(AlertModel.class);
                        if (alert != null) {
                            alert.setAlertId(doc.getId());
                            alertList.add(alert);
                        }
                    }

                    updateEmptyState(count);
                    alertAdapter.notifyDataSetChanged();
                });
    }

    // ── Alert action dialog ───────────────────────────────────
    private void showAlertActionDialog(AlertModel alert) {
        new AlertDialog.Builder(this)
                .setTitle("Alert Action")
                .setMessage("What action would you like to take for this alert?")
                .setPositiveButton("DELETE (SCAM)", (d, w) ->
                        db.collection("alerts").document(alert.getAlertId())
                                .delete()
                                .addOnSuccessListener(v -> toast("Alert deleted successfully"))
                                .addOnFailureListener(ex -> toast("Failed to delete alert")))
                .setNeutralButton("MARK CRITICAL", (d, w) ->
                        db.collection("alerts").document(alert.getAlertId())
                                .update("status", "CRITICAL")
                                .addOnSuccessListener(v -> toast("Status updated to CRITICAL")))
                .setNegativeButton("NAVIGATE", (d, w) -> {
                    // Show navigate + rescue buttons
                    if (btnNavigate    != null) btnNavigate.setVisibility(View.VISIBLE);
                    if (btnMarkRescued != null) btnMarkRescued.setVisibility(View.VISIBLE);
                })
                .show();
    }

    // ── Listeners ─────────────────────────────────────────────
    private void setupListeners() {

        // Profile
        if (btnProfile != null)
            btnProfile.setOnClickListener(v ->
                    startActivity(new Intent(this, ProfileRescuerActivity.class)));

        // Quick action buttons
        safeClick(R.id.btnStartDetection,
                v -> startActivity(new Intent(this, CameraActivity.class)));

        safeClick(R.id.btnMap,
                v -> startActivity(new Intent(this, MapActivity.class)));

        safeClick(R.id.btnSendAlert,
                v -> toast("🆘 Manual alert sent!"));

        safeClick(R.id.btnHistory,
                v -> toast("History coming soon!"));

        safeClick(R.id.btnUploadImage,
                v -> startActivity(new Intent(this, ImageUploadActivity.class)));

        safeClick(R.id.btnUploadVideo,
                v -> toast("Video upload coming soon!"));

        safeClick(R.id.cardAbout,
                v -> startActivity(new Intent(this, MainActivity.class)));

        // ── Generate PDF ──────────────────────────────────────
        safeClick(R.id.btnGeneratePdf, v -> {
            if (alertList.isEmpty()) {
                toast("No alerts available to generate a report");
            } else {
                generatePdfReport();
            }
        });

        // ── Navigate to survivor ──────────────────────────────
        if (btnNavigate != null) {
            btnNavigate.setOnClickListener(v -> {
                if (selectedAlert != null && selectedAlert.getLocation() != null) {
                    double lat = selectedAlert.getLocation().getLatitude();
                    double lng = selectedAlert.getLocation().getLongitude();
                    Uri geoUri = Uri.parse("google.navigation:q=" + lat + "," + lng);
                    Intent mapIntent = new Intent(Intent.ACTION_VIEW, geoUri);
                    mapIntent.setPackage("com.google.android.apps.maps");
                    if (mapIntent.resolveActivity(getPackageManager()) != null) {
                        startActivity(mapIntent);
                    } else {
                        // Fallback to browser maps
                        Uri browserUri = Uri.parse(
                                "https://maps.google.com/?q=" + lat + "," + lng);
                        startActivity(new Intent(Intent.ACTION_VIEW, browserUri));
                    }
                } else {
                    toast("Location is not available");
                }
            });
        }

        // ── Mark as Rescued ───────────────────────────────────
        if (btnMarkRescued != null) {
            btnMarkRescued.setOnClickListener(v -> {
                if (selectedAlert == null) return;
                db.collection("alerts").document(selectedAlert.getAlertId())
                        .update("status", "RESCUED")
                        .addOnSuccessListener(unused -> {
                            toast("✅ Survivor marked as rescued!!");
                            btnNavigate.setVisibility(View.GONE);
                            btnMarkRescued.setVisibility(View.GONE);
                            selectedAlert = null;
                        })
                        .addOnFailureListener(ex ->
                                toast("Update failed: " + ex.getMessage()));
            });
        }

        // ── Bottom Navigation ─────────────────────────────────
        safeClick(R.id.navHome,    v -> toast("Home is active"));
        safeClick(R.id.navMap,     v -> startActivity(new Intent(this, MapActivity.class)));
        safeClick(R.id.navProfile, v -> startActivity(
                new Intent(this, ProfileRescuerActivity.class)));
        safeClick(R.id.navAlerts,  v -> toast("You are already on the Alerts screen!"));
        safeClick(R.id.navAbout,   v -> startActivity(new Intent(this, MainActivity.class)));
    }

    // ══ PDF REPORT ════════════════════════════════════════════
    private void generatePdfReport() {
        String ts       = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        String fileName = "RescueAI_Report_" + ts + ".pdf";
        File   pdfFile  = new File(getExternalFilesDir(null), fileName);

        try {
            PdfWriter   writer   = new PdfWriter(pdfFile.getAbsolutePath());
            PdfDocument pdf      = new PdfDocument(writer);
            Document    document = new Document(pdf);

            DeviceRgb pink = new DeviceRgb(255, 77, 109);
            DeviceRgb gray = new DeviceRgb(100, 116, 139);

            // Header
            document.add(new Paragraph("RESCUEAI — EMERGENCY RESPONSE SYSTEM")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(16).setBold().setFontColor(pink));

            document.add(new Paragraph("SURVIVOR DETECTION REPORT")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(12).setFontColor(gray));

            document.add(new LineSeparator(new SolidLine()));
            document.add(new Paragraph("\n"));

            // Report meta
            String reportTime  = new SimpleDateFormat("dd MMM yyyy, HH:mm:ss",
                    Locale.getDefault()).format(new Date());
            String officerName = tvRescuerName != null
                    ? tvRescuerName.getText().toString() : "Rescue Officer";

            document.add(new Paragraph("REPORT INFORMATION")
                    .setBold().setFontColor(pink));
            document.add(new Paragraph("Generated On : " + reportTime));
            document.add(new Paragraph("Officer      : " + officerName));
            document.add(new Paragraph("Total Alerts : " + alertList.size()));
            document.add(new Paragraph("\n"));

            // Summary stats
            int totalSurvivors = 0, pending = 0,
                    resolved = 0, critical = 0;
            Map<String, Integer> locationCount = new HashMap<>();

            for (AlertModel a : alertList) {
                totalSurvivors += a.getSurvivorCount();
                String st = a.getStatus() != null
                        ? a.getStatus().toLowerCase() : "";
                if (st.equals("pending"))  pending++;
                if (st.equals("resolved")) resolved++;
                if (st.equals("critical") || st.equals("CRITICAL")) critical++;

                if (a.getAddress() != null && !a.getAddress().isEmpty()) {
                    String loc = a.getAddress().length() > 30
                            ? a.getAddress().substring(0, 30)
                            : a.getAddress();
                    locationCount.put(loc,
                            locationCount.getOrDefault(loc, 0) + 1);
                }
            }

            document.add(new Paragraph("SUMMARY STATISTICS")
                    .setBold().setFontColor(pink));
            Table summaryTable = new Table(new float[]{250f, 250f});
            summaryTable.setWidth(UnitValue.createPercentValue(100));
            addRow(summaryTable, "Total Survivors Detected",
                    String.valueOf(totalSurvivors));
            addRow(summaryTable, "Pending Alerts",  String.valueOf(pending));
            addRow(summaryTable, "Resolved Alerts", String.valueOf(resolved));
            addRow(summaryTable, "Critical Alerts", String.valueOf(critical));
            document.add(summaryTable);
            document.add(new Paragraph("\n"));

            // Location wise
            if (!locationCount.isEmpty()) {
                document.add(new Paragraph("LOCATION WISE ALERTS")
                        .setBold().setFontColor(pink));
                Table locTable = new Table(new float[]{350f, 150f});
                locTable.setWidth(UnitValue.createPercentValue(100));
                locTable.addHeaderCell(new Cell()
                        .add(new Paragraph("Location").setBold())
                        .setBackgroundColor(new DeviceRgb(240, 240, 240)));
                locTable.addHeaderCell(new Cell()
                        .add(new Paragraph("Count").setBold())
                        .setBackgroundColor(new DeviceRgb(240, 240, 240)));
                for (Map.Entry<String, Integer> entry : locationCount.entrySet()) {
                    locTable.addCell(new Cell()
                            .add(new Paragraph(entry.getKey()).setFontSize(9)));
                    locTable.addCell(new Cell()
                            .add(new Paragraph(String.valueOf(entry.getValue()))
                                    .setTextAlignment(TextAlignment.CENTER)
                                    .setFontSize(9)));
                }
                document.add(locTable);
                document.add(new Paragraph("\n"));
            }

            // All alerts table
            document.add(new Paragraph("ALL ALERTS DETAIL")
                    .setBold().setFontColor(pink));
            Table alertTable = new Table(
                    new float[]{110f, 80f, 70f, 70f, 120f});
            alertTable.setWidth(UnitValue.createPercentValue(100));

            for (String h : new String[]{
                    "Timestamp", "Reported By", "Survivors", "Status", "Location"}) {
                alertTable.addHeaderCell(new Cell()
                        .add(new Paragraph(h).setBold().setFontSize(8))
                        .setBackgroundColor(pink)
                        .setFontColor(ColorConstants.WHITE));
            }

            boolean alt = false;
            for (AlertModel a : alertList) {
                DeviceRgb row = alt
                        ? new DeviceRgb(250, 250, 250)
                        : new DeviceRgb(255, 255, 255);
                alt = !alt;

                alertTable.addCell(cell(a.getTimestamp(), row));
                alertTable.addCell(cell(
                        a.getReportedBy() != null
                                ? a.getReportedBy().substring(0,
                                Math.min(8, a.getReportedBy().length())) + "..."
                                : "—", row));
                alertTable.addCell(cell(
                        String.valueOf(a.getSurvivorCount()), row));
                alertTable.addCell(cell(
                        a.getStatus() != null
                                ? a.getStatus().toUpperCase() : "—", row));
                alertTable.addCell(cell(
                        a.getAddress() != null && !a.getAddress().isEmpty()
                                ? a.getAddress().substring(0,
                                Math.min(25, a.getAddress().length()))
                                : "N/A", row));
            }
            document.add(alertTable);
            document.add(new Paragraph("\n\n"));

            // Footer
            document.add(new LineSeparator(new SolidLine()));
            Table footer = new Table(2);
            footer.setWidth(UnitValue.createPercentValue(100));
            footer.addCell(new Cell()
                    .add(new Paragraph(
                            "\n\n__________________________\nOfficer Signature"))
                    .setBorder(Border.NO_BORDER)
                    .setTextAlignment(TextAlignment.CENTER));
            footer.addCell(new Cell()
                    .add(new Paragraph("\n\n__________________________\nUnit Seal"))
                    .setBorder(Border.NO_BORDER)
                    .setTextAlignment(TextAlignment.CENTER));
            document.add(footer);

            document.add(new Paragraph(
                    "Generated by RescueAI · " + reportTime)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(8).setFontColor(gray));

            document.close();

            // Open PDF
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".provider", pdfFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
            toast("✅ PDF Report ready!");

        } catch (Exception e) {
            Log.e("PDF", "Error: " + e.getMessage());
            toast("PDF generate fail: " + e.getMessage());
        }
    }

    // ── PDF helpers ───────────────────────────────────────────
    private void addRow(Table table, String label, String value) {
        table.addCell(new Cell()
                .add(new Paragraph(label).setBold().setFontSize(10)));
        table.addCell(new Cell()
                .add(new Paragraph(value).setFontSize(10)
                        .setTextAlignment(TextAlignment.CENTER)));
    }

    private Cell cell(String text, DeviceRgb bg) {
        return new Cell()
                .add(new Paragraph(text != null ? text : "—").setFontSize(8))
                .setBackgroundColor(bg);
    }

    // ── Load profile ──────────────────────────────────────────
    private void loadRescuerProfile() {
        String uid = mAuth.getUid();
        if (uid == null) return;
        db.collection("users").document(uid)
                .addSnapshotListener(MetadataChanges.INCLUDE, (doc, e) -> {
                    if (doc != null && doc.exists() && tvRescuerName != null) {
                        String name = doc.getString("name");
                        tvRescuerName.setText(name != null ? name : "Rescuer");
                    }
                });
    }

    // ── Rescued stats ─────────────────────────────────────────
    private void fetchRescuedStats() {
        db.collection("alerts").whereEqualTo("status", "RESCUED")
                .addSnapshotListener(MetadataChanges.INCLUDE, (snaps, e) -> {
                    if (snaps != null && tvRescued != null)
                        tvRescued.setText(String.valueOf(snaps.size()));
                });
    }

    // ── Alert sound ───────────────────────────────────────────
    private void playAlertSound() {
        try {
            Uri notif = RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(
                    getApplicationContext(), notif);
            if (r != null) r.play();
        } catch (Exception e) {
            Log.e("Sound", "Error", e);
        }
    }

    // ── Empty state ───────────────────────────────────────────
    private void updateEmptyState(int count) {
        if (emptyState == null) return;
        if (count > 0) {
            emptyState.setVisibility(View.GONE);
            rvLiveAlerts.setVisibility(View.VISIBLE);
        } else {
            emptyState.setVisibility(View.VISIBLE);
            rvLiveAlerts.setVisibility(View.GONE);
        }
    }

    // ── Helpers ───────────────────────────────────────────────
    private void safeClick(int id, View.OnClickListener l) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(l);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}