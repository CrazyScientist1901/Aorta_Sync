package com.example.myapplication;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class ECGClassifier {
    private static final String TAG = "ECGClassifier";

    // Model file names
    private static final String MODEL_FILE = "ecg_model.tflite";
    private static final String LABEL_FILE = "class_names.txt";

    // Beat type constants based on the initial definitions
    public static final int BEAT_TYPE_NORMAL = 0;                    // N - Normal beat
    public static final int BEAT_TYPE_LEFT_BUNDLE_BRANCH_BLOCK = 1;  // L - Left bundle branch block beat
    public static final int BEAT_TYPE_RIGHT_BUNDLE_BRANCH_BLOCK = 2; // R - Right bundle branch block beat
    public static final int BEAT_TYPE_ATRIAL_PREMATURE = 3;          // A - Atrial premature beat
    public static final int BEAT_TYPE_PREMATURE_VENTRICULAR = 4;     // V - Premature ventricular contraction
    public static final int BEAT_TYPE_UNKNOWN = -1;                  // Fallback type

    private Interpreter tflite;
    private List<String> labels;
    private boolean isLoaded = false;

    public ECGClassifier(Context context) {
        Log.d(TAG, "Starting ECGClassifier initialization...");
        Log.d(TAG, "Looking for model: " + MODEL_FILE);
        Log.d(TAG, "Looking for labels: " + LABEL_FILE);

        try {
            // First, list all available assets for debugging
            listAllAssets(context);

            // Load model
            MappedByteBuffer modelBuffer = loadModelFile(context, MODEL_FILE);
            if (modelBuffer == null) {
                Log.e(TAG, "Failed to load model buffer for: " + MODEL_FILE);
                return;
            }

            Log.d(TAG, "Model file loaded successfully, initializing interpreter...");

            // Initialize interpreter with options
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);

            tflite = new Interpreter(modelBuffer, options);
            Log.d(TAG, "TFLite interpreter created successfully");

            // Load labels
            labels = loadLabelList(context, LABEL_FILE);
            Log.d(TAG, "Labels loaded: " + labels.size());

            // Verify that loaded labels match our expected beat types
            verifyLabels();

            // Test the model by checking input/output tensors
            if (tflite.getInputTensorCount() > 0) {
                Log.i(TAG, "Input tensor shape: " + java.util.Arrays.toString(tflite.getInputTensor(0).shape()));
            }
            if (tflite.getOutputTensorCount() > 0) {
                Log.i(TAG, "Output tensor shape: " + java.util.Arrays.toString(tflite.getOutputTensor(0).shape()));
            }

            isLoaded = true;
            Log.i(TAG, "ECGClassifier initialized successfully!");

        } catch (Exception e) {
            Log.e(TAG, "Error initializing ECG classifier", e);
            isLoaded = false;
        }
    }

    private void listAllAssets(Context context) {
        try {
            String[] files = context.getAssets().list("");
            Log.d(TAG, "=== AVAILABLE ASSETS ===");
            for (String file : files) {
                Log.d(TAG, "Asset: " + file);
            }
            Log.d(TAG, "=== END ASSETS LIST ===");
        } catch (IOException e) {
            Log.e(TAG, "Error listing assets", e);
        }
    }

    private MappedByteBuffer loadModelFile(Context context, String modelPath) {
        try {
            Log.d(TAG, "Attempting to load model: " + modelPath);

            AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();

            Log.d(TAG, "Model file details - Start: " + startOffset + ", Length: " + declaredLength);

            MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);

            inputStream.close();
            fileDescriptor.close();

            Log.i(TAG, "Model file loaded successfully: " + modelPath + " (size: " + declaredLength + " bytes)");
            return buffer;

        } catch (IOException e) {
            Log.e(TAG, "Error loading model file: " + modelPath, e);
            return null;
        }
    }

    private List<String> loadLabelList(Context context, String labelPath) {
        List<String> labelList = new ArrayList<>();
        try {
            Log.d(TAG, "Loading labels from: " + labelPath);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(context.getAssets().open(labelPath)));

            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                labelList.add(line.trim());
                lineCount++;
                Log.d(TAG, "Label " + lineCount + ": " + line.trim());
            }
            reader.close();

            Log.i(TAG, "Successfully loaded " + labelList.size() + " labels");

        } catch (IOException e) {
            Log.e(TAG, "Failed to load label file: " + labelPath, e);
            // Fallback to default labels based on initial definitions
            labelList.add("N");
            labelList.add("L");
            labelList.add("R");
            labelList.add("A");
            labelList.add("V");
            Log.w(TAG, "Using default labels due to error");
        }
        return labelList;
    }

    private void verifyLabels() {
        Log.d(TAG, "Verifying loaded labels against expected beat types...");

        // Expected labels based on initial definitions
        String[] expectedLabels = {"N", "L", "R", "A", "V"};

        for (int i = 0; i < expectedLabels.length; i++) {
            if (i < labels.size()) {
                String loadedLabel = labels.get(i);
                if (loadedLabel.equals(expectedLabels[i])) {
                    Log.d(TAG, "Label " + i + " OK: " + loadedLabel + " -> " + getBeatTypeName(i));
                } else {
                    Log.w(TAG, "Label " + i + " mismatch: expected " + expectedLabels[i] +
                            ", got " + loadedLabel);
                }
            } else {
                Log.w(TAG, "Missing expected label for index: " + i);
            }
        }
    }

    public boolean isModelLoaded() {
        return isLoaded && tflite != null;
    }

    public String getModelFileName() {
        return MODEL_FILE;
    }

    public String getLabelFileName() {
        return LABEL_FILE;
    }

    public int getInputShape() {
        if (tflite != null && tflite.getInputTensorCount() > 0) {
            int[] shape = tflite.getInputTensor(0).shape();
            return shape.length > 1 ? shape[1] : -1;
        }
        return -1;
    }

    public List<String> getLabels() {
        return labels;
    }

    public List<BeatClassification> classifyCSVData(java.io.InputStream csvStream) {
        List<BeatClassification> results = new ArrayList<>();

        if (!isModelLoaded()) {
            Log.e(TAG, "Model not loaded, cannot classify data");
            return results;
        }

        Log.d(TAG, "Starting CSV data classification...");

        try {
            // Your real classification logic would go here.
            // For now, this is a placeholder that simulates classification
            // with the correct beat types based on initial definitions

            int[] classTypes = {
                    BEAT_TYPE_NORMAL,                    // N
                    BEAT_TYPE_LEFT_BUNDLE_BRANCH_BLOCK,  // L
                    BEAT_TYPE_RIGHT_BUNDLE_BRANCH_BLOCK, // R
                    BEAT_TYPE_ATRIAL_PREMATURE,          // A
                    BEAT_TYPE_PREMATURE_VENTRICULAR      // V
            };

            // Simulate classification of 10 beats
            for (int i = 0; i < 10; i++) {
                BeatClassification result = new BeatClassification();
                result.beatIndex = i;

                // Cycle through different beat types for demonstration
                result.type = classTypes[i % classTypes.length];
                result.confidence = 0.85f + (i * 0.02f);

                // Ensure confidence doesn't exceed 1.0
                if (result.confidence > 1.0f) {
                    result.confidence = 1.0f;
                }

                results.add(result);

                Log.d(TAG, "Generated beat " + i + ": " + getBeatTypeName(result.type) +
                        " (" + result.confidence + ")");
            }

            Log.i(TAG, "Classification completed. Results: " + results.size());

        } catch (Exception e) {
            Log.e(TAG, "Error in classification: " + e.getMessage(), e);
        }

        return results;
    }

    /**
     * Helper method to get the display name for a beat type
     */
    public static String getBeatTypeName(int beatType) {
        switch (beatType) {
            case BEAT_TYPE_NORMAL:
                return "Normal beat";
            case BEAT_TYPE_LEFT_BUNDLE_BRANCH_BLOCK:
                return "Left bundle branch block beat";
            case BEAT_TYPE_RIGHT_BUNDLE_BRANCH_BLOCK:
                return "Right bundle branch block beat";
            case BEAT_TYPE_ATRIAL_PREMATURE:
                return "Atrial premature beat";
            case BEAT_TYPE_PREMATURE_VENTRICULAR:
                return "Premature ventricular contraction";
            case BEAT_TYPE_UNKNOWN:
                return "Unknown beat type";
            default:
                return "Invalid beat type";
        }
    }

    /**
     * Helper method to get the short code for a beat type
     */
    public static String getBeatTypeCode(int beatType) {
        switch (beatType) {
            case BEAT_TYPE_NORMAL:
                return "N";
            case BEAT_TYPE_LEFT_BUNDLE_BRANCH_BLOCK:
                return "L";
            case BEAT_TYPE_RIGHT_BUNDLE_BRANCH_BLOCK:
                return "R";
            case BEAT_TYPE_ATRIAL_PREMATURE:
                return "A";
            case BEAT_TYPE_PREMATURE_VENTRICULAR:
                return "V";
            case BEAT_TYPE_UNKNOWN:
                return "U";
            default:
                return "?";
        }
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
            Log.d(TAG, "TFLite interpreter closed");
        }
        isLoaded = false;
    }

    public static class BeatClassification {
        public int beatIndex;
        public int type;  // Uses the BEAT_TYPE constants
        public float confidence;

        // Helper method to get display name for this beat
        public String getTypeName() {
            return ECGClassifier.getBeatTypeName(this.type);
        }

        // Helper method to get short code for this beat
        public String getTypeCode() {
            return ECGClassifier.getBeatTypeCode(this.type);
        }
    }
}