package com.survivordetection.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.survivordetection.R;
import java.util.HashMap;
import java.util.Map;

public class ProfileRescuerActivity extends AppCompatActivity {

    private TextView    tvProfileName, tvProfileEmail;
    private EditText    etEditName, etEditMobile, etEditBloodGroup,
            etEditTeamId, etEditOrganisation, etEditDesignation,
            etOldPassword, etNewPassword;
    private ProgressBar progressBar;
    private FirebaseAuth      mAuth;
    private FirebaseFirestore db;
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_rescuer);

        mAuth    = FirebaseAuth.getInstance();
        db       = FirebaseFirestore.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) { finish(); return; }
        uid = user.getUid();

        // Views
        tvProfileName      = findViewById(R.id.tvProfileName);
        tvProfileEmail     = findViewById(R.id.tvProfileEmail);
        etEditName         = findViewById(R.id.etEditName);
        etEditMobile       = findViewById(R.id.etEditMobile);
        etEditBloodGroup   = findViewById(R.id.etEditBloodGroup);
        etEditTeamId       = findViewById(R.id.etEditTeamId);
        etEditOrganisation = findViewById(R.id.etEditOrganisation);
        etEditDesignation  = findViewById(R.id.etEditDesignation);
        etOldPassword      = findViewById(R.id.etOldPassword);
        etNewPassword      = findViewById(R.id.etNewPassword);
        progressBar        = findViewById(R.id.progressBar);

        // Back
        ((TextView) findViewById(R.id.btnBack)).setOnClickListener(v -> finish());

        // Load
        loadProfile();

        // Save
        findViewById(R.id.btnSave).setOnClickListener(v -> saveProfile());

        // Logout
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(this, RoleSelectionActivity.class));
            finishAffinity();
        });
    }

    private void loadProfile() {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String name   = doc.getString("name");
                    String email  = doc.getString("email");
                    String mobile = doc.getString("mobile");
                    String blood  = doc.getString("bloodGroup");
                    String teamId = doc.getString("teamId");
                    String org    = doc.getString("organisation");
                    String desig  = doc.getString("designation");

                    if (name != null)  { tvProfileName.setText(name); etEditName.setText(name); }
                    if (email != null)  tvProfileEmail.setText(email);
                    if (mobile != null) etEditMobile.setText(mobile);
                    if (blood != null)  etEditBloodGroup.setText(blood);
                    if (teamId != null) etEditTeamId.setText(teamId);
                    if (org != null)    etEditOrganisation.setText(org);
                    if (desig != null)  etEditDesignation.setText(desig);
                });
    }

    private void saveProfile() {
        String name   = etEditName.getText().toString().trim();
        String mobile = etEditMobile.getText().toString().trim();
        String blood  = etEditBloodGroup.getText().toString().trim();
        String teamId = etEditTeamId.getText().toString().trim();
        String org    = etEditOrganisation.getText().toString().trim();
        String desig  = etEditDesignation.getText().toString().trim();
        String oldPwd = etOldPassword.getText().toString().trim();
        String newPwd = etNewPassword.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        // ✅ Firestore update
        Map<String, Object> updates = new HashMap<>();
        updates.put("name",         name);
        updates.put("mobile",       mobile);
        updates.put("bloodGroup",   blood);
        updates.put("teamId",       teamId);
        updates.put("organisation", org);
        updates.put("designation",  desig);

        db.collection("users").document(uid).update(updates)
                .addOnSuccessListener(unused -> {
                    tvProfileName.setText(name);

                    if (!oldPwd.isEmpty() && !newPwd.isEmpty()) {
                        changePassword(oldPwd, newPwd);
                    } else {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "✅ Profile updated!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Update failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void changePassword(String oldPwd, String newPwd) {
        if (newPwd.length() < 6) {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "New password must be 6+ characters",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), oldPwd);
        user.reauthenticate(credential)
                .addOnSuccessListener(unused ->
                        user.updatePassword(newPwd)
                                .addOnSuccessListener(v -> {
                                    progressBar.setVisibility(View.GONE);
                                    etOldPassword.setText("");
                                    etNewPassword.setText("");
                                    Toast.makeText(this,
                                            "✅ Profile & password updated!",
                                            Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(this,
                                            "Password update failed: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                }))
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Old password incorrect",
                            Toast.LENGTH_SHORT).show();
                });
    }
}