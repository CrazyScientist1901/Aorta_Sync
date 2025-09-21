package com.example.myapplication;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryFragment extends Fragment {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private RecyclerView reportsRecyclerView;
    private ReportAdapter adapter; // DECLARE THE ADAPTER VARIABLE
    private List<Report> reportList = new ArrayList<>();
    private ProgressBar progressBar;
    private TextView emptyStateText;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize UI components
        reportsRecyclerView = view.findViewById(R.id.reportsRecyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        emptyStateText = view.findViewById(R.id.emptyStateText);

        // Setup RecyclerView
        reportsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ReportAdapter(reportList); // INITIALIZE THE ADAPTER
        reportsRecyclerView.setAdapter(adapter);

        // Show loading state
        showLoading(true);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mAuth.getCurrentUser() != null) {
            fetchHistoricalReports();
        } else {
            showLoading(false);
            emptyStateText.setText("Please sign in to view your reports");
            emptyStateText.setVisibility(View.VISIBLE);
        }
    }

    private void fetchHistoricalReports() {
        String userId = mAuth.getCurrentUser().getUid();

        // Query only by userId (shouldn't require composite index)
        db.collection("ecg_reports")
                .whereEqualTo("userId", userId)
                .get()
                .addOnCompleteListener(task -> {
                    showLoading(false);

                    if (task.isSuccessful() && task.getResult() != null) {
                        reportList.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Report report = document.toObject(Report.class);
                            reportList.add(report);
                        }

                        // Sort by timestamp locally
                        Collections.sort(reportList, (r1, r2) ->
                                r2.getTimestamp().compareTo(r1.getTimestamp()));

                        adapter.notifyDataSetChanged();

                        if (reportList.isEmpty()) {
                            emptyStateText.setText("No ECG reports found");
                            emptyStateText.setVisibility(View.VISIBLE);
                        } else {
                            emptyStateText.setVisibility(View.GONE);
                        }
                    } else {
                        emptyStateText.setText("Error loading reports: " +
                                (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                        emptyStateText.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            reportsRecyclerView.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.GONE);
        } else {
            progressBar.setVisibility(View.GONE);
            reportsRecyclerView.setVisibility(View.VISIBLE);
        }
    }
}