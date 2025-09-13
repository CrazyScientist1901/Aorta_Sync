package com.example.myapplication;

import static android.content.ContentValues.TAG;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

// DataFragment.java
public class DataFragment extends Fragment {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextView heartbeatTextView, ecgTextView, sp02TextView, reportTextView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_data, container, false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        heartbeatTextView = view.findViewById(R.id.heartbeatTextView);
        ecgTextView = view.findViewById(R.id.ecgTextView);
        sp02TextView = view.findViewById(R.id.sp02TextView);
        reportTextView = view.findViewById(R.id.reportTextView);

        if (mAuth.getCurrentUser() != null) {
            listenForDataUpdates();
        }

        return view;
    }

    private void listenForDataUpdates() {
        String userId = mAuth.getCurrentUser().getUid();
        DocumentReference dataRef = db.collection("users").document(userId).collection("data").document("current");

        dataRef.addSnapshotListener((snapshot, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                heartbeatTextView.setText("Heartbeat: " + snapshot.getLong("heartbeat") + " bpm");
                ecgTextView.setText("ECG Signal: " + snapshot.getString("ecg"));
                sp02TextView.setText("SpO2: " + snapshot.getLong("sp02") + " %");
                reportTextView.setText(snapshot.getString("ml_report"));
            } else {
                Log.d(TAG, "Current data: null");
            }
        });
    }
}
