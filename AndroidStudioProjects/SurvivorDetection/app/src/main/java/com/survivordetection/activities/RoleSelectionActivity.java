package com.survivordetection.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.survivordetection.R;

public class RoleSelectionActivity extends AppCompatActivity {

    private String selectedRole = null;
    private LinearLayout cardFieldOperator, cardRescueTeam;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        cardFieldOperator = findViewById(R.id.cardFieldOperator);
        cardRescueTeam    = findViewById(R.id.cardRescueTeam);
        Button btnContinue = findViewById(R.id.btnContinue);

        cardFieldOperator.setOnClickListener(v -> selectRole("user"));
        cardRescueTeam.setOnClickListener(v -> selectRole("rescuer"));

        btnContinue.setOnClickListener(v -> {
            if (selectedRole == null) {
                Toast.makeText(this, "Please select your role first",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, "rescuer".equals(selectedRole)
                    ? RescuerLoginActivity.class
                    : UserLoginActivity.class));
        });
    }

    private void selectRole(String role) {
        selectedRole = role;

        // ✅ Simple background change — no elevation, no CardView
        cardFieldOperator.setBackgroundResource(
                "user".equals(role) ? R.drawable.role_card_selected : R.drawable.dark_card_bg);
        cardRescueTeam.setBackgroundResource(
                "rescuer".equals(role) ? R.drawable.role_card_selected : R.drawable.dark_card_bg);
    }
}