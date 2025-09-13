package com.example.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import java.util.Set;
import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class DataFragment extends Fragment {
    private static final String TAG = "FrugalLogs";
    private static final int REQUEST_ENABLE_BT = 1;
    public static Handler handler;
    private final static int ERROR_READ = 0;
    BluetoothDevice arduinoBTModule = null;
    UUID arduinoUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private TextView btReadings;
    private TextView btDevices;
    private Button connectToDevice;
    private Button seachDevices;
    private Button clearValues;

    private MedicalDataAnalyzer medicalAnalyzer;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_data, container, false);

        // Initialize UI elements
        btReadings = view.findViewById(R.id.btReadings);
        btDevices = view.findViewById(R.id.btDevices);
        connectToDevice = view.findViewById(R.id.connectToDevice);
        seachDevices = view.findViewById(R.id.seachDevices);
        clearValues = view.findViewById(R.id.refresh);

        Log.d(TAG, "DataFragment created");

        // Setup medical analyzer
        medicalAnalyzer = new MedicalDataAnalyzer(handler);

        // Setup handlers and listeners
        setupHandler();
        setupListeners();

        return view;
    }

    private void setupHandler() {
        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case ERROR_READ:
                        String arduinoMsg = msg.obj.toString();
                        btReadings.setText(arduinoMsg);
                        break;
                    case MedicalHandler.DATA_UPDATE:
                        Bundle data = msg.getData();
                        int heartRate = data.getInt("heart_rate", 0);
                        int spo2 = data.getInt("spo2", 0);
                        int ecgSamples = data.getInt("ecg_samples", 0);
                        String rawData = data.getString("raw_data", "");

                        String reading = String.format(
                                "HR: %d bpm\nSpO₂: %d%%\nECG Samples: %d\nStatus: %s\n\nRaw: %s",
                                heartRate, spo2, ecgSamples,
                                medicalAnalyzer.getHealthStatus(),
                                rawData
                        );
                        btReadings.setText(reading);
                        break;
                }
            }
        };
    }

    private void connectToBluetoothDevice() {
        Observable<String> connectToBTObservable = Observable.create(emitter -> {
            ConnectThread connectThread = new ConnectThread(arduinoBTModule, arduinoUUID, handler);
            connectThread.run();

            if (connectThread.getMmSocket().isConnected()) {
                // Pass the medical analyzer to ConnectedThread
                ConnectedThread connectedThread = new ConnectedThread(connectThread.getMmSocket(), medicalAnalyzer);
                connectedThread.run();
                if (connectedThread.getValueRead() != null) {
                    emitter.onNext(connectedThread.getValueRead());
                }
                connectedThread.cancel();
            }
            connectThread.cancel();
            emitter.onComplete();
        });

        connectToBTObservable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(valueRead -> {
                    // This will still work for backward compatibility
                    if (valueRead != null) {
                        medicalAnalyzer.processData(valueRead);
                    }
                });
    }


    // ... rest of your existing code remains unchanged
    private void setupListeners() {
        clearValues.setOnClickListener(view -> {
            btDevices.setText("");
            btReadings.setText("");
            medicalAnalyzer.reset();
        });

        connectToDevice.setOnClickListener(view -> {
            btReadings.setText("");
            if (arduinoBTModule != null) {
                connectToBluetoothDevice();
            }
        });

        seachDevices.setOnClickListener(view -> {
            searchForBluetoothDevices();
        });
    }

    @SuppressLint("MissingPermission")
    private void searchForBluetoothDevices() {
        BluetoothManager bluetoothManager = requireContext().getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            Log.d(TAG, "Device doesn't support Bluetooth");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_ENABLE_BT);
            } else {
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        } else {
            listPairedDevices(bluetoothAdapter);
        }
    }

    @SuppressLint("MissingPermission")
    private void listPairedDevices(BluetoothAdapter bluetoothAdapter) {
        String btDevicesString = "";
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress();
                btDevicesString = btDevicesString + deviceName + " || " + deviceHardwareAddress + "\n";

                if (deviceName.equals("HC-05")) {
                    arduinoUUID = device.getUuids()[0].getUuid();
                    arduinoBTModule = device;
                    connectToDevice.setEnabled(true);
                }
            }
            btDevices.setText(btDevicesString);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                searchForBluetoothDevices();
            }
        }
    }
}


// ... keep all your existing Bluetooth methods unchanged
