package com.survivordetection.activities;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.survivordetection.R;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class MapActivity extends AppCompatActivity {

    private MapView              mapView;
    private TextView             mapStatusText, tvAlertCount;
    private TextView             tvIncidentTitle, btnCloseCard,
            incidentDesc, tvIncidentTime,
            tvIncidentDistance, btnNavigate;
    private CardView             incidentDetailCard;
    private FloatingActionButton fabMyLocation;

    private FirebaseFirestore    db;
    private ListenerRegistration alertListener;

    private FusedLocationProviderClient fusedLocation;
    private Location             myLocation;
    private MyLocationNewOverlay myLocationOverlay;

    private double selectedLat, selectedLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_map);

        db            = FirebaseFirestore.getInstance();
        fusedLocation = LocationServices.getFusedLocationProviderClient(this);

        mapView            = findViewById(R.id.map);
        mapStatusText      = findViewById(R.id.mapStatusText);
        tvAlertCount       = findViewById(R.id.tvAlertCount);
        incidentDetailCard = findViewById(R.id.incidentDetailCard);
        tvIncidentTitle    = findViewById(R.id.tvIncidentTitle);
        btnCloseCard       = findViewById(R.id.btnCloseCard);
        incidentDesc       = findViewById(R.id.incidentDesc);
        tvIncidentTime     = findViewById(R.id.tvIncidentTime);
        tvIncidentDistance = findViewById(R.id.tvIncidentDistance);
        btnNavigate        = findViewById(R.id.btnNavigate);
        fabMyLocation      = findViewById(R.id.fab_my_location);

        setupMap();

        // FIXED: Using generic View for back button to prevent ClassCastException
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        btnCloseCard.setOnClickListener(v -> incidentDetailCard.setVisibility(View.GONE));
        btnNavigate.setOnClickListener(v -> navigateToSurvivor());
        fabMyLocation.setOnClickListener(v -> goToMyLocation());

        getMyLocation();
        loadLiveAlerts();
    }

    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(14.0);
        mapView.getController().setCenter(new GeoPoint(18.5204, 73.8567));

        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        myLocationOverlay.enableMyLocation();
        mapView.getOverlays().add(myLocationOverlay);
    }

    private void loadLiveAlerts() {
        alertListener = db.collection("alerts")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    mapView.getOverlays().clear();
                    mapView.getOverlays().add(myLocationOverlay);

                    int count = 0;
                    for (QueryDocumentSnapshot doc : snapshots) {
                        com.google.firebase.firestore.GeoPoint fp = doc.getGeoPoint("location");
                        if (fp == null) continue;

                        GeoPoint osmPoint = new GeoPoint(fp.getLatitude(), fp.getLongitude());
                        String status     = doc.getString("status");
                        String type       = doc.getString("type");

                        // Handle both String and Timestamp for timestamp field
                        String timestampStr = "";
                        Object tsObj = doc.get("timestamp");
                        if (tsObj instanceof com.google.firebase.Timestamp) {
                            timestampStr = new SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
                                    .format(((com.google.firebase.Timestamp) tsObj).toDate());
                        } else {
                            timestampStr = doc.getString("timestamp");
                        }
                        final String finalTimestamp = timestampStr;

                        Marker marker = new Marker(mapView);
                        marker.setPosition(osmPoint);
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                        marker.setTitle(type != null ? type : "Survivor Alert");

                        // ─── DYNAMIC COLOR LOGIC (Bina Drawable Files ke) ───
                        // Hum default marker icon ko fetch karke uska color filter change karenge
                        Drawable markerIcon = marker.getIcon().mutate();

                        int color;
                        if ("rescued".equalsIgnoreCase(status)) {
                            color = 0xFF4CAF50; // Green
                            marker.setSubDescription("✅ SAFE");
                        } else if ("urgent".equalsIgnoreCase(status)) {
                            color = 0xFFFF0000; // Red
                            marker.setSubDescription("🆘 URGENT");
                        } else {
                            color = 0xFFFF9800; // Orange
                            marker.setSubDescription("🔴 PENDING");
                        }

                        // Icon ka color change kar rahe hain
                        markerIcon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                        marker.setIcon(markerIcon);

                        marker.setOnMarkerClickListener((m, map) -> {
                            selectedLat = fp.getLatitude();
                            selectedLng = fp.getLongitude();
                            showIncidentCard(type, doc.getString("address"), finalTimestamp, 1);
                            return true;
                        });

                        mapView.getOverlays().add(marker);
                        count++;
                    }

                    final int finalCount = count;
                    runOnUiThread(() -> {
                        if (tvAlertCount != null) tvAlertCount.setText(String.valueOf(finalCount));
                        mapView.invalidate();
                    });
                });
    }

    private void showIncidentCard(String type, String address, String time, int count) {
        tvIncidentTitle.setText(type);
        incidentDesc.setText(count + " survivor(s) detected.");
        tvIncidentTime.setText(time);
        incidentDetailCard.setVisibility(View.VISIBLE);
    }

    private void navigateToSurvivor() {
        if (selectedLat == 0 && selectedLng == 0) return;
        String uri = String.format(Locale.ENGLISH, "google.navigation:q=%f,%f", selectedLat, selectedLng);
        Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri));
        intent.setPackage("com.google.android.apps.maps");
        try { startActivity(intent); } catch (Exception e) { }
    }

    private void goToMyLocation() {
        if (myLocationOverlay.getMyLocation() != null) {
            mapView.getController().animateTo(myLocationOverlay.getMyLocation());
            mapView.getController().setZoom(17.0);
        }
    }

    private void getMyLocation() {
        try {
            fusedLocation.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) myLocation = location;
            });
        } catch (SecurityException ignored) {}
    }

    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause()  { super.onPause();  mapView.onPause();  }
    @Override
    protected void onDestroy() {
        if (alertListener != null) alertListener.remove();
        mapView.onDetach();
        super.onDestroy();
    }
}