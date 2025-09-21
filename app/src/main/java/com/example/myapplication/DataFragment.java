package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class DataFragment extends Fragment {
    private FirebaseAuth mAuth;
    private static final String TAG = "ECGMonitor";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String ESP32_MAC_ADDRESS = "6C:C8:40:4E:B1:36";
    private static final long RECORDING_DURATION = 7 * 60 * 1000; // 7 minutes

    // UI Components
    private TextView textViewECG, textViewStatus, textViewHeartRate, textViewTimer;
    private Button btnConnect, btnDisconnect, btnExportData, btnAnalyzeData;

    // Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private ConnectedThread connectedThread;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isConnected = false;

    // ECG Data
    private int heartRate = 0;
    private long lastPeakTime = 0;
    private static final int ECG_PEAK_THRESHOLD = 2500;

    // Data logging - now only storing ECG values
    private StringBuilder ecgData = new StringBuilder();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());

    // Timer
    private CountDownTimer recordingTimer;
    private boolean isRecording = false;

    // Permissions
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int BLUETOOTH_ENABLE_REQUEST_CODE = 2;
    private FirebaseFirestore db;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_data, container, false);

        // Initialize Firebase components
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        setupExceptionHandler();
        initViews(view);
        checkAndRequestPermissions();

        return view;
    }

    private void setupExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "CRASH: " + throwable.getMessage(), throwable);
            handler.post(() -> Toast.makeText(requireActivity(), "App crashed: " + throwable.getMessage(), Toast.LENGTH_SHORT).show());
        });
    }


    private void initViews(View view) {
        textViewECG = view.findViewById(R.id.textViewECG);
        textViewStatus = view.findViewById(R.id.textViewStatus);
        textViewHeartRate = view.findViewById(R.id.textViewHeartRate);
        textViewTimer = view.findViewById(R.id.textViewTimer);
        btnConnect = view.findViewById(R.id.btnConnect);
        btnDisconnect = view.findViewById(R.id.btnDisconnect);
        btnExportData = view.findViewById(R.id.btnExportData);
        btnAnalyzeData = view.findViewById(R.id.btnAnalyzeData);

        btnConnect.setOnClickListener(v -> connectToESP32());
        btnDisconnect.setOnClickListener(v -> disconnect());
        btnExportData.setOnClickListener(v -> exportDataToCSV());
        btnAnalyzeData.setOnClickListener(v -> analyzeECGData());

        updateButtonStates(false);
        textViewTimer.setText("Timer: 07:00");

        // Debug log
        Log.d(TAG, "Connect button initialized. Enabled: " + btnConnect.isEnabled());

        // Initially disable connect button until permissions are granted
        btnConnect.setEnabled(false);
        Log.d(TAG, "Connect button disabled after initialization");
    }

    private void updateButtonStates(boolean connected) {
        btnConnect.setEnabled(!connected);
        btnDisconnect.setEnabled(connected);
        // Only enable export/analyze after recording completes
        if (!connected) {
            btnExportData.setEnabled(ecgData.length() > 0);
            btnAnalyzeData.setEnabled(ecgData.length() > 0);
        }
    }

    private void checkAndRequestPermissions() {
        Log.d(TAG, "Checking permissions...");

        // For Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] permissions = {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };

            boolean allGranted = true;
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(requireActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    Log.d(TAG, "Permission missing: " + permission);
                    break;
                }
            }

            if (allGranted) {
                Log.d(TAG, "All Android 12+ permissions granted");
                checkBluetoothEnabled();
            } else {
                Log.d(TAG, "Requesting Android 12+ permissions");
                ActivityCompat.requestPermissions(requireActivity(), permissions, PERMISSION_REQUEST_CODE);
            }
        }
        // For Android 6.0 to Android 11
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };

            boolean allGranted = true;
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(requireActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    Log.d(TAG, "Permission missing: " + permission);
                    break;
                }
            }

            if (allGranted) {
                Log.d(TAG, "All Android 6-11 permissions granted");
                checkBluetoothEnabled();
            } else {
                Log.d(TAG, "Requesting Android 6-11 permissions");
                ActivityCompat.requestPermissions(requireActivity(), permissions, PERMISSION_REQUEST_CODE);
            }
        }
        // For Android below 6.0 (no runtime permissions needed)
        else {
            Log.d(TAG, "Android <6.0, no runtime permissions needed");
            checkBluetoothEnabled();
        }
    }

    private void checkBluetoothEnabled() {
        Log.d(TAG, "Checking Bluetooth enabled...");

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            handler.post(() -> {
                Toast.makeText(requireActivity(), "Device does not support Bluetooth", Toast.LENGTH_LONG).show();
                textViewStatus.setText("Bluetooth not supported");
                Log.d(TAG, "Bluetooth not supported on this device");
            });
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth not enabled, requesting enable...");
            handler.post(() -> {
                textViewStatus.setText("Please enable Bluetooth...");
            });

            // Request user to enable Bluetooth
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            try {
                startActivityForResult(enableBtIntent, BLUETOOTH_ENABLE_REQUEST_CODE);
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception enabling Bluetooth: " + e.getMessage());
                handler.post(() -> {
                    Toast.makeText(requireActivity(), "Need Bluetooth permission to enable", Toast.LENGTH_LONG).show();
                });
            }
        } else {
            Log.d(TAG, "Bluetooth already enabled");
            handler.post(() -> {
                btnConnect.setEnabled(true);
                Log.d(TAG, "Connect button enabled - Bluetooth ready");
                textViewStatus.setText("Ready to connect");
                Toast.makeText(requireActivity(), "Bluetooth enabled. Ready to connect!", Toast.LENGTH_SHORT).show();
            });
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == BLUETOOTH_ENABLE_REQUEST_CODE) {
            if (resultCode == getActivity().RESULT_OK) {
                // Bluetooth enabled successfully
                Log.d(TAG, "Bluetooth enabled by user");
                btnConnect.setEnabled(true);
                textViewStatus.setText("Bluetooth enabled - Ready to connect");
                Toast.makeText(requireActivity(), "Bluetooth enabled. Ready to connect!", Toast.LENGTH_SHORT).show();
            } else {
                // User denied to enable Bluetooth
                Log.d(TAG, "User denied Bluetooth enable");
                Toast.makeText(requireActivity(), "Bluetooth is required to connect to ECG device", Toast.LENGTH_LONG).show();
                btnConnect.setEnabled(false);
                textViewStatus.setText("Bluetooth disabled - Enable in settings");
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "Fragment resumed, checking permissions again");

        // Re-check if we have permissions and Bluetooth is enabled
        if (hasAllPermissions() && isBluetoothEnabled()) {
            btnConnect.setEnabled(true);
            Log.d(TAG, "Connect button enabled on resume");
        }
    }

    private boolean hasAllPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private boolean isBluetoothEnabled() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Check if all permissions are granted
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Log.d(TAG, "All permissions granted");
                checkBluetoothEnabled();
            } else {
                Log.d(TAG, "Some permissions denied");
                handler.post(() -> {
                    Toast.makeText(requireActivity(), "Bluetooth permissions are required to connect", Toast.LENGTH_LONG).show();
                    textViewStatus.setText("Permissions denied");
                    btnConnect.setEnabled(false);
                });
            }
        }
    }

    private void connectToESP32() {
        handler.post(() -> {
            textViewStatus.setText("Connecting...");
            textViewStatus.setTextColor(Color.YELLOW);
        });

        new Thread(() -> {
            try {
                if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                    handler.post(() -> {
                        Toast.makeText(requireActivity(), "Bluetooth not enabled", Toast.LENGTH_SHORT).show();
                        textViewStatus.setText("Bluetooth disabled");
                    });
                    return;
                }

                BluetoothDevice device;
                try {
                    device = bluetoothAdapter.getRemoteDevice(ESP32_MAC_ADDRESS);
                } catch (IllegalArgumentException e) {
                    handler.post(() -> {
                        Toast.makeText(requireActivity(), "Invalid MAC address format", Toast.LENGTH_SHORT).show();
                        textViewStatus.setText("Invalid MAC address");
                    });
                    return;
                }

                // Method 1: Standard connection
                try {
                    // Check for Bluetooth connect permission
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            handler.post(() -> textViewStatus.setText("Bluetooth permission denied"));
                            return;
                        }
                    }

                    bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                    bluetoothAdapter.cancelDiscovery();
                    bluetoothSocket.connect();
                    onConnectionSuccess();
                    return;
                } catch (IOException e) {
                    Log.e(TAG, "Standard connection failed", e);
                }

                // Method 2: Reflection fallback
                try {
                    bluetoothSocket = (BluetoothSocket) device.getClass()
                            .getMethod("createRfcommSocket", int.class)
                            .invoke(device, 1);
                    bluetoothSocket.connect();
                    onConnectionSuccess();
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Reflection connection failed", e);
                }

                handler.post(() -> {
                    textViewStatus.setText("Connection failed");
                    Toast.makeText(requireActivity(), "Check ESP32 power and pairing. Make sure ESP32 is discoverable.", Toast.LENGTH_LONG).show();
                });

            } catch (SecurityException e) {
                Log.e(TAG, "Security exception", e);
                handler.post(() -> {
                    textViewStatus.setText("Permission error");
                    Toast.makeText(requireActivity(), "Bluetooth permission error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Connection error", e);
                handler.post(() -> {
                    textViewStatus.setText("Connection error");
                    Toast.makeText(requireActivity(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void onConnectionSuccess() {
        handler.post(() -> {
            textViewStatus.setText("Connected! Recording...");
            textViewStatus.setTextColor(Color.GREEN);
            isConnected = true;
            updateButtonStates(true);

            ecgData.setLength(0); // Clear previous data

            startRecordingTimer();
            connectedThread = new ConnectedThread(bluetoothSocket);
            connectedThread.start();
        });
    }

    private void startRecordingTimer() {
        if (recordingTimer != null) {
            recordingTimer.cancel();
        }

        recordingTimer = new CountDownTimer(RECORDING_DURATION, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                updateTimerText(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                handler.post(() -> {
                    textViewTimer.setText("Recording Complete!");
                    textViewTimer.setTextColor(Color.RED);
                    Toast.makeText(requireActivity(), "7-minute recording complete", Toast.LENGTH_LONG).show();
                    disconnect();
                    btnExportData.setEnabled(true);
                    btnAnalyzeData.setEnabled(true);
                });
            }
        }.start();

        isRecording = true;
    }

    private void updateTimerText(long millisUntilFinished) {
        int minutes = (int) (millisUntilFinished / 1000) / 60;
        int seconds = (int) (millisUntilFinished / 1000) % 60;
        String timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);

        handler.post(() -> {
            textViewTimer.setText("Timer: " + timeFormatted);
            textViewTimer.setTextColor(minutes < 1 ? Color.RED : Color.BLUE);
        });
    }

    private void disconnect() {
        isRecording = false;
        isConnected = false;

        try {
            if (connectedThread != null) {
                connectedThread.cancel();
                connectedThread = null;
            }
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
                bluetoothSocket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error disconnecting", e);
        }

        if (recordingTimer != null) {
            recordingTimer.cancel();
            recordingTimer = null;
        }

        handler.post(() -> {
            textViewStatus.setText("Disconnected");
            textViewStatus.setTextColor(Color.RED);
            textViewECG.setText("ECG Value: --\nLO+: --\nLO-: --");
            textViewHeartRate.setText("Heart Rate: -- BPM");
            textViewTimer.setText("Timer: 07:00");
            updateButtonStates(false);
        });
    }

    private int calculateHeartRate(int ecgValue) {
        if (ecgValue > ECG_PEAK_THRESHOLD) {
            long currentTime = System.currentTimeMillis();
            if (lastPeakTime > 0) {
                long timeDiff = currentTime - lastPeakTime;
                if (timeDiff > 300) {
                    int newHeartRate = (int) (60000 / timeDiff);
                    if (newHeartRate > 40 && newHeartRate < 200) {
                        heartRate = newHeartRate;
                        handler.post(() -> {
                            textViewHeartRate.setText("Heart Rate: " + heartRate + " BPM");
                        });
                    }
                }
            }
            lastPeakTime = currentTime;
        }
        return heartRate;
    }

    private void exportDataToCSV() {
        if (ecgData.length() == 0) {
            Toast.makeText(requireActivity(), "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String fileName = "ECG_Recording_" + timestamp + ".csv";
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File file = new File(downloadsDir, fileName);

                FileWriter writer = new FileWriter(file);
                writer.write("ECG_Value\n"); // Header
                writer.write(ecgData.toString()); // Only ECG values
                writer.flush();
                writer.close();

                // Upload to Firebase using Report class
                uploadDataToFirestore(fileName);

                handler.post(() -> {
                    Toast.makeText(requireActivity(), "Data exported to Downloads/" + file.getName(), Toast.LENGTH_LONG).show();
                    Log.d(TAG, "File saved: " + file.getAbsolutePath());
                });
            } catch (IOException e) {
                Log.e(TAG, "Export failed", e);
                handler.post(() -> {
                    Toast.makeText(requireActivity(), "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void analyzeECGData() {
        if (ecgData.length() == 0) {
            Toast.makeText(requireActivity(), "No data to analyze", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] values = ecgData.toString().split("\n");
        int dataPoints = values.length;

        String analysis = "ECG Analysis Results:\n" +
                "Duration: 7 minutes\n" +
                "Data Points: " + dataPoints + "\n" +
                "Heart Rate: " + heartRate + " BPM\n" +
                "Ready for export";

        Toast.makeText(requireActivity(), analysis, Toast.LENGTH_LONG).show();
        textViewStatus.setText("Analysis Complete");
    }

    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final BluetoothSocket mmSocket;
        private final StringBuilder receivedData = new StringBuilder();

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error getting input stream", e);
            }
            mmInStream = tmpIn;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (isConnected && isRecording) {
                try {
                    bytes = mmInStream.read(buffer);
                    if (bytes > 0) {
                        String data = new String(buffer, 0, bytes);
                        receivedData.append(data);

                        String fullData = receivedData.toString();
                        if (fullData.contains("\n")) {
                            String[] lines = fullData.split("\n");
                            for (int i = 0; i < lines.length - 1; i++) {
                                processData(lines[i].trim());
                            }
                            receivedData.setLength(0);
                            receivedData.append(lines[lines.length - 1]);
                        }
                    }
                    Thread.sleep(10);
                } catch (IOException e) {
                    Log.e(TAG, "Connection lost", e);
                    disconnect();
                    break;
                } catch (InterruptedException e) {
                    Log.e(TAG, "Thread interrupted", e);
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error", e);
                }
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Close error", e);
            }
        }
    }

    private void processData(String data) {
        if (data.isEmpty()) return;

        try {
            String[] values = data.split(",");
            if (values.length >= 3) {
                int ecgValue = Integer.parseInt(values[0].trim());
                int loPlus = Integer.parseInt(values[1].trim());
                int loMinus = Integer.parseInt(values[2].trim());

                int currentHeartRate = calculateHeartRate(ecgValue);

                // Only store the ECG value
                ecgData.append(ecgValue).append("\n");

                handler.post(() -> {
                    textViewECG.setText(String.format("ECG: %d\nLO+: %d\nLO-: %d", ecgValue, loPlus, loMinus));
                    if (loPlus == 1 || loMinus == 1) {
                        textViewStatus.setText("Electrode disconnected!");
                        textViewStatus.setTextColor(Color.RED);
                    } else {
                        textViewStatus.setText("Connected - Good signal");
                        textViewStatus.setTextColor(Color.GREEN);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing: " + data, e);
        }
    }

    private void uploadDataToFirestore(String fileName) {
        if (mAuth.getCurrentUser() == null) {
            Log.e(TAG, "User not authenticated when trying to upload data");
            handler.post(() -> {
                Toast.makeText(requireActivity(), "Not signed in. Data not uploaded.", Toast.LENGTH_SHORT).show();
            });
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        Log.d(TAG, "Uploading data for user: " + userId);

        // Create a Report object with userId
        Report report = new Report(
                ecgData.toString(),   // ECG values
                new Date(),           // timestamp
                fileName,             // filename
                heartRate,            // heartRate
                RECORDING_DURATION,   // recordingDuration
                userId                // userId - MAKE SURE THIS IS INCLUDED
        );

        db.collection("ecg_reports")
                .add(report)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "DocumentSnapshot added with ID: " + documentReference.getId());
                    handler.post(() -> {
                        Toast.makeText(requireActivity(), "Data uploaded to Firebase", Toast.LENGTH_SHORT).show();
                    });
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error adding document", e);
                    handler.post(() -> {
                        Toast.makeText(requireActivity(), "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
    }
}