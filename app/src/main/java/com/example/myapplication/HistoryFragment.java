package com.example.myapplication;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class HistoryFragment extends Fragment {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private RecyclerView reportsRecyclerView;
    private ReportAdapter adapter;
    private List<Report> reportList = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        reportsRecyclerView = view.findViewById(R.id.reportsRecyclerView);
        reportsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ReportAdapter(reportList);
        reportsRecyclerView.setAdapter(adapter);

        if (mAuth.getCurrentUser() != null) {
            fetchHistoricalReports();
        }

        return view;
    }

    private void fetchHistoricalReports() {
        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users").document(userId).collection("reports")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    reportList.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Report report = document.toObject(Report.class);
                        reportList.add(report);
                    }
                    adapter.notifyDataSetChanged();
                });
    }
}