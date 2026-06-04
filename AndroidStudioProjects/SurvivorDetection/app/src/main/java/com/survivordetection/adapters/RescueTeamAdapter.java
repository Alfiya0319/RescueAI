package com.survivordetection.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.survivordetection.R;
import com.survivordetection.models.RescueTeam;

import java.util.List;
import java.util.Locale;

public class RescueTeamAdapter extends RecyclerView.Adapter<RescueTeamAdapter.ViewHolder> {

    private List<RescueTeam> teamList;
    private Context context;

    public RescueTeamAdapter(Context context, List<RescueTeam> teamList) {
        this.context = context;
        this.teamList = teamList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rescue_team, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RescueTeam team = teamList.get(position);

        // Name aur Organization/Designation set karein
        holder.tvName.setText(team.getName());
        holder.tvStatus.setText(team.getOrganisation() + " | " + team.getDesignation());

        // --- 1. DISTANCE LOGIC (Nearest Highlight) ---
        if (team.getDistance() >= 0 && team.getDistance() < 9000) {
            String distanceText = String.format(Locale.getDefault(), "%.1f km away", team.getDistance());

            if (team.getDistance() < 5.0) {
                // Agar 5km se kam hai toh Green aur "Nearest" tag
                holder.tvDistance.setText("📍 Nearest: " + distanceText);
                holder.tvDistance.setTextColor(Color.parseColor("#4CAF50")); // Green
            } else {
                // Normal distance Yellow (aapki theme ke hisaab se)
                holder.tvDistance.setText(distanceText);
                holder.tvDistance.setTextColor(Color.parseColor("#FFEB3B")); // Yellow
            }
        } else {
            holder.tvDistance.setText("Calculating distance...");
            holder.tvDistance.setTextColor(Color.GRAY);
        }

        // --- 2. ADDRESS LOGIC (Naya tvAddress use karein) ---
        // Pehle check karein agar Firestore mein address string pehle se hai
        if (team.getAddress() != null && !team.getAddress().isEmpty()) {
            holder.tvAddress.setText(team.getAddress());
        } else {
            // Varna coordinates se convert karein
            fetchAddressFromCoords(holder.tvAddress, team.getLatitude(), team.getLongitude());
        }

        // --- 3. CALL BUTTON ---
        holder.btnCall.setOnClickListener(v -> {
            String mobileNumber = team.getMobile();
            if (mobileNumber != null && !mobileNumber.isEmpty()) {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + mobileNumber));
                context.startActivity(intent);
            } else {
                Toast.makeText(context, "Contact number not found", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Coordinates to Address Name Helper
    private void fetchAddressFromCoords(TextView textView, double lat, double lon) {
        if (lat == 0 || lon == 0) {
            textView.setText("Location not set");
            return;
        }

        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(context, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);

                if (addresses != null && !addresses.isEmpty()) {
                    String city = addresses.get(0).getLocality(); // City/Town
                    String area = addresses.get(0).getSubLocality(); // Area/Street

                    String finalAddress = (area != null ? area : "") +
                            (city != null ? (area != null ? ", " : "") + city : "");

                    new Handler(Looper.getMainLooper()).post(() -> {
                        textView.setText(finalAddress.trim().isEmpty() ? "Unknown Area" : finalAddress);
                    });
                }
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> textView.setText("Updating Location..."));
            }
        }).start();
    }

    @Override
    public int getItemCount() {
        return teamList != null ? teamList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvStatus, tvAddress, tvDistance;
        Button btnCall;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvTeamName);
            tvStatus = itemView.findViewById(R.id.tvTeamStatus);
            tvDistance = itemView.findViewById(R.id.tvDistance);
            tvAddress = itemView.findViewById(R.id.tvAddress); // XML mein ye ID add karna
            btnCall = itemView.findViewById(R.id.btnCallTeam);
        }
    }
}