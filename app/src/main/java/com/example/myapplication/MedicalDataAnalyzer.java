package com.example.myapplication;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class MedicalDataAnalyzer {
    private static final String TAG = "MedicalAnalyzer";
    private final Handler handler;

    // Medical data storage
    private int heartRate = 0;
    private int spo2 = 0;
    private final List<Integer> ecgData = new ArrayList<>();
    private String rawData = "";

    // Medical constants
    private static final int MIN_HEART_RATE = 30;
    private static final int MAX_HEART_RATE = 220;
    private static final int MIN_SPO2 = 70;
    private static final int MAX_SPO2 = 100;

    public MedicalDataAnalyzer(Handler handler) {
        this.handler = handler;
    }

    public void processData(String rawData) {
        this.rawData = rawData;
        Log.d(TAG, "Raw data received: " + rawData);

        try {
            // Parse different data formats from Arduino
            if (rawData.contains("HR:") && rawData.contains("SpO2:") && rawData.contains("ECG:")) {
                parseFormattedData(rawData);
            } else if (rawData.contains(",")) {
                parseCsvData(rawData);
            } else if (rawData.matches(".*\\d+.*")) {
                parseNumericData(rawData);
            } else {
                // Try to extract numbers from any format
                extractMedicalData(rawData);
            }

            // Send update to UI
            sendDataUpdate();

        } catch (Exception e) {
            Log.e(TAG, "Error processing medical data: " + rawData, e);
        }
    }

    private void parseFormattedData(String data) {
        // Example: "HR:75 SpO2:98 ECG:512"
        try {
            String[] parts = data.split(" ");
            for (String part : parts) {
                if (part.startsWith("HR:")) {
                    heartRate = Integer.parseInt(part.substring(3));
                } else if (part.startsWith("SpO2:") || part.startsWith("SPO2:") || part.startsWith("O2:")) {
                    spo2 = Integer.parseInt(part.substring(part.indexOf(":") + 1));
                } else if (part.startsWith("ECG:") || part.matches("\\d+")) {
                    int ecgValue = Integer.parseInt(part.replace("ECG:", ""));
                    ecgData.add(ecgValue);
                    if (ecgData.size() > 1000) ecgData.remove(0);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing formatted data", e);
        }
    }

    private void parseCsvData(String data) {
        // Example: "75,98,512" or "HR=75,SpO2=98,ECG=512"
        try {
            String[] values = data.split(",");
            if (values.length >= 3) {
                heartRate = parseNumber(values[0]);
                spo2 = parseNumber(values[1]);
                int ecgValue = parseNumber(values[2]);
                ecgData.add(ecgValue);
                if (ecgData.size() > 1000) ecgData.remove(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing CSV data", e);
        }
    }

    private void parseNumericData(String data) {
        // Example: "75 98 512" or "75\n98\n512"
        try {
            // Extract all numbers from the string
            String[] numbers = data.split("\\D+");
            if (numbers.length >= 3) {
                heartRate = Integer.parseInt(numbers[0]);
                spo2 = Integer.parseInt(numbers[1]);
                int ecgValue = Integer.parseInt(numbers[2]);
                ecgData.add(ecgValue);
                if (ecgData.size() > 1000) ecgData.remove(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing numeric data", e);
        }
    }

    private void extractMedicalData(String data) {
        // Try to find patterns in the data
        try {
            // Look for heart rate pattern (60-100 typical)
            java.util.regex.Pattern hrPattern = java.util.regex.Pattern.compile("\\b([6-9][0-9]|1[0-9]{2})\\b");
            java.util.regex.Matcher hrMatcher = hrPattern.matcher(data);
            if (hrMatcher.find()) {
                heartRate = Integer.parseInt(hrMatcher.group(1));
            }

            // Look for SpO2 pattern (95-100 typical)
            java.util.regex.Pattern spo2Pattern = java.util.regex.Pattern.compile("\\b(9[5-9]|100)\\b");
            java.util.regex.Matcher spo2Matcher = spo2Pattern.matcher(data);
            if (spo2Matcher.find()) {
                spo2 = Integer.parseInt(spo2Matcher.group(1));
            }

            // Look for ECG values (typically 3-4 digit numbers)
            java.util.regex.Pattern ecgPattern = java.util.regex.Pattern.compile("\\b\\d{3,4}\\b");
            java.util.regex.Matcher ecgMatcher = ecgPattern.matcher(data);
            if (ecgMatcher.find()) {
                int ecgValue = Integer.parseInt(ecgMatcher.group());
                ecgData.add(ecgValue);
                if (ecgData.size() > 1000) ecgData.remove(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting medical data", e);
        }
    }

    private int parseNumber(String value) {
        try {
            // Remove non-digit characters
            String digits = value.replaceAll("\\D", "");
            return Integer.parseInt(digits);
        } catch (Exception e) {
            return 0;
        }
    }

    private void sendDataUpdate() {
        Message msg = handler.obtainMessage(MedicalHandler.DATA_UPDATE);
        Bundle bundle = new Bundle();
        bundle.putInt("heart_rate", heartRate);
        bundle.putInt("spo2", spo2);
        bundle.putInt("ecg_samples", ecgData.size());
        bundle.putString("raw_data", rawData);
        msg.setData(bundle);
        handler.sendMessage(msg);
    }

    // Data analysis methods
    public boolean isDataValid() {
        return heartRate >= MIN_HEART_RATE && heartRate <= MAX_HEART_RATE &&
                spo2 >= MIN_SPO2 && spo2 <= MAX_SPO2;
    }

    public String getHealthStatus() {
        if (!isDataValid()) return "Invalid data";

        if (heartRate < 60) return "Bradycardia (Low HR)";
        if (heartRate > 100) return "Tachycardia (High HR)";
        if (spo2 < 95) return "Low Oxygen";
        if (spo2 >= 95 && spo2 <= 100) return "Normal";

        return "Unknown status";
    }

    public int getAverageHeartRate() {
        return heartRate; // Simple implementation
    }

    public int getAverageSpO2() {
        return spo2; // Simple implementation
    }

    public List<Integer> getRecentEcgData(int count) {
        count = Math.min(count, ecgData.size());
        return new ArrayList<>(ecgData.subList(ecgData.size() - count, ecgData.size()));
    }

    public void reset() {
        heartRate = 0;
        spo2 = 0;
        ecgData.clear();
        rawData = "";
    }
}