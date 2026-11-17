package com.example.runtrack.ui.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Backwards-compatible entry point. Registration now lives inside the tabbed
 * {@link LoginActivity} so both flows always share the same branding.
 */
public class RegisterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(LoginActivity.EXTRA_START_MODE, LoginActivity.MODE_SIGN_UP);
        startActivity(intent);
        finish();
    }
}
