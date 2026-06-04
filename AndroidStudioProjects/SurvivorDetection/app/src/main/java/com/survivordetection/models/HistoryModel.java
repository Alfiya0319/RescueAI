package com.survivordetection.models;

import com.google.firebase.database.PropertyName;

public class HistoryModel {
    // Variables (Make sure these match your Firebase keys)
    private int survivor_count;
    private String image_url;
    private String timestamp;
    private String status;

    // 1. Empty Constructor (Must for Firebase)
    public HistoryModel() {
    }

    // 2. Full Constructor (Optional, for manual use)
    public HistoryModel(int survivor_count, String image_url, String timestamp, String status) {
        this.survivor_count = survivor_count;
        this.image_url = image_url;
        this.timestamp = timestamp;
        this.status = status;
    }

    // 3. Getters & Setters (Firebase needs Setters to fill data)

    @PropertyName("survivor_count")
    public int getSurvivor_count() { return survivor_count; }

    @PropertyName("survivor_count")
    public void setSurvivor_count(int survivor_count) { this.survivor_count = survivor_count; }

    @PropertyName("image_url")
    public String getImage_url() { return image_url; }

    @PropertyName("image_url")
    public void setImage_url(String image_url) { this.image_url = image_url; }

    @PropertyName("timestamp")
    public String getTimestamp() { return timestamp; }

    @PropertyName("timestamp")
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    @PropertyName("status")
    public String getStatus() { return status; }

    @PropertyName("status")
    public void setStatus(String status) { this.status = status; }
}