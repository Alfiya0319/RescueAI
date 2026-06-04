package com.survivordetection.activities;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.survivordetection.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final View btnBack = findViewById(R.id.btnBack);
        final Button btnGetStarted = findViewById(R.id.btnBackToLogin);

        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        if (btnGetStarted != null) {
            btnGetStarted.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MainActivity.this, RoleSelectionActivity.class);
                    startActivity(intent);
                }
            });
        }

        // Animations call (Optional)
        setupAnimations();
    }

    private void setupAnimations() {
        View r1 = findViewById(android.R.id.content).findViewWithTag("ring1");
        if (r1 != null) {
            ObjectAnimator rot = ObjectAnimator.ofFloat(r1, "rotation", 0f, 360f);
            rot.setDuration(10000);
            rot.setRepeatCount(ValueAnimator.INFINITE);
            rot.setInterpolator(new LinearInterpolator());
            rot.start();
        }
    }
}