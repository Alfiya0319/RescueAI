package com.survivordetection.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.GeoPoint;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class AlertModel {
    private String alertId;
    private GeoPoint location;
    private String type;
    private String status;
    private Object timestamp; // Changed to Object to handle both String and Timestamp from Firestore
    private String address;   // Added to show location text
    private int survivorCount; // Match with CameraActivity "survivorCount"
    private String confidence;
    private String snapshotUrl;
    private String rescuerName;

    // Required empty constructor for Firestore
    public AlertModel() {}

    // Getters
    public String getAlertId() { return alertId; }
    public GeoPoint getLocation() { return location; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    
    public String getTimestamp() {
        if (timestamp instanceof Timestamp) {
            return new SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
                    .format(((Timestamp) timestamp).toDate());
        }
        return timestamp != null ? timestamp.toString() : null;
    }
    
    public String getAddress() { return address; }
    public int getSurvivorCount() { return survivorCount; }
    public String getConfidence() { return confidence; }
    public String getSnapshotUrl() { return snapshotUrl; }
    public String getRescuerName() { return rescuerName; }


    public String getReportedBy(){
        return rescuerName;
    }
    // Setters
    public void setAlertId(String alertId) { this.alertId = alertId; }
    public void setLocation(GeoPoint location) { this.location = location; }
    public void setType(String type) { this.type = type; }
    public void setStatus(String status) { this.status = status; }
    
    public void setTimestamp(Object timestamp) { this.timestamp = timestamp; }

    public void setAddress(String address) { this.address = address; }
    public void setSurvivorCount(int survivorCount) { this.survivorCount = survivorCount; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
    public void setSnapshotUrl(String snapshotUrl) { this.snapshotUrl = snapshotUrl; }
    public void setRescuerName(String rescuerName) { this.rescuerName = rescuerName; }
}