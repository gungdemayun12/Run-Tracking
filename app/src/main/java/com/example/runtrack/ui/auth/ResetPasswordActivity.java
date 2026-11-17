package com.example.runtrack.ui.auth;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.runtrack.R;
import com.example.runtrack.data.AppSettings;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;

public class ResetPasswordActivity extends AppCompatActivity {

    private View groupLoading;
    private View groupForm;
    private View groupSuccess;
    private View groupError;
    private View hero;
    private View sheet;

    private TextInputLayout tilPassword;
    private TextInputLayout tilConfirm;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirm;
    private TextView tvPasswordRules;
    private TextView tvErrorMessage;
    private MaterialButton btnConfirm;

    private FirebaseAuth auth;
    private String oobCode;
    private boolean submitAttempted;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);
        setupWindowChrome();

        auth = FirebaseAuth.getInstance();
        auth.setLanguageCode("id");

        bindViews();
        setupValidation();

        btnConfirm.setOnClickListener(v -> submitNewPassword());
        findViewById(R.id.btn_reset_back_to_login).setOnClickListener(v -> goToLogin());
        findViewById(R.id.btn_reset_request_new).setOnClickListener(v -> goToLogin());

        playIntro();

        Uri data = getIntent().getData();
        if (data != null) {
            oobCode = data.getQueryParameter("oobCode");
        }
        if (TextUtils.isEmpty(oobCode)) {
            showError("Tautan reset tidak lengkap. Minta tautan baru dari halaman masuk.");
            return;
        }
        verifyCode();
    }

    private void bindViews() {
        groupLoading = findViewById(R.id.reset_loading_group);
        groupForm = findViewById(R.id.reset_form_group);
        groupSuccess = findViewById(R.id.reset_success_group);
        groupError = findViewById(R.id.reset_error_group);
        hero = findViewById(R.id.reset_hero);
        sheet = findViewById(R.id.reset_sheet);

        tilPassword = findViewById(R.id.til_reset_password);
        tilConfirm = findViewById(R.id.til_reset_confirm);
        etPassword = findViewById(R.id.et_reset_password);
        etConfirm = findViewById(R.id.et_reset_confirm);
        tvPasswordRules = findViewById(R.id.tv_reset_password_rules);
        tvErrorMessage = findViewById(R.id.tv_reset_error_message);
        btnConfirm = findViewById(R.id.btn_reset_confirm);
    }

    private void setupWindowChrome() {
        Window window = getWindow();
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.auth_bg));
        window.setNavigationBarColor(ContextCompat.getColor(this, R.color.auth_sheet));
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        boolean darkMode = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        controller.setAppearanceLightStatusBars(!darkMode);
        controller.setAppearanceLightNavigationBars(!darkMode);
    }

    private void setupValidation() {
        etPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                String password = s == null ? "" : s.toString();
                refreshRules(password);
                if (submitAttempted) {
                    showFieldError(tilPassword, passwordError(password));
                }
                String confirm = raw(etConfirm);
                if (!confirm.isEmpty()) {
                    showFieldError(tilConfirm, confirmError(password, confirm));
                }
            }
        });
        etConfirm.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (submitAttempted) {
                    showFieldError(tilConfirm,
                            confirmError(raw(etPassword), s == null ? "" : s.toString()));
                }
            }
        });
    }

    private void verifyCode() {
        showLoading();
        auth.verifyPasswordResetCode(oobCode)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        showForm();
                    } else {
                        String message = friendlyResetError(task.getException());
                        showError(message);
                    }
                });
    }

    private void submitNewPassword() {
        submitAttempted = true;
        String password = raw(etPassword);
        String confirm = raw(etConfirm);
        String passwordErr = passwordError(password);
        String confirmErr = confirmError(password, confirm);
        showFieldError(tilPassword, passwordErr);
        showFieldError(tilConfirm, confirmErr);
        if (passwordErr != null || confirmErr != null) return;

        btnConfirm.setEnabled(false);
        showLoading();
        auth.confirmPasswordReset(oobCode, password)
                .addOnCompleteListener(task -> {
                    btnConfirm.setEnabled(true);
                    if (task.isSuccessful()) {
                        showSuccess();
                    } else {
                        showForm();
                        String message = friendlyResetError(task.getException());
                        tvErrorMessage.setText(message);
                        showErrorCard(message);
                    }
                });
    }

    private void showLoading() {
        groupLoading.setVisibility(View.VISIBLE);
        groupForm.setVisibility(View.GONE);
        groupSuccess.setVisibility(View.GONE);
        groupError.setVisibility(View.GONE);
    }

    private void showForm() {
        groupLoading.setVisibility(View.GONE);
        groupForm.setVisibility(View.VISIBLE);
        groupSuccess.setVisibility(View.GONE);
        groupError.setVisibility(View.GONE);
    }

    private void showSuccess() {
        groupLoading.setVisibility(View.GONE);
        groupForm.setVisibility(View.GONE);
        groupSuccess.setVisibility(View.VISIBLE);
        groupError.setVisibility(View.GONE);
    }

    private void showError(String message) {
        groupLoading.setVisibility(View.GONE);
        groupForm.setVisibility(View.GONE);
        groupSuccess.setVisibility(View.GONE);
        groupError.setVisibility(View.VISIBLE);
        if (tvErrorMessage != null && !TextUtils.isEmpty(message)) {
            tvErrorMessage.setText(message);
        }
    }

    private void showErrorCard(String message) {
        if (TextUtils.isEmpty(message)) return;
        tvErrorMessage.setText(message);
    }

    private void refreshRules(String value) {
        boolean length = value.length() >= 8;
        boolean upper = value.matches(".*[A-Z].*");
        boolean lower = value.matches(".*[a-z].*");
        boolean digit = value.matches(".*[0-9].*");
        boolean noSpaces = !value.matches(".*\\s.*");
        boolean ok = length && upper && lower && digit && noSpaces;
        int color = ok ? R.color.auth_validation_success
                : (value.isEmpty() ? R.color.auth_text_hint : R.color.auth_validation_warning);
        tvPasswordRules.setTextColor(getColor(color));
    }

    private String passwordError(String value) {
        if (TextUtils.isEmpty(value)) return "Kata sandi wajib diisi";
        if (value.length() < 8) return "Minimal 8 karakter";
        if (value.length() > 64) return "Maksimal 64 karakter";
        if (!value.matches(".*[A-Z].*")) return "Wajib ada huruf besar";
        if (!value.matches(".*[a-z].*")) return "Wajib ada huruf kecil";
        if (!value.matches(".*[0-9].*")) return "Wajib ada angka";
        if (value.matches(".*\\s.*")) return "Jangan gunakan spasi";
        return null;
    }

    private String confirmError(String password, String confirmation) {
        if (TextUtils.isEmpty(confirmation)) return "Ulangi kata sandi";
        return password.equals(confirmation) ? null : "Kata sandi belum sama";
    }

    private void showFieldError(TextInputLayout layout, String message) {
        if (layout == null) return;
        layout.setError(message);
        layout.setErrorEnabled(message != null);
    }

    private String friendlyResetError(Exception exception) {
        String code = "";
        if (exception instanceof FirebaseAuthException) {
            code = ((FirebaseAuthException) exception).getErrorCode();
        }
        switch (code) {
            case "ERROR_EXPIRED_ACTION_CODE":
                return "Tautan reset sudah kedaluwarsa. Minta tautan baru dari halaman masuk.";
            case "ERROR_INVALID_ACTION_CODE":
                return "Tautan reset tidak valid. Minta tautan baru dari halaman masuk.";
            case "ERROR_WEAK_PASSWORD":
                return "Kata sandi terlalu lemah. Gunakan minimal 8 karakter berisi huruf besar, huruf kecil, dan angka.";
            case "ERROR_USER_DISABLED":
                return "Akun ini dinonaktifkan. Hubungi dukungan RunTrack bila perlu bantuan.";
            case "ERROR_NETWORK_REQUEST_FAILED":
                return "Tidak ada koneksi internet. Periksa jaringanmu, lalu coba lagi.";
            case "ERROR_TOO_MANY_REQUESTS":
                return "Terlalu banyak percobaan. Tunggu beberapa menit, lalu coba lagi.";
            default:
                return "Tautan reset tidak bisa digunakan. Minta tautan baru dari halaman masuk.";
        }
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    private void playIntro() {
        if (!AppSettings.isAnimationsEnabled(this)) return;
        if (hero != null) {
            hero.setAlpha(0f);
            hero.setTranslationY(18f);
            hero.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(480)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
        if (sheet != null) {
            sheet.setAlpha(0f);
            sheet.setTranslationY(48f);
            sheet.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(90)
                    .setDuration(520)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private String raw(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString() : "";
    }
}
