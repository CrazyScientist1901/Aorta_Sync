package com.example.myapplication;


import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

//Class that given an open BT Socket will
//Open, manage and close the data Stream from the Arduino BT device
public class ConnectedThread extends Thread {
    private static final String TAG = "FrugalLogs";
    private final BluetoothSocket mmSocket;
    private final InputStream mmInStream;
    private final MedicalDataAnalyzer dataAnalyzer;
    private String valueRead;

    public ConnectedThread(BluetoothSocket socket, MedicalDataAnalyzer analyzer) {
        this.mmSocket = socket;
        this.dataAnalyzer = analyzer;

        InputStream tmpIn = null;
        try {
            tmpIn = socket.getInputStream();
        } catch (IOException e) {
            Log.e(TAG, "Error occurred when creating input stream", e);
        }
        mmInStream = tmpIn;
    }

    public String getValueRead(){
        return valueRead;
    }

    public void run() {
        byte[] buffer = new byte[1024];
        int bytes = 0;
        int numberOfReadings = 0;

        while (numberOfReadings < 1) {
            try {
                buffer[bytes] = (byte) mmInStream.read();
                String readMessage;

                if (buffer[bytes] == '\n') {
                    readMessage = new String(buffer, 0, bytes);
                    Log.e(TAG, readMessage);
                    valueRead = readMessage;

                    // Send data to analyzer
                    if (dataAnalyzer != null && valueRead != null) {
                        dataAnalyzer.processData(valueRead);
                    }

                    bytes = 0;
                    numberOfReadings++;
                } else {
                    bytes++;
                }

            } catch (IOException e) {
                Log.d(TAG, "Input stream was disconnected", e);
                break;
            }
        }
    }
    public void cancel() {
        try {
            mmSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not close the connect socket", e);
        }
    }

}