package com.example.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

    private List<Report> reports;

    public ReportAdapter(List<Report> reports) {
        this.reports = reports;
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_report, parent, false);
        return new ReportViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        Report report = reports.get(position);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        // Set all the text views
        holder.filenameTextView.setText(String.format("ECG Report - %s", formatter.format(report.getTimestamp())));
        holder.timestampTextView.setText(formatter.format(report.getTimestamp()));
        holder.heartRateTextView.setText(String.format("Heart Rate: %d BPM", report.getHeartRate()));
        holder.durationTextView.setText(String.format("Duration: %d minutes", report.getRecordingDuration() / 60000));

        int dataPoints = report.getEcgValues() != null ? report.getEcgValues().split("\n").length : 0;
        holder.dataPointsTextView.setText(String.format("Data Points: %d", dataPoints));
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

    static class ReportViewHolder extends RecyclerView.ViewHolder {
        TextView filenameTextView, timestampTextView, heartRateTextView, durationTextView, dataPointsTextView;

        ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            filenameTextView = itemView.findViewById(R.id.filenameTextView);
            timestampTextView = itemView.findViewById(R.id.timestampTextView);
            heartRateTextView = itemView.findViewById(R.id.heartRateTextView);
            durationTextView = itemView.findViewById(R.id.durationTextView);
            dataPointsTextView = itemView.findViewById(R.id.dataPointsTextView);
        }
    }
}