package com.example.myapplication;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

// ProfileFragment.java
public class ProfileFragment extends Fragment {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private EditText nameEditText, ageEditText, emergencyContactEditText;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        nameEditText = view.findViewById(R.id.nameEditText);
        ageEditText = view.findViewById(R.id.ageEditText);
        emergencyContactEditText = view.findViewById(R.id.emergencyContactEditText);

        loadUserProfile();

        view.findViewById(R.id.saveProfileButton).setOnClickListener(v -> saveUserProfile());

        return view;
    }

    private void loadUserProfile() {
        String userId = mAuth.getCurrentUser().getUid();
        DocumentReference profileRef = db.collection("users").document(userId).collection("profile").document("details");

        profileRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                nameEditText.setText(documentSnapshot.getString("name"));
                ageEditText.setText(String.valueOf(documentSnapshot.getLong("age")));
                emergencyContactEditText.setText(documentSnapshot.getString("emergencyContact"));
            }
        });
    }

    private void saveUserProfile() {
        String userId = mAuth.getCurrentUser().getUid();
        Map<String, Object> profile = new HashMap<>();
        profile.put("name", nameEditText.getText().toString());
        profile.put("age", Long.valueOf(ageEditText.getText().toString()));
        profile.put("emergencyContact", emergencyContactEditText.getText().toString());

        db.collection("users").document(userId).collection("profile").document("details")
                .set(profile)
                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Profile saved!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Error saving profile.", Toast.LENGTH_SHORT).show());
    }
}

