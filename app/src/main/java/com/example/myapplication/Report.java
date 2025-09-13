package com.example.myapplication;

import java.util.Date;

public class Report {
    private String content;
    private Date timestamp;

    public Report() {
        // Required empty constructor for Firestore
    }

    public Report(String content, Date timestamp) {
        this.content = content;
        this.timestamp = timestamp;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
}