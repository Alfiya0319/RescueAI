package com.survivordetection.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.survivordetection.R;

import java.util.HashMap;
import java.util.Map;

public class ProfileUserActivity extends AppCompatActivity {

    // ── UI Elements (Matching your XML IDs) ──────────────────
    private TextView  tvProfileName, tvProfileEmail, btnBack;
    private EditText  etEditName, etEditMobile, etEditBloodGroup;
    private EditText  etOldPassword, etNewPassword; // Password fields from XML
    private ImageView ivAvatar;
    private ProgressBar progressBar;
    private Button    btnSave, btnLogout;

    // ── Firebase ──────────────────────────────────────────────
    private FirebaseAuth      mAuth;
    private DatabaseReference mDatabase;
    private String            uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_user);

        // Firebase Setup
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance(
                "https://ai-surviour-default-rtdb.asia-southeast1.firebasedatabase.app"
        ).getReference();

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            goToRoleSelection();
            return;
        }
        uid = user.getUid();

        initViews();
        loadProfileData();
        setListeners();
    }

    private void initViews() {
        // Ye saari IDs aapke XML code se match karti hain
        btnBack          = findViewById(R.id.btnBack);
        tvProfileName    = findViewById(R.id.tvProfileName);
        tvProfileEmail   = findViewById(R.id.tvProfileEmail);
        etEditName       = findViewById(R.id.etEditName);
        etEditMobile     = findViewById(R.id.etEditMobile);
        etEditBloodGroup = findViewById(R.id.etEditBloodGroup);
        etOldPassword    = findViewById(R.id.etOldPassword);
        etNewPassword    = findViewById(R.id.etNewPassword);
        ivAvatar         = findViewById(R.id.ivAvatar);
        progressBar      = findViewById(R.id.progressBar);
        btnSave          = findViewById(R.id.btnSave);
        btnLogout        = findViewById(R.id.btnLogout);
    }

    private void setListeners() {
        // Back Button (XML mein TextView hai)
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Save Button
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> updateProfile());
        }

        // Logout Button
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                mAuth.signOut();
                toast("Logged out!");
                goToRoleSelection();
            });
        }
    }

    private void loadProfileData() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        mDatabase.child("users").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);

                        if (snapshot.exists()) {
                            String name   = snapshot.child("name").getValue(String.class);
                            String email  = snapshot.child("email").getValue(String.class);
                            String mobile = snapshot.child("mobile").getValue(String.class);
                            String blood  = snapshot.child("bloodGroup").getValue(String.class);

                            // UI Update
                            if (name != null) {
                                tvProfileName.setText(name);
                                etEditName.setText(name);
                            }
                            if (email != null) tvProfileEmail.setText(email);
                            if (mobile != null) etEditMobile.setText(mobile);
                            if (blood != null) etEditBloodGroup.setText(blood);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        toast("Error: " + error.getMessage());
                    }
                });
    }

    private void updateProfile() {
        String name   = etEditName.getText().toString().trim();
        String mobile = etEditMobile.getText().toString().trim();
        String blood  = etEditBloodGroup.getText().toString().trim();

        if (name.isEmpty()) {
            etEditName.setError("Name is required");
            return;
        }

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("mobile", mobile);
        updates.put("bloodGroup", blood);

        mDatabase.child("users").child(uid).updateChildren(updates)
                .addOnCompleteListener(task -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    btnSave.setEnabled(true);

                    if (task.isSuccessful()) {
                        tvProfileName.setText(name);
                        toast("✅ Profile Updated!");
                    } else {
                        toast("Failed to update profile");
                    }
                });
    }

    private void goToRoleSelection() {
        Intent intent = new Intent(this, RoleSelectionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}