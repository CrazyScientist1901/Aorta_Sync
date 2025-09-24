    package com.example.myapplication;

    import android.app.Activity;
    import android.content.Intent;
    import android.net.Uri;
    import android.os.Bundle;
    import android.util.Log;
    import android.view.LayoutInflater;
    import android.view.View;
    import android.view.ViewGroup;
    import android.widget.Button;
    import android.widget.ProgressBar;
    import android.widget.TextView;
    import android.widget.Toast;

    import androidx.annotation.NonNull;
    import androidx.annotation.Nullable;
    import androidx.fragment.app.Fragment;

    import java.io.BufferedReader;
    import java.io.InputStream;
    import java.io.InputStreamReader;
    import java.util.ArrayList;
    import java.util.List;
    import java.util.concurrent.atomic.AtomicBoolean;

    public class AnalyseFragment extends Fragment {

        private static final String TAG = "Analyser";
        private static final int PICK_CSV_FILE = 1001;

        // View references
        private ECGClassifier ecgClassifier;
        private Button btnProcessCSV, btnRealTime;
        private ProgressBar progressBar;
        private TextView tvStatus, resultsTextView, tvNormalBeats, tvAbnormalBeats, tvTotalBeats;

        // State management
        private final AtomicBoolean isFragmentActive = new AtomicBoolean(false);
        private boolean isProcessing = false;
        private Uri currentFileUri;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater,
                                 @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.analyse, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            isFragmentActive.set(true);
            initializeViews(view);
            initializeClassifier();
            setupClickListeners();
        }

        @Override
        public void onResume() {
            super.onResume();
            isFragmentActive.set(true);
            // This is a more reliable way to update the UI state
            updateFileSelectionStatus();
        }

        @Override
        public void onPause() {
            super.onPause();
            isFragmentActive.set(false);
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            cleanupResources();
        }

        private void initializeViews(View view) {
            try {
                btnProcessCSV = view.findViewById(R.id.btnProcessCSV);
                btnRealTime = view.findViewById(R.id.btnRealTime);
                progressBar = view.findViewById(R.id.progressBar);
                tvStatus = view.findViewById(R.id.tvStatus);
                resultsTextView = view.findViewById(R.id.resultsTextView);
                tvNormalBeats = view.findViewById(R.id.tvNormalBeats);
                tvAbnormalBeats = view.findViewById(R.id.tvAbnormalBeats);
                tvTotalBeats = view.findViewById(R.id.tvTotalBeats);

                progressBar.setVisibility(View.GONE);
                updateFileSelectionStatus();

            } catch (Exception e) {
                Log.e(TAG, "Error initializing views", e);
                if (tvStatus != null) {
                    tvStatus.setText("Error initializing UI components");
                }
            }
        }

        private void updateFileSelectionStatus() {
            if (currentFileUri != null) {
                btnProcessCSV.setText("Process Selected File");
                resultsTextView.setText("File ready: " + getFileNameFromUri(currentFileUri) + "\n\nClick the button to analyze this ECG file.");
            } else {
                btnProcessCSV.setText("Select CSV File");
                resultsTextView.setText("Select a CSV file with ECG data to analyze");
            }
        }

        private void initializeClassifier() {
            if (!isFragmentActive.get() || getActivity() == null) return;

            try {
                Log.d(TAG, "Initializing ECG classifier...");
                ecgClassifier = new ECGClassifier(getActivity());

                if (ecgClassifier.isModelLoaded()) {
                    Log.i(TAG, "ECG classifier ready");
                    updateUI(() -> {
                        tvStatus.setText("✅ Ready - " + (currentFileUri != null ? "File selected" : "Select CSV file"));
                        btnProcessCSV.setEnabled(true);
                        updateFileSelectionStatus();
                    });
                } else {
                    throw new IllegalStateException("Model not loaded");
                }

            } catch (Exception e) {
                Log.e(TAG, "Classifier init failed", e);
                updateUI(() -> {
                    tvStatus.setText("❌ Classifier failed");
                    resultsTextView.setText("Model loading error: " + e.getMessage());
                    btnProcessCSV.setEnabled(false);
                });
            }
        }

        private void setupClickListeners() {
            // REVISED LOGIC: Check state on every click
            btnProcessCSV.setOnClickListener(v -> {
                if (!isProcessing) {
                    if (currentFileUri != null) {
                        // File is already selected, so let's process it
                        processSelectedCSVFile(currentFileUri);
                    } else {
                        // No file yet, so open the picker
                        openFilePicker();
                    }
                }
            });

            btnRealTime.setOnClickListener(v -> startDemoRealTimeMode());
        }

        private void openFilePicker() {
            try {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                String[] mimeTypes = {
                        "text/csv",
                        "application/csv",
                        "text/comma-separated-values",
                        "application/vnd.ms-excel",
                        "text/plain"
                };
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

                Intent chooserIntent = Intent.createChooser(intent, "Select ECG CSV File");
                startActivityForResult(chooserIntent, PICK_CSV_FILE);

            } catch (Exception e) {
                Log.e(TAG, "File picker error", e);
                updateUI(() -> {
                    tvStatus.setText("Error opening file picker");
                    resultsTextView.setText("Cannot open file picker: " + e.getMessage());
                    Toast.makeText(getContext(), "File picker error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
            super.onActivityResult(requestCode, resultCode, data);

            Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

            if (requestCode == PICK_CSV_FILE) {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    Uri uri = data.getData();
                    if (uri != null) {
                        Log.d(TAG, "File selected: " + uri.toString());

                        // Immediately update UI to show file selection
                        updateUI(() -> {
                            tvStatus.setText("File selected ✓");
                            resultsTextView.setText("File selected: " + getFileNameFromUri(uri) + "\n\nChecking file validity...");
                            Toast.makeText(getContext(), "File selected: " + getFileNameFromUri(uri), Toast.LENGTH_SHORT).show();
                        });

                        // Take persistent permissions
                        try {
                            if (getActivity() != null) {
                                final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                getActivity().getContentResolver().takePersistableUriPermission(uri, takeFlags);
                                Log.d(TAG, "Persistent permission granted");
                            }
                        } catch (SecurityException e) {
                            Log.w(TAG, "Could not get persistent permission, will try without");
                        }

                        // Validate file type
                        if (!isValidCSVFile(uri)) {
                            Log.e(TAG, "Invalid file type selected");
                            updateUI(() -> {
                                tvStatus.setText("❌ Invalid File");
                                resultsTextView.setText("Selected file is not a valid CSV format.\n\nFile: " + getFileNameFromUri(uri) +
                                        "\n\nPlease select a .csv file with ECG data.");
                                Toast.makeText(getContext(), "Invalid file type", Toast.LENGTH_LONG).show();
                            });
                            return;
                        }

                        // Store the URI and update UI
                        currentFileUri = uri;
                        updateUI(this::updateFileSelectionStatus);

                    } else {
                        Log.e(TAG, "Selected file URI is null");
                        updateUI(() -> {
                            tvStatus.setText("Selection Error");
                            resultsTextView.setText("No file was selected. Please try again.");
                            Toast.makeText(getContext(), "No file selected", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    Log.d(TAG, "File selection cancelled by user");
                    updateUI(() -> {
                        tvStatus.setText("Selection Cancelled");
                        Toast.makeText(getContext(), "File selection cancelled", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    Log.e(TAG, "File selection failed with result code: " + resultCode);
                    updateUI(() -> {
                        tvStatus.setText("Selection Failed");
                        resultsTextView.setText("Could not select file. Error code: " + resultCode);
                        Toast.makeText(getContext(), "File selection failed", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }

        private boolean isValidCSVFile(Uri fileUri) {
            try {
                if (getActivity() == null) return false;

                String fileName = getFileNameFromUri(fileUri).toLowerCase();
                boolean hasCsvExtension = fileName.endsWith(".csv") || fileName.endsWith(".txt");

                // Quick content check: Try to read the first line
                try {
                    InputStream stream = getActivity().getContentResolver().openInputStream(fileUri);
                    if (stream != null) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                        String firstLine = reader.readLine();
                        reader.close();
                        stream.close();

                        if (firstLine != null && firstLine.matches(".*\\d+.*")) {
                            Log.d(TAG, "File appears to contain numeric data");
                            return true;
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Quick content check failed: " + e.getMessage());
                }

                return hasCsvExtension;

            } catch (Exception e) {
                Log.e(TAG, "Error validating file type", e);
                return false;
            }
        }

        private String getFileNameFromUri(Uri uri) {
            try {
                String fileName = null;

                if ("content".equals(uri.getScheme()) && getActivity() != null) {
                    try (android.database.Cursor cursor = getActivity().getContentResolver().query(
                            uri, null, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                            if (nameIndex != -1) {
                                fileName = cursor.getString(nameIndex);
                            }
                        }
                    }
                }

                if (fileName == null) {
                    fileName = uri.getLastPathSegment();
                }

                return fileName != null ? fileName : "Unknown File";

            } catch (Exception e) {
                Log.e(TAG, "Error getting file name", e);
                return "Unknown File";
            }
        }

        private void processSelectedCSVFile(Uri fileUri) {
            Log.d(TAG, "Starting CSV processing for: " + fileUri);
            if (!isFragmentActive.get() || getActivity() == null) {
                Log.w(TAG, "Fragment not active, skipping processing");
                return;
            }

            isProcessing = true;
            setLoadingState(true);
            updateUI(() -> {
                tvStatus.setText("📊 Analyzing ECG...");
                resultsTextView.setText("Processing file: " + getFileNameFromUri(fileUri) +
                        "\n\nSteps:\n• Reading file data\n• Preprocessing signal\n• Detecting heartbeats\n• Classifying rhythms\n\nPlease wait...");
            });

            new Thread(() -> {
                try {
                    // Read file content
                    updateUI(() -> tvStatus.setText("Reading file..."));
                    String fileContent = readFileContent(fileUri);
                    if (fileContent == null || fileContent.isEmpty()) {
                        throw new Exception("File is empty or cannot be read");
                    }

                    // Parse ECG values
                    updateUI(() -> tvStatus.setText("Parsing ECG data..."));
                    List<Double> ecgValues = parseECGValuesFromContent(fileContent);
                    Log.d(TAG, "Parsed " + ecgValues.size() + " ECG values");

                    if (ecgValues.isEmpty()) {
                        throw new Exception("No valid ECG data found. Please check the file format.");
                    }

                    // Preprocess data
                    updateUI(() -> tvStatus.setText("Preprocessing signal..."));
                    float[] processedData = preprocessECGData(ecgValues);

                    // Analyze data
                    updateUI(() -> tvStatus.setText("Analyzing heartbeats..."));
                    List<ECGClassifier.BeatClassification> results = analyzeECGData(processedData);

                    // Display results
                    if (isFragmentActive.get()) {
                        updateUI(() -> displayResults(results, fileUri, ecgValues.size()));
                    }

                } catch (Exception e) {
                    Log.e(TAG, "ECG processing error", e);
                    if (isFragmentActive.get()) {
                        updateUI(() -> {
                            setLoadingState(false);
                            isProcessing = false;
                            tvStatus.setText("❌ Processing Failed");
                            resultsTextView.setText("Error processing file:\n" + e.getMessage() +
                                    "\n\nPlease ensure:\n• File is a valid CSV with numeric values\n• File is not corrupted\n• Try selecting the file again");
                            Toast.makeText(getContext(), "Processing failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }
                } finally {
                    isProcessing = false;
                }
            }).start();
        }

        private String readFileContent(Uri fileUri) throws Exception {
            StringBuilder content = new StringBuilder();
            try (InputStream inputStream = requireActivity().getContentResolver().openInputStream(fileUri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

                if (inputStream == null) {
                    Log.e(TAG, "Input stream is null. The file cannot be opened from the provided URI.");
                    throw new Exception("Cannot open file stream");
                }

                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null) {
                    lineCount++;
                    content.append(line).append("\n");
                }

                Log.d(TAG, "Read " + lineCount + " lines from file");
                return content.toString();

            } catch (SecurityException e) {
                Log.e(TAG, "Security exception reading file.", e);
                throw new Exception("Permission denied to read the file");
            } catch (Exception e) {
                Log.e(TAG, "Error reading file content", e);
                throw new Exception("Error reading file: " + e.getMessage());
            }
        }

        private void logFileInfo(Uri fileUri) {
            try {
                Log.d(TAG, "=== FILE INFO ===");
                Log.d(TAG, "URI: " + fileUri.toString());
                Log.d(TAG, "Scheme: " + fileUri.getScheme());
                Log.d(TAG, "Path: " + fileUri.getPath());
                Log.d(TAG, "Last segment: " + fileUri.getLastPathSegment());

                try {
                    InputStream stream = getActivity().getContentResolver().openInputStream(fileUri);
                    if (stream != null) {
                        int available = stream.available();
                        Log.d(TAG, "File size: " + available + " bytes");
                        stream.close();
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Could not determine file size: " + e.getMessage());
                }
                Log.d(TAG, "=== END FILE INFO ===");
            } catch (Exception e) {
                Log.e(TAG, "Error logging file info", e);
            }
        }

        private List<Double> parseECGValuesFromContent(String content) {
            List<Double> values = new ArrayList<>();
            String[] lines = content.split("\n");
            boolean headerSkipped = false;

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();

                if (line.isEmpty()) {
                    continue;
                }

                if (!headerSkipped && isHeaderLine(line)) {
                    Log.d(TAG, "Skipping header line: " + line);
                    headerSkipped = true;
                    continue;
                }

                try {
                    Double value = parseLineToDouble(line);
                    if (value != null) {
                        values.add(value);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Skipping line " + (i + 1) + ": " + line);
                }
            }
            return values;
        }

        private boolean isHeaderLine(String line) {
            String lowerLine = line.toLowerCase();
            return lowerLine.contains("ecg") ||
                    lowerLine.contains("value") ||
                    lowerLine.contains("time") ||
                    !line.matches(".*\\d+.*");
        }

        private Double parseLineToDouble(String line) {
            try {
                String[] parts = line.split("[,\\s;]");
                for (String part : parts) {
                    String cleanPart = part.trim()
                            .replace("\"", "")
                            .replace(",", ".");
                    if (!cleanPart.isEmpty()) {
                        try {
                            return Double.parseDouble(cleanPart);
                        } catch (NumberFormatException e) {
                            // Continue to the next part
                        }
                    }
                }
                return null;
            } catch (Exception e) {
                Log.w(TAG, "Cannot parse as double: " + line);
                return null;
            }
        }

        private float[] preprocessECGData(List<Double> rawValues) {
            float[] processed = new float[rawValues.size()];

            double min = Double.MAX_VALUE;
            double max = Double.MIN_VALUE;
            for (double value : rawValues) {
                if (value < min) min = value;
                if (value > max) max = value;
            }

            double range = max - min;
            if (range == 0) range = 1;

            for (int i = 0; i < rawValues.size(); i++) {
                processed[i] = (float) ((rawValues.get(i) - min) / range);
            }

            Log.d(TAG, "Normalized ECG data: min=" + min + ", max=" + max);
            return processed;
        }

        private List<ECGClassifier.BeatClassification> analyzeECGData(float[] ecgData) {
            if (ecgClassifier == null) {
                throw new IllegalStateException("ECG classifier not available");
            }
            try {
                String csvContent = convertToCSVFormat(ecgData);
                InputStream stream = new java.io.ByteArrayInputStream(csvContent.getBytes());
                List<ECGClassifier.BeatClassification> results = ecgClassifier.classifyCSVData(stream);
                stream.close();
                return results;
            } catch (Exception e) {
                Log.e(TAG, "ECG analysis error", e);
                throw new RuntimeException("Analysis failed: " + e.getMessage());
            }
        }

        private String convertToCSVFormat(float[] ecgData) {
            StringBuilder csv = new StringBuilder();
            for (int i = 0; i < ecgData.length; i++) {
                csv.append(i * 0.004).append(",").append(ecgData[i]).append("\n");
            }
            return csv.toString();
        }

        private void displayResults(List<ECGClassifier.BeatClassification> results, Uri fileUri, int totalSamples) {
            setLoadingState(false);
            isProcessing = false;
            if (!isFragmentActive.get()) return;

            if (results == null || results.isEmpty()) {
                String message = "No heartbeats detected in the ECG signal.\n\nPossible reasons:\n" +
                        "• Signal may be too noisy\n" +
                        "• Sampling rate may be incorrect\n" +
                        "• ECG signal may be weak\n" +
                        "• Try recording a longer ECG\n";
                updateUI(() -> {
                    resultsTextView.setText(message);
                    tvStatus.setText("No beats detected");
                    tvNormalBeats.setText("Normal: 0");
                    tvAbnormalBeats.setText("Abnormal: 0");
                    tvTotalBeats.setText("Total: 0");
                });
                return;
            }

            int normalBeats = 0;
            int abnormalBeats = 0;
            StringBuilder detailedResults = new StringBuilder();
            detailedResults.append("--- Detailed Results ---\n");

            for (ECGClassifier.BeatClassification beat : results) {
                String beatType = getBeatTypeDescription(beat.type);
                detailedResults.append("Beat detected at index ").append(beat.beatIndex)
                        .append(": ").append(beatType).append("\n");
                if (beat.type == ECGClassifier.BEAT_TYPE_NORMAL) {
                    normalBeats++;
                } else {
                    abnormalBeats++;
                }
            }
            int totalBeats = normalBeats + abnormalBeats;

            StringBuilder finalResult = new StringBuilder();
            finalResult.append("ECG ANALYSIS RESULTS\n");
            finalResult.append("File: ").append(getFileNameFromUri(fileUri)).append("\n");
            finalResult.append("Total samples: ").append(totalSamples).append("\n");
            finalResult.append("Total beats analyzed: ").append(totalBeats).append("\n\n");
            finalResult.append("Normal beats: ").append(normalBeats).append("\n");
            finalResult.append("Abnormal beats: ").append(abnormalBeats).append("\n\n");
            if (abnormalBeats > 0) {
                finalResult.append("❗ **Abnormalities Detected** ❗\n");
                finalResult.append("Please consult a medical professional with this data.\n");
            } else {
                finalResult.append("✅ **No Abnormalities Detected** ✅\n");
                finalResult.append("The analyzed signal appears to have a normal rhythm.\n");
            }
            finalResult.append("\n\n").append(detailedResults);

            int finalNormalBeats = normalBeats;
            int finalAbnormalBeats = abnormalBeats;
            updateUI(() -> {
                resultsTextView.setText(finalResult.toString());
                tvStatus.setText("✅ Analysis Complete!");
                tvNormalBeats.setText("Normal: " + finalNormalBeats);
                tvAbnormalBeats.setText("Abnormal: " + finalAbnormalBeats);
                tvTotalBeats.setText("Total: " + totalBeats);
            });
        }

        private String getBeatTypeDescription(int beatType) {
            switch (beatType) {
                case ECGClassifier.BEAT_TYPE_NORMAL:
                    return "Normal Beat (N)";
                case ECGClassifier.BEAT_TYPE_LEFT_BUNDLE_BRANCH_BLOCK:
                    return "Supraventricular Ectopic Beat (L)";
                case ECGClassifier.BEAT_TYPE_RIGHT_BUNDLE_BRANCH_BLOCK:
                    return "Ventricular Ectopic Beat (R)";
                case ECGClassifier.BEAT_TYPE_ATRIAL_PREMATURE:
                    return "Fusion Beat (A)";
                case ECGClassifier.BEAT_TYPE_PREMATURE_VENTRICULAR:
                    return "Fusion Beat (V)";
                case ECGClassifier.BEAT_TYPE_UNKNOWN:
                    return "Fusion Beat (U)";
                default:
                    return "Unknown/Unclassified Beat (Q)";
            }
        }

        private void setLoadingState(boolean isLoading) {
            updateUI(() -> {
                progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
                btnProcessCSV.setEnabled(!isLoading);
                btnProcessCSV.setText(isLoading ? "Analyzing..." : "Select CSV File");

                if (isLoading) {
                    resultsTextView.setText("Analyzing ECG data...\n\n• Reading file\n• Preprocessing signal\n• Detecting heartbeats\n• Classifying rhythms");
                }
            });
        }

        private void updateUI(Runnable uiUpdates) {
            if (getActivity() != null && isFragmentActive.get()) {
                getActivity().runOnUiThread(uiUpdates);
            }
        }

        private void startDemoRealTimeMode() {
            updateUI(() -> {
                tvStatus.setText("Real-time ECG Demo");
                resultsTextView.setText("Real-time ECG Monitoring\n\nThis would connect to:\n• ECG sensor via Bluetooth\n• Chest strap monitor\n• Handheld ECG device\n\nFeature coming soon!");
            });
        }

        private void cleanupResources() {
            isFragmentActive.set(false);
            isProcessing = false;
            currentFileUri = null;
            if (ecgClassifier != null) {
                try {
                    ecgClassifier.close();
                } catch (Exception e) {
                    Log.w(TAG, "Error closing classifier", e);
                }
            }
        }
    }