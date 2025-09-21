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

        // Set the date
        holder.dateTextView.setText(formatter.format(report.getTimestamp()));

        // Set the content - show summary info instead of all ECG values
        String summary = String.format("Heart Rate: %d BPM | Duration: %d min | Data Points: %d",
                report.getHeartRate(),
                report.getRecordingDuration() / 60000, // Convert ms to minutes
                report.getEcgValues() != null ? report.getEcgValues().split("\n").length : 0);

        holder.contentTextView.setText(summary);
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

    static class ReportViewHolder extends RecyclerView.ViewHolder {
        TextView dateTextView, contentTextView;

        ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            dateTextView = itemView.findViewById(R.id.reportDateTextView);
            contentTextView = itemView.findViewById(R.id.reportContentTextView);
        }
    }
}