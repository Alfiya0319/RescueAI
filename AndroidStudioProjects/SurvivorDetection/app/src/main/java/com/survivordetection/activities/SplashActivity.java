package com.survivordetection.activities;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.Source;
import com.survivordetection.R;

public class SplashActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // --- STEP 1: Initialize Firebase & Offline Persistence ---
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        try {
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build();
            db.setFirestoreSettings(settings);
        } catch (Exception e) {
            // Settings already set or error in initialization
        }

        initAnimations();

        // 3 Seconds Delay for Branding, then check session
        new Handler().postDelayed(this::checkUserSession, 3000);
    }

    private void initAnimations() {
        ImageView ivLogo = findViewById(R.id.ivLogo);
        View r1 = findViewById(R.id.ring1);
        View r2 = findViewById(R.id.ring2);
        View r3 = findViewById(R.id.ring3);

        if (ivLogo != null) {
            ivLogo.setAlpha(0f);
            ivLogo.animate().alpha(1f).setDuration(1000).start();
        }

        animateRing(r1, 1800, 1.2f, true);
        animateRing(r2, 2200, 1.3f, false);
        animateRing(r3, 2600, 1.4f, true);
    }

    private void animateRing(View ring, int duration, float scaleTarget, boolean clockwise) {
        if (ring == null) return;
        ObjectAnimator rotate = ObjectAnimator.ofFloat(ring, "rotation", 0f, clockwise ? 360f : -360f);
        rotate.setDuration(duration * 2);
        rotate.setRepeatCount(ValueAnimator.INFINITE);
        rotate.setInterpolator(new LinearInterpolator());
        rotate.start();

        PropertyValuesHolder sx = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, scaleTarget);
        PropertyValuesHolder sy = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, scaleTarget);
        PropertyValuesHolder al = PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f);

        ObjectAnimator pulse = ObjectAnimator.ofPropertyValuesHolder(ring, sx, sy, al);
        pulse.setDuration(duration);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        pulse.start();
    }

    private void checkUserSession() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            // Case: Kisi ne login nahi kiya hai
            startTargetActivity(RoleSelectionActivity.class);
            return;
        }

        // Case: User Login hai.
        // Pehle Firestore check karega (Rescuer ke liye)
        db.collection("users").document(currentUser.getUid())
                .get(Source.DEFAULT) // Check local cache first for offline speed
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        // Agar Firestore mein document mil gaya -> Ye Rescuer Dashboard hai
                        startTargetActivity(RescuerDashboardActivity.class);
                    } else {
                        // Agar Firestore mein nahi mila -> Iska matlab ye User Database wala banda hai
                        // Seedha User Dashboard par bhej do
                        startTargetActivity(UserDashboardActivity.class);
                    }
                });
    }

    private void startTargetActivity(Class<?> targetClass) {
        Intent intent = new Intent(SplashActivity.this, targetClass);
        startActivity(intent);
        finish(); // Splash screen ko stack se hata do
    }
}