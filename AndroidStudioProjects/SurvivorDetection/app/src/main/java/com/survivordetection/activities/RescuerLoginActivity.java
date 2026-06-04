package com.survivordetection.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.survivordetection.R;

public class RescuerLoginActivity extends AppCompatActivity {

    private EditText etTeamId, etEmail, etPassword;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rescuer_login);

        // Firebase Initialization
        mAuth       = FirebaseAuth.getInstance();
        db          = FirebaseFirestore.getInstance();

        // Views Initialization
        etTeamId    = findViewById(R.id.etTeamId);
        etEmail     = findViewById(R.id.etEmail);
        etPassword  = findViewById(R.id.etPassword);
        progressBar = findViewById(R.id.progressBar);

        // Login Button Click
        findViewById(R.id.btnLogin).setOnClickListener(v -> loginRescuer());

        // Register Link Click
        TextView tvRegister = findViewById(R.id.tvRegister);
        tvRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RescuerRegisterActivity.class)));
    }

    private void loginRescuer() {
        String teamId   = etTeamId.getText().toString().trim();
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // 1. Basic Validation
        if (teamId.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        // 2. Firebase Authentication
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    String uid = result.getUser().getUid();

                    // 3. Firestore Verification (Role and Team ID check)
                    db.collection("users").document(uid).get()
                            .addOnSuccessListener(doc -> {
                                progressBar.setVisibility(View.GONE);
                                if (doc.exists()) {
                                    String savedTeamId = doc.getString("teamId");
                                    String role        = doc.getString("role");

                                    // Role and Team ID Match Check
                                    if ("rescuer".equalsIgnoreCase(role) && teamId.equals(savedTeamId)) {

                                        // Login Success: Redirect to Dashboard and Clear Activity Stack
                                        Intent intent = new Intent(RescuerLoginActivity.this, RescuerDashboardActivity.class);
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        startActivity(intent);
                                        finish();

                                    } else {
                                        // Role mismatch or Wrong Team ID
                                        mAuth.signOut();
                                        Toast.makeText(this, "Unauthorized: Invalid Team ID or Role", Toast.LENGTH_LONG).show();
                                    }
                                } else {
                                    mAuth.signOut();
                                    Toast.makeText(this, "User profile not found", Toast.LENGTH_LONG).show();
                                }
                            })
                            .addOnFailureListener(e -> {
                                progressBar.setVisibility(View.GONE);
                                mAuth.signOut();
                                Toast.makeText(this, "Database Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Authentication Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}