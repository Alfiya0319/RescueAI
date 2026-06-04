package com.survivordetection.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.survivordetection.R;
import com.survivordetection.models.HistoryModel;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView rvHistory;
    private FirebaseRecyclerAdapter<HistoryModel, HistoryViewHolder> adapter;
    private ImageView btnBack;
    private ProgressBar progressHistory;
    private LinearLayout layoutEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // Views Initialization
        btnBack = findViewById(R.id.btnBack);
        rvHistory = findViewById(R.id.rvHistory);
        progressHistory = findViewById(R.id.progressHistory);
        layoutEmpty = findViewById(R.id.layoutEmpty);

        btnBack.setOnClickListener(v -> finish());

        // Layout Manager setup
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setReverseLayout(true); // Naya data upar dikhane ke liye
        layoutManager.setStackFromEnd(true);
        rvHistory.setLayoutManager(layoutManager);

        // Firebase Query
        Query query = FirebaseDatabase.getInstance().getReference("RescueReports").limitToLast(50);

        // Check if data exists for Empty State
        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressHistory.setVisibility(View.GONE);
                if (!snapshot.exists()) {
                    layoutEmpty.setVisibility(View.VISIBLE);
                } else {
                    layoutEmpty.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        FirebaseRecyclerOptions<HistoryModel> options = new FirebaseRecyclerOptions.Builder<HistoryModel>()
                .setQuery(query, HistoryModel.class)
                .build();

        adapter = new FirebaseRecyclerAdapter<HistoryModel, HistoryViewHolder>(options) {
            @Override
            protected void onBindViewHolder(@NonNull HistoryViewHolder holder, int position, @NonNull HistoryModel model) {
                holder.txtCount.setText(model.getSurvivor_count() + " Survivors Found");
                holder.txtTime.setText(model.getTimestamp());
                holder.txtStatus.setText(model.getStatus());

                // Glide for image loading
                Glide.with(holder.imgThumb.getContext())
                        .load(model.getImage_url())
                        .placeholder(R.drawable.icon_circle_pink)
                        .error(android.R.drawable.ic_menu_report_image)
                        .into(holder.imgThumb);

                holder.btnView.setOnClickListener(v -> {
                    Toast.makeText(HistoryActivity.this, "Viewing Report from: " + model.getTimestamp(), Toast.LENGTH_SHORT).show();
                });
            }

            @NonNull
            @Override
            public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
                return new HistoryViewHolder(view);
            }
        };

        rvHistory.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (adapter != null) adapter.startListening();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (adapter != null) adapter.stopListening();
    }

    public static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView txtCount, txtTime, txtStatus;
        ImageView imgThumb, btnView;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            txtCount = itemView.findViewById(R.id.txtHistoryCount);
            txtTime = itemView.findViewById(R.id.txtHistoryTime);
            txtStatus = itemView.findViewById(R.id.txtHistoryStatus);
            imgThumb = itemView.findViewById(R.id.imgHistoryThumb);
            btnView = itemView.findViewById(R.id.btnViewReport);
        }
    }
}