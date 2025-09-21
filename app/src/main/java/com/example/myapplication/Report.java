package com.example.myapplication;

import java.util.Date;

public class Report {
    private String ecgValues;
    private Date timestamp;
    private String filename;
    private int heartRate;
    private long recordingDuration;
    private String userId;

    // Required empty constructor for Firestore
    public Report() {
    }

    // 5-parameter constructor (for backward compatibility)
    public Report(String ecgValues, Date timestamp, String filename, int heartRate, long recordingDuration) {
        this.ecgValues = ecgValues;
        this.timestamp = timestamp;
        this.filename = filename;
        this.heartRate = heartRate;
        this.recordingDuration = recordingDuration;
        this.userId = "unknown"; // Default value
    }

    // 6-parameter constructor (with userId)
    public Report(String ecgValues, Date timestamp, String filename, int heartRate, long recordingDuration, String userId) {
        this.ecgValues = ecgValues;
        this.timestamp = timestamp;
        this.filename = filename;
        this.heartRate = heartRate;
        this.recordingDuration = recordingDuration;
        this.userId = userId;
    }

    // Getters and setters
    public String getEcgValues() {
        return ecgValues;
    }

    public void setEcgValues(String ecgValues) {
        this.ecgValues = ecgValues;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public int getHeartRate() {
        return heartRate;
    }

    public void setHeartRate(int heartRate) {
        this.heartRate = heartRate;
    }

    public long getRecordingDuration() {
        return recordingDuration;
    }

    public void setRecordingDuration(long recordingDuration) {
        this.recordingDuration = recordingDuration;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    // Helper method to get the number of ECG data points
    public int getDataPointCount() {
        if (ecgValues == null || ecgValues.isEmpty()) {
            return 0;
        }
        return ecgValues.split("\n").length;
    }

    // Optional: toString method for debugging
    @Override
    public String toString() {
        return "Report{" +
                "filename='" + filename + '\'' +
                ", timestamp=" + timestamp +
                ", heartRate=" + heartRate +
                ", recordingDuration=" + recordingDuration +
                ", userId='" + userId + '\'' +
                ", dataPoints=" + getDataPointCount() +
                '}';
    }
}