package com.survivordetection.models;

public class RescueTeam {
    private String name;
    private String organisation;
    private String designation;
    private String mobile;
    private float distance;
    private String address; // Agar Firestore mein direct address saved hai toh
    private double latitude;  // Firestore mein field ka naam 'latitude' hona chahiye
    private double longitude; // Firestore mein field ka naam 'longitude' hona chahiye

    // 1. Empty Constructor (Firestore ke liye MUST hai)
    public RescueTeam() {}

    // 2. Full Constructor
    public RescueTeam(String name, String organisation, String designation, String mobile, double latitude, double longitude) {
        this.name = name;
        this.organisation = organisation;
        this.designation = designation;
        this.mobile = mobile;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // --- Getters & Setters ---

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOrganisation() { return organisation; }
    public void setOrganisation(String organisation) { this.organisation = organisation; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public float getDistance() { return distance; }
    public void setDistance(float distance) { this.distance = distance; }
}