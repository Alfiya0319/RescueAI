package com.survivordetection.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.survivordetection.R;
import java.util.HashMap;
import java.util.Map;

public class RescuerRegisterActivity extends AppCompatActivity {
    // Class variables mein add karein
    private com.google.android.gms.location.FusedLocationProviderClient fusedLocationClient;
    private EditText etFullName, etEmail, etMobile, etPassword,
            etTeamId, etOrganisation, etIdNumber;
    private Spinner  spinnerRole, spinnerIdType;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rescuer_register);

// onCreate() ke andar initialize karein
        fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this);
        mAuth          = FirebaseAuth.getInstance();
        db             = FirebaseFirestore.getInstance();
        etFullName     = findViewById(R.id.etFullName);
        etEmail        = findViewById(R.id.etEmail);
        etMobile       = findViewById(R.id.etMobile);
        etPassword     = findViewById(R.id.etPassword);
        etTeamId       = findViewById(R.id.etTeamId);
        etOrganisation = findViewById(R.id.etOrganisation);
        etIdNumber     = findViewById(R.id.etIdNumber);
        spinnerRole    = findViewById(R.id.spinnerRole);
        spinnerIdType  = findViewById(R.id.spinnerIdType);
        progressBar    = findViewById(R.id.progressBar);

        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Team Leader", "Field Medic", "Search & Rescue", "Coordinator"});
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRole.setAdapter(roleAdapter);

        ArrayAdapter<String> idAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Aadhaar Card", "PAN Card", "Passport", "Voter ID", "Employee ID"});
        idAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerIdType.setAdapter(idAdapter);

        findViewById(R.id.btnRegister).setOnClickListener(v -> registerRescuer());

        ((TextView) findViewById(R.id.tvLogin)).setOnClickListener(v -> {
            startActivity(new Intent(this, RescuerLoginActivity.class));
            finish();
        });
    }

    private void registerRescuer() {
        String name         = etFullName.getText().toString().trim();
        String email        = etEmail.getText().toString().trim();
        String mobile       = etMobile.getText().toString().trim();
        String password     = etPassword.getText().toString().trim();
        String teamId       = etTeamId.getText().toString().trim();
        String organisation = etOrganisation.getText().toString().trim();
        String idNumber     = etIdNumber.getText().toString().trim();
        String designation  = spinnerRole.getSelectedItem().toString();
        String idType       = spinnerIdType.getSelectedItem().toString();

        // 1. Basic Validation
        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || teamId.isEmpty() || organisation.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        findViewById(R.id.btnRegister).setEnabled(false);

        // 2. Pehle Location Fetch Karein
        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // Permission maangein agar nahi hai
            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            progressBar.setVisibility(View.GONE);
            findViewById(R.id.btnRegister).setEnabled(true);
            return;
        }

        com.google.android.gms.location.FusedLocationProviderClient fusedLocationClient =
                com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this);

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            // Agar location mil gayi toh theek, varna 0.0 set karein
            double lat = (location != null) ? location.getLatitude() : 0.0;
            double lon = (location != null) ? location.getLongitude() : 0.0;

            // 3. Auth User Create Karein
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(result -> {
                        String uid = result.getUser().getUid();

                        // 4. Data bundle mein location add karein
                        Map<String, Object> rescuer = new HashMap<>();
                        rescuer.put("name",         name);
                        rescuer.put("email",        email);
                        rescuer.put("mobile",       mobile);
                        rescuer.put("teamId",       teamId);
                        rescuer.put("organisation", organisation);
                        rescuer.put("designation",  designation);
                        rescuer.put("idType",       idType);
                        rescuer.put("idNumber",     idNumber);
                        rescuer.put("role",         "rescuer");
                        rescuer.put("verified",     false);
                        rescuer.put("createdAt",    com.google.firebase.Timestamp.now());

                        // ✅ COORDINATES ADD KIYE
                        rescuer.put("latitude",     lat);
                        rescuer.put("longitude",    lon);

                        // 5. Firestore mein final data save karein
                        db.collection("users").document(uid).set(rescuer)
                                .addOnSuccessListener(aVoid -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(this, "Registration Successful!", Toast.LENGTH_SHORT).show();
                                    Intent intent = new Intent(this, RescuerDashboardActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    progressBar.setVisibility(View.GONE);
                                    findViewById(R.id.btnRegister).setEnabled(true);
                                    Toast.makeText(this, "Firestore Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    })
                    .addOnFailureListener(e -> {
                        progressBar.setVisibility(View.GONE);
                        findViewById(R.id.btnRegister).setEnabled(true);
                        Toast.makeText(this, "Auth Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        });
    }}