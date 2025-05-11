package com.example.travelguide2;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class EditProfileActivity extends AppCompatActivity {

    EditText editName, editEmail, editUsername, editPassword;
    Button saveButton;
    String nameUser, emailUser, usernameUser, passwordUser;
    DatabaseReference reference;
    FirebaseAuth auth;
    FirebaseUser firebaseUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        auth = FirebaseAuth.getInstance();
        firebaseUser = auth.getCurrentUser();

        if (firebaseUser == null) {
            finish();
            return;
        }

        reference = FirebaseDatabase.getInstance().getReference("users");

        editName = findViewById(R.id.editName);
        editEmail = findViewById(R.id.editEmail);
        editUsername = findViewById(R.id.editUsername);
        editPassword = findViewById(R.id.editPassword);
        saveButton = findViewById(R.id.saveButton);

        showData();

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateProfile();
            }
        });
    }

    private void updateProfile() {
        boolean isUpdated = false;

        String newName = editName.getText().toString().trim();
        if (!newName.equals(nameUser) && !newName.isEmpty()) {
            updateField("name", newName);
            nameUser = newName;
            isUpdated = true;
        }

        String newEmail = editEmail.getText().toString().trim();
        if (!newEmail.equals(emailUser) && !newEmail.isEmpty()) {
            updateField("email", newEmail);
            emailUser = newEmail;
            isUpdated = true;
        }

        String newUsername = editUsername.getText().toString().trim();
        if (!newUsername.equals(usernameUser) && !newUsername.isEmpty()) {
            updateField("username", newUsername);
            usernameUser = newUsername;
            isUpdated = true;
        }

        String newPassword = editPassword.getText().toString().trim();
        if (!newPassword.equals(passwordUser) && !newPassword.isEmpty()) {
            updateField("password", newPassword);
            passwordUser = newPassword;
            isUpdated = true;
        }

        if (isUpdated) {
            Toast.makeText(EditProfileActivity.this, "Profile Updated", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(EditProfileActivity.this, "No changes detected", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateField(String field, String value) {
        reference.child(firebaseUser.getUid()).child(field).setValue(value);
    }

    public void showData() {
        Intent intent = getIntent();

        nameUser = intent.getStringExtra("name");
        emailUser = intent.getStringExtra("email");
        usernameUser = intent.getStringExtra("username");
        passwordUser = intent.getStringExtra("password");

        editName.setText(nameUser);
        editEmail.setText(emailUser);
        editUsername.setText(usernameUser);
        editPassword.setText(passwordUser);
    }
}
