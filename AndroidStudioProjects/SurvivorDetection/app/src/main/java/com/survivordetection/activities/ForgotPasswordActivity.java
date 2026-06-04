package com.survivordetection.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.survivordetection.R;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText etEmail;
    private ProgressBar progressBar;
    private LinearLayout contentBlock, successBlock;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        mAuth        = FirebaseAuth.getInstance();
        etEmail      = findViewById(R.id.etEmail);
        progressBar  = findViewById(R.id.progressBar);
        contentBlock = findViewById(R.id.contentBlock);
        successBlock = findViewById(R.id.successBlock);

        // Back button
        ((TextView) findViewById(R.id.btnBack)).setOnClickListener(v -> finish());

        // Back to login link
        ((TextView) findViewById(R.id.tvBackToLogin)).setOnClickListener(v -> finish());

        // Send reset button
        ((Button) findViewById(R.id.btnSendReset)).setOnClickListener(v -> sendResetEmail());

        // Success state back to login
        ((Button) findViewById(R.id.btnBackToLogin)).setOnClickListener(v -> finish());
    }

    private void sendResetEmail() {
        String email = etEmail.getText().toString().trim();

        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        mAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> {
                    progressBar.setVisibility(View.GONE);
                    // ✅ Show success block
                    contentBlock.setVisibility(View.GONE);
                    successBlock.setVisibility(View.VISIBLE);
                    ((TextView) findViewById(R.id.tvSuccessMsg)).setText(
                            "A password reset link has been\nsent to " + email);
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this,
                            "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}