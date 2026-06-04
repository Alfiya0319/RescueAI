package com.survivordetection.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.survivordetection.R;
import com.survivordetection.models.AlertModel;

import java.util.List;
import java.util.Locale;

public class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.ViewHolder> {

    private List<AlertModel> alertList;
    private Context context;
    private OnAlertLongClickListener onAlertLongClickListener;

    public interface OnAlertLongClickListener {
        void onAlertLongClick(AlertModel alert);
    }

    // UPDATED CONSTRUCTOR: Ab ye listener bhi lega taaki Dashboard se connect ho sake
    public AlertAdapter(List<AlertModel> alertList, Context context, OnAlertLongClickListener listener) {
        this.alertList = alertList;
        this.context = context;
        this.onAlertLongClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alert, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AlertModel alert = alertList.get(position);

        // 1. Long Click for Delete/Scam Logic
        holder.itemView.setOnLongClickListener(v -> {
            if (onAlertLongClickListener != null) {
                onAlertLongClickListener.onAlertLongClick(alert);
            }
            return true;
        });

        // 2. Critical Status Logic (Color Change)
        if ("CRITICAL".equalsIgnoreCase(alert.getStatus()) || "SOS EMERGENCY".equalsIgnoreCase(alert.getType())) {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#421010"));
            holder.tvStatus.setTextColor(Color.parseColor("#FF4D6D"));
            holder.tvStatus.setText("⚠️ CRITICAL");
        } else if ("RESCUED".equalsIgnoreCase(alert.getStatus())) {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#0D2B12"));
            holder.tvStatus.setTextColor(Color.GREEN);
            holder.tvStatus.setText("✅ RESCUED");
        } else {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#1E1E1E"));
            holder.tvStatus.setTextColor(Color.WHITE);
            holder.tvStatus.setText(alert.getStatus());
        }

        // 3. Display Data
        if (alert.getAlertId() != null && alert.getAlertId().length() > 5) {
            holder.tvAlertId.setText("ID: ..." + alert.getAlertId().substring(alert.getAlertId().length()-5));
        } else {
            holder.tvAlertId.setText("New Alert");
        }

        holder.tvTime.setText(alert.getTimestamp() != null ? alert.getTimestamp() : "Just Now");
        holder.tvConfidence.setText("Confidence: " + (alert.getConfidence() != null ? alert.getConfidence() : "N/A"));

        // 4. Location Logic
        if (alert.getAddress() != null && !alert.getAddress().isEmpty()) {
            holder.tvLocation.setText(alert.getAddress());
        } else if (alert.getLocation() != null) {
            GeoPoint gp = alert.getLocation();
            holder.tvLocation.setText(String.format(Locale.getDefault(), "%.4f, %.4f", gp.getLatitude(), gp.getLongitude()));
        } else {
            holder.tvLocation.setText("Location N/A");
        }

        // 5. Image Loading
        if (alert.getSnapshotUrl() != null && !alert.getSnapshotUrl().isEmpty()) {
            Glide.with(context)
                    .load(alert.getSnapshotUrl())
                    .placeholder(R.drawable.ic_person_placeholder)
                    .into(holder.ivSnapshot);
        }

        // 6. BUTTONS
        holder.btnNavigate.setOnClickListener(v -> {
            if (alert.getLocation() != null) {
                double lat = alert.getLocation().getLatitude();
                double lng = alert.getLocation().getLongitude();
                Uri gmmIntentUri = Uri.parse("google.navigation:q=" + lat + "," + lng);
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                try {
                    context.startActivity(mapIntent);
                } catch (Exception e) {
                    Toast.makeText(context, "Maps not installed", Toast.LENGTH_SHORT).show();
                }
            }
        });

        holder.btnMarkRescued.setOnClickListener(v -> {
            if (alert.getAlertId() != null) {
                FirebaseFirestore.getInstance().collection("alerts").document(alert.getAlertId())
                        .update("status", "RESCUED")
                        .addOnSuccessListener(aVoid -> {
                            alert.setStatus("RESCUED");
                            notifyItemChanged(position);
                            Toast.makeText(context, "Marked as Rescued", Toast.LENGTH_SHORT).show();
                        });
            }
        });
    }

    @Override
    public int getItemCount() {
        return alertList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAlertId, tvTime, tvLocation, tvConfidence, tvStatus;
        ImageView ivSnapshot;
        Button btnNavigate, btnMarkRescued;
        CardView cardView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAlertId = itemView.findViewById(R.id.tvAlertId);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvConfidence = itemView.findViewById(R.id.tvConfidence);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            ivSnapshot = itemView.findViewById(R.id.ivSnapshot);
            btnNavigate = itemView.findViewById(R.id.btnNavigate);
            btnMarkRescued = itemView.findViewById(R.id.btnMarkRescued);
            cardView = itemView.findViewById(R.id.cardAlertItem);
        }
    }
}