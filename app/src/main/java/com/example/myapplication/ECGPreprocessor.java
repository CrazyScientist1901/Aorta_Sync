package com.example.myapplication;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ECGPreprocessor {
    public static final int SEGMENT_LENGTH = 300;

    public static class ProcessedECGData {
        public List<float[]> normalizedBeats;
        public List<Integer> rPeakIndices;

        public ProcessedECGData() {
            normalizedBeats = new ArrayList<>();
            rPeakIndices = new ArrayList<>();
        }

        // Add setter method to allow modification
        public void setRPeakIndices(List<Integer> rPeakIndices) {
            this.rPeakIndices = rPeakIndices;
        }
    }

    public ProcessedECGData processCSVData(InputStream csvInputStream) {
        ProcessedECGData result = new ProcessedECGData();

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(csvInputStream));
            String line;
            List<Float> ecgData = new ArrayList<>();

            // Read CSV data
            while ((line = reader.readLine()) != null) {
                try {
                    float value = Float.parseFloat(line.trim());
                    ecgData.add(value);
                } catch (NumberFormatException e) {
                    // Skip non-numeric lines (headers, etc.)
                }
            }
            reader.close();

            // Detect R-peaks
            List<Integer> rPeaks = detectRPeaks(ecgData);

            // Extract beats around R-peaks
            for (int rPeak : rPeaks) {
                float[] beat = extractBeatSegment(ecgData, rPeak);
                if (beat != null) {
                    float[] normalizedBeat = normalizeBeat(beat);
                    result.normalizedBeats.add(normalizedBeat);
                    result.rPeakIndices.add(rPeak); // This should work now
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return result;
    }

    private List<Integer> detectRPeaks(List<Float> ecgData) {
        List<Integer> rPeaks = new ArrayList<>();
        if (ecgData.isEmpty()) return rPeaks;

        // Calculate threshold based on data
        float maxVal = Float.MIN_VALUE;
        for (float value : ecgData) {
            if (Math.abs(value) > maxVal) {
                maxVal = Math.abs(value);
            }
        }
        float threshold = maxVal * 0.6f; // 60% of max value

        // Simple peak detection
        for (int i = 1; i < ecgData.size() - 1; i++) {
            float current = Math.abs(ecgData.get(i));
            float prev = Math.abs(ecgData.get(i - 1));
            float next = Math.abs(ecgData.get(i + 1));

            if (current > threshold && current > prev && current > next) {
                rPeaks.add(i);
                i += 100; // Skip ahead to avoid multiple detections
            }
        }

        return rPeaks;
    }

    private float[] extractBeatSegment(List<Float> ecgData, int rPeakIndex) {
        int halfSegment = SEGMENT_LENGTH / 2;
        int start = rPeakIndex - halfSegment;
        int end = rPeakIndex + halfSegment;

        if (start < 0 || end >= ecgData.size()) {
            return null; // Not enough data for segment
        }

        float[] segment = new float[SEGMENT_LENGTH];
        for (int i = 0; i < SEGMENT_LENGTH; i++) {
            segment[i] = ecgData.get(start + i);
        }

        return segment;
    }

    private float[] normalizeBeat(float[] beat) {
        if (beat == null || beat.length == 0) return beat;

        // Calculate mean
        float mean = 0;
        for (float value : beat) {
            mean += value;
        }
        mean /= beat.length;

        // Calculate standard deviation
        float variance = 0;
        for (float value : beat) {
            variance += (value - mean) * (value - mean);
        }
        float std = (float) Math.sqrt(variance / beat.length);

        // Avoid division by zero
        if (std < 0.0001f) std = 1.0f;

        // Normalize
        float[] normalized = new float[beat.length];
        for (int i = 0; i < beat.length; i++) {
            normalized[i] = (beat[i] - mean) / std;
        }

        return normalized;
    }
}