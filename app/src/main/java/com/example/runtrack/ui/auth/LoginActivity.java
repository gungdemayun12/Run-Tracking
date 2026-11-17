package com.example.runtrack.ui.auth;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.runtrack.R;
import com.example.runtrack.data.AppSettings;
import com.example.runtrack.data.UserRepository;
import com.example.runtrack.model.UserProfile;
import com.example.runtrack.ui.main.MainNavigationActivity;
import com.example.runtrack.ui.widget.RunnerLoaderView;
import com.example.runtrack.util.SessionManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.ActionCodeSettings;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LoginActivity extends AppCompatActivity {

    public static final String EXTRA_START_MODE = "extra_start_mode";
    public static final String MODE_SIGN_UP = "sign_up";

    private View groupSignIn;
    private View groupSignUp;
    private View groupForgot;
    private View groupResetForm;
    private View groupResetSent;
    private View groupContinue;
    private View authTabs;
    private TextView tvTabSignIn;
    private TextView tvTabSignUp;
    private TextView tvForgot;
    private TextView tvRegisterLink;
    private TextView tvWelcome;
    private TextView tvSubtitle;
    private View authHero;
    private View authSheet;
    private ViewGroup formRoot;

    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private TextInputEditText etSignupName;
    private TextInputEditText etSignupEmail;
    private TextInputEditText etSignupPassword;
    private TextInputEditText etSignupPasswordConfirm;
    private TextInputEditText etSignupWeight;
    private TextInputEditText etSignupAge;
    private TextInputEditText etSignupHeight;
    private TextInputEditText etResetEmail;

    private TextInputLayout tilEmail;
    private TextInputLayout tilPassword;
    private TextInputLayout tilSignupName;
    private TextInputLayout tilSignupEmail;
    private TextInputLayout tilSignupPassword;
    private TextInputLayout tilSignupPasswordConfirm;
    private TextInputLayout tilSignupWeight;
    private TextInputLayout tilSignupAge;
    private TextInputLayout tilSignupHeight;
    private TextInputLayout tilResetEmail;
    private TextView tvPasswordRules;
    private TextView tvResetSentEmail;
    private View authErrorCard;
    private TextView tvAuthError;
    private View authSuccessCard;
    private TextView tvAuthSuccess;

    private MaterialButton btnSignIn;
    private MaterialButton btnSignUp;
    private MaterialButton btnSendReset;
    private MaterialButton btnBackToLogin;
    private MaterialButton btnResendReset;
    private MaterialButton btnChangeResetEmail;
    private MaterialButton btnGoogleSignIn;
    private MaterialCheckBox cbKeepSignedIn;

    private RunnerLoaderView runnerLoader;

    private FirebaseAuth auth;
    private UserRepository userRepository;
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private final List<ObjectAnimator> loopAnimators = new ArrayList<>();

    private boolean signUpMode;
    private boolean forgotMode;
    private boolean submitAttempted;
    private final Set<Integer> touchedFields = new HashSet<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        setupWindowChrome();

        auth = FirebaseAuth.getInstance();
        auth.setLanguageCode("id");
        userRepository = new UserRepository(this);
        bindViews();

        signUpMode = MODE_SIGN_UP.equals(getIntent().getStringExtra(EXTRA_START_MODE));
        cbKeepSignedIn.setChecked(true);
        cbKeepSignedIn.setClickable(false);
        cbKeepSignedIn.setFocusable(false);

        tvTabSignIn.setOnClickListener(v -> setMode(false));
        tvTabSignUp.setOnClickListener(v -> setMode(true));
        tvRegisterLink.setOnClickListener(v -> setMode(!signUpMode));
        tvForgot.setOnClickListener(v -> openForgot());
        btnSignIn.setOnClickListener(v -> submitSignIn());
        btnSignUp.setOnClickListener(v -> submitSignUp());
        btnSendReset.setOnClickListener(v -> resetPassword());
        btnResendReset.setOnClickListener(v -> resetPassword());
        btnChangeResetEmail.setOnClickListener(v -> showResetForm());
        btnBackToLogin.setOnClickListener(v -> closeForgot());
        btnGoogleSignIn.setOnClickListener(v -> handleGoogleSignIn());

        setupRealtimeValidation();
        applyMode(false);
        setupGoogleSignIn();
        playIntro();
    }

    private void bindViews() {
        groupSignIn = findViewById(R.id.group_sign_in);
        groupSignUp = findViewById(R.id.group_sign_up);
        groupForgot = findViewById(R.id.group_forgot);
        groupResetForm = findViewById(R.id.group_reset_form);
        groupResetSent = findViewById(R.id.group_reset_sent);
        groupContinue = findViewById(R.id.group_continue);
        authTabs = findViewById(R.id.auth_tabs);
        tvTabSignIn = findViewById(R.id.tv_tab_sign_in);
        tvTabSignUp = findViewById(R.id.tv_tab_sign_up);
        tvForgot = findViewById(R.id.tv_forgot_password);
        tvRegisterLink = findViewById(R.id.tv_register_link);
        tvWelcome = findViewById(R.id.tv_welcome);
        tvSubtitle = findViewById(R.id.tv_subtitle);
        authHero = findViewById(R.id.auth_hero);
        authSheet = findViewById(R.id.auth_sheet);
        formRoot = findViewById(R.id.auth_form_root);

        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etSignupName = findViewById(R.id.et_signup_name);
        etSignupEmail = findViewById(R.id.et_signup_email);
        etSignupPassword = findViewById(R.id.et_signup_password);
        etSignupPasswordConfirm = findViewById(R.id.et_signup_password_confirm);
        etSignupWeight = findViewById(R.id.et_signup_weight);
        etSignupAge = findViewById(R.id.et_signup_age);
        etSignupHeight = findViewById(R.id.et_signup_height);
        etResetEmail = findViewById(R.id.et_reset_email);

        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        tilSignupName = findViewById(R.id.til_signup_name);
        tilSignupEmail = findViewById(R.id.til_signup_email);
        tilSignupPassword = findViewById(R.id.til_signup_password);
        tilSignupPasswordConfirm = findViewById(R.id.til_signup_password_confirm);
        tilSignupWeight = findViewById(R.id.til_signup_weight);
        tilSignupAge = findViewById(R.id.til_signup_age);
        tilSignupHeight = findViewById(R.id.til_signup_height);
        tilResetEmail = findViewById(R.id.til_reset_email);
        tvPasswordRules = findViewById(R.id.tv_password_rules);

        btnSignIn = findViewById(R.id.btn_sign_in);
        btnSignUp = findViewById(R.id.btn_sign_up);
        btnSendReset = findViewById(R.id.btn_send_reset);
        btnResendReset = findViewById(R.id.btn_resend_reset);
        btnChangeResetEmail = findViewById(R.id.btn_change_reset_email);
        tvResetSentEmail = findViewById(R.id.tv_reset_sent_email);
        btnGoogleSignIn = findViewById(R.id.btn_google_sign_in);
        btnBackToLogin = findViewById(R.id.btn_back_to_login);
        cbKeepSignedIn = findViewById(R.id.cb_keep_signed_in);

        authErrorCard = findViewById(R.id.auth_error_card);
        tvAuthError = findViewById(R.id.tv_auth_error);
        View closeError = findViewById(R.id.btn_auth_error_close);
        if (closeError != null) closeError.setOnClickListener(v -> hideAuthError());

        authSuccessCard = findViewById(R.id.auth_success_card);
        tvAuthSuccess = findViewById(R.id.tv_auth_success);
        View closeSuccess = findViewById(R.id.btn_auth_success_close);
        if (closeSuccess != null) closeSuccess.setOnClickListener(v -> hideAuthSuccess());

        runnerLoader = findViewById(R.id.runner_loader);
    }

    private void setupWindowChrome() {
        Window window = getWindow();
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.auth_bg));
        window.setNavigationBarColor(ContextCompat.getColor(this, R.color.auth_sheet));
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        boolean darkMode = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        controller.setAppearanceLightStatusBars(!darkMode);
        controller.setAppearanceLightNavigationBars(!darkMode);
    }

    private void setMode(boolean signUp) {
        if (!forgotMode && signUpMode == signUp) return;
        forgotMode = false;
        signUpMode = signUp;
        submitAttempted = false;
        hideAuthError();
        hideAuthSuccess();
        applyMode(true);
    }

    private void openForgot() {
        if (forgotMode) return;
        String currentEmail = textOf(etEmail);
        if (!TextUtils.isEmpty(currentEmail) && TextUtils.isEmpty(textOf(etResetEmail))) {
            etResetEmail.setText(currentEmail);
        }
        showResetForm();
        forgotMode = true;
        submitAttempted = false;
        hideAuthError();
        hideAuthSuccess();
        applyMode(true);
    }

    private void closeForgot() {
        if (!forgotMode) return;
        forgotMode = false;
        submitAttempted = false;
        showResetForm();
        hideAuthError();
        applyMode(true);
    }

    private void showResetForm() {
        if (groupResetForm != null) groupResetForm.setVisibility(View.VISIBLE);
        if (groupResetSent != null) groupResetSent.setVisibility(View.GONE);
    }

    private void showResetSent(String email) {
        if (groupResetForm != null) groupResetForm.setVisibility(View.GONE);
        if (groupResetSent != null) groupResetSent.setVisibility(View.VISIBLE);
        if (tvResetSentEmail != null) {
            tvResetSentEmail.setText(email);
        }
    }

    private void applyMode(boolean animate) {
        if (animate && formRoot != null && AppSettings.isAnimationsEnabled(this)) {
            TransitionSet transition = new TransitionSet();
            transition.setOrdering(TransitionSet.ORDERING_TOGETHER);
            Fade fade = new Fade();
            fade.setDuration(240);
            ChangeBounds bounds = new ChangeBounds();
            bounds.setDuration(380);
            transition.addTransition(fade);
            transition.addTransition(bounds);
            transition.setInterpolator(new DecelerateInterpolator());
            TransitionManager.beginDelayedTransition(formRoot, transition);
            if (tvWelcome != null) {
                tvWelcome.animate().alpha(0f).setDuration(140).withEndAction(() -> {
                    bindCopy();
                    tvWelcome.animate().alpha(1f).setDuration(260).start();
                    if (tvSubtitle != null) {
                        tvSubtitle.setAlpha(0f);
                        tvSubtitle.animate().alpha(1f).setDuration(260).start();
                    }
                }).start();
            } else {
                bindCopy();
            }
        } else {
            bindCopy();
        }

        int signInVis = !signUpMode && !forgotMode ? View.VISIBLE : View.GONE;
        int signUpVis = signUpMode && !forgotMode ? View.VISIBLE : View.GONE;
        int forgotVis = forgotMode ? View.VISIBLE : View.GONE;
        int sharedVis = forgotMode ? View.GONE : View.VISIBLE;
        groupSignIn.setVisibility(signInVis);
        groupSignUp.setVisibility(signUpVis);
        groupForgot.setVisibility(forgotVis);
        if (authTabs != null) authTabs.setVisibility(sharedVis);
        if (groupContinue != null) groupContinue.setVisibility(sharedVis);
        btnGoogleSignIn.setVisibility(sharedVis);
        tvRegisterLink.setVisibility(sharedVis);

        boolean signInTab = !signUpMode && !forgotMode;
        tvTabSignIn.setBackgroundResource(signInTab ? R.drawable.bg_auth_tab_active : 0);
        tvTabSignIn.setTextColor(getColor(signInTab
                ? R.color.auth_tab_active_text : R.color.auth_text_secondary));
        tvTabSignUp.setBackgroundResource(signUpMode && !forgotMode
                ? R.drawable.bg_auth_tab_active : 0);
        tvTabSignUp.setTextColor(getColor(signUpMode && !forgotMode
                ? R.color.auth_tab_active_text : R.color.auth_text_secondary));
    }

    private void bindCopy() {
        if (forgotMode) {
            if (tvWelcome != null) tvWelcome.setText("Pulihkan akun.");
            if (tvSubtitle != null) {
                tvSubtitle.setText("Tautan reset dikirim ke email Anda. Tidak perlu membuat akun baru.");
            }
            return;
        }
        if (signUpMode) {
            if (tvWelcome != null) tvWelcome.setText("Mulai berlari.");
            if (tvSubtitle != null) {
                tvSubtitle.setText("Buat akun dan simpan setiap rute, jarak, dan waktu.");
            }
            setFooter("Sudah punya akun?  ", "Masuk");
        } else {
            if (tvWelcome != null) tvWelcome.setText("Selamat datang.");
            if (tvSubtitle != null) {
                tvSubtitle.setText("Lanjutkan latihan Anda. Riwayat lari sudah menunggu.");
            }
            setFooter("Baru di RunTrack?  ", "Buat akun");
        }
    }

    private void setFooter(String prefix, String action) {
        String full = prefix + action;
        SpannableString text = new SpannableString(full);
        int split = prefix.length();
        text.setSpan(new ForegroundColorSpan(getColor(R.color.auth_text_secondary)),
                0, split, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(getColor(R.color.brand)),
                split, full.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new StyleSpan(Typeface.BOLD),
                split, full.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvRegisterLink.setText(text);
    }

    private void setupGoogleSignIn() {
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    try {
                        GoogleSignInAccount account = GoogleSignIn
                                .getSignedInAccountFromIntent(result.getData())
                                .getResult(ApiException.class);
                        if (account != null) {
                            firebaseAuthWithGoogle(account.getIdToken(),
                                    account.getDisplayName(), account.getEmail());
                        }
                    } catch (ApiException e) {
                        setLoading(false);
                        int status = e.getStatusCode();
                        if (status == 12501) {
                            // Pengguna menutup dialog Google: tidak perlu pesan error.
                            hideAuthError();
                        } else if (status == 10 || status == 12500) {
                            showAuthError("Masuk dengan Google belum bisa dipakai di perangkat ini. "
                                    + "Gunakan email dan kata sandi, atau hubungi admin RunTrack.");
                        } else {
                            showAuthError("Masuk dengan Google gagal. Periksa koneksi internet, lalu coba lagi.");
                        }
                    }
                });
    }

    private void playIntro() {
        if (runnerLoader != null) runnerLoader.start();
        if (!AppSettings.isAnimationsEnabled(this)) return;
        if (authHero != null) {
            authHero.setAlpha(0f);
            authHero.setTranslationY(18f);
            authHero.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(480)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
        if (authSheet != null) {
            authSheet.setAlpha(0f);
            authSheet.setTranslationY(48f);
            authSheet.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(90)
                    .setDuration(520)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    @Override
    protected void onDestroy() {
        for (ObjectAnimator animator : loopAnimators) {
            animator.cancel();
        }
        loopAnimators.clear();
        super.onDestroy();
    }

    private void submitSignIn() {
        submitAttempted = true;
        String emailError = emailError(textOf(etEmail), true);
        String passwordError = loginPasswordError(rawTextOf(etPassword));
        showFieldError(tilEmail, emailError);
        showFieldError(tilPassword, passwordError);
        if (emailError != null || passwordError != null) {
            return;
        }

        String email = textOf(etEmail);
        String password = rawTextOf(etPassword);
        Log.d("LoginActivity", "sign-in attempt emailLen=" + email.length()
                + " passwordLen=" + password.length());
        hideAuthError();
        hideAuthSuccess();
        setLoading(true);
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        AppSettings.setKeepSignedIn(this, true);
                        SessionManager.onLogin(this);
                        openMain();
                    } else {
                        handleLoginFailure(task.getException());
                    }
                });
    }

    private void submitSignUp() {
        submitAttempted = true;
        String name = textOf(etSignupName);
        String email = textOf(etSignupEmail);
        String password = rawTextOf(etSignupPassword);
        String passwordConfirm = rawTextOf(etSignupPasswordConfirm);
        String weightStr = textOf(etSignupWeight);
        String ageStr = textOf(etSignupAge);
        String heightStr = textOf(etSignupHeight);
        String nameError = nameError(name);
        String emailError = emailError(email, true);
        String passwordError = signupPasswordError(password);
        String passwordConfirmError = signupPasswordConfirmError(password, passwordConfirm);
        String weightError = weightError(weightStr);
        String ageError = ageError(ageStr);
        String heightError = heightError(heightStr);
        showFieldError(tilSignupName, nameError);
        showFieldError(tilSignupEmail, emailError);
        showFieldError(tilSignupPassword, passwordError);
        showFieldError(tilSignupPasswordConfirm, passwordConfirmError);
        showFieldError(tilSignupWeight, weightError);
        showFieldError(tilSignupAge, ageError);
        showFieldError(tilSignupHeight, heightError);
        refreshPasswordRules(password, true);
        if (nameError != null || emailError != null || passwordError != null
                || passwordConfirmError != null || weightError != null
                || ageError != null || heightError != null) {
            return;
        }

        setLoading(true);
        hideAuthError();
        hideAuthSuccess();
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        setLoading(false);
                        handleSignupFailure(task.getException());
                        return;
                    }
                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        setLoading(false);
                        handleSignupFailure(task.getException());
                        return;
                    }
                    user.updateProfile(new UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build())
                            .addOnCompleteListener(update -> {
                                UserProfile profile = new UserProfile();
                                profile.uid = user.getUid();
                                profile.displayName = name;
                                profile.email = email;
                                profile.age = (int) parseDouble(ageStr, 0);
                                profile.heightCm = parseDouble(heightStr, 0);
                                double weight = parseDouble(weightStr, 0);
                                if (weight > 0) profile.weightKg = weight;

                                final boolean[] handled = {false};
                                final Runnable complete = () -> {
                                    if (handled[0]) return;
                                    handled[0] = true;
                                    runOnUiThread(() -> finishSignupAndGoToLogin(email));
                                };
                                // Persist the profile while the new account is still
                                // authenticated, then return to the login screen.
                                userRepository.saveUser(profile, complete);
                                new android.os.Handler(getMainLooper())
                                        .postDelayed(complete, 6000L);
                            });
                });
    }

    /**
     * Registration never auto-signs-in: the user is sent back to the Masuk tab
     * with the email pre-filled and a success notice. This also makes the
     * account/profile write happen while the auth session is still active.
     */
    private void finishSignupAndGoToLogin(String email) {
        if (isFinishing() || isDestroyed()) return;
        setLoading(false);
        try {
            auth.signOut();
        } catch (Exception ignored) {
        }
        SessionManager.clear(this);
        AppSettings.setKeepSignedIn(this, false);

        etSignupPassword.setText("");
        etSignupPasswordConfirm.setText("");
        etPassword.setText("");
        hideAuthError();
        // Keep the current sign-up mode so applyMode animates back to Masuk.
        setMode(false);
        etEmail.setText(email);
        if (email != null) etEmail.setSelection(email.length());
        showAuthSuccess("Akun berhasil dibuat. Silakan masuk dengan email dan kata sandi Anda.");
    }

    private void handleGoogleSignIn() {
        String clientId = getString(R.string.default_web_client_id);
        boolean configured = clientId != null
                && !clientId.isEmpty()
                && !clientId.startsWith("YOUR_")
                && clientId.length() > 20;

        if (!configured) {
            showGoogleSetupHelp();
            return;
        }

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(clientId)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        Intent signInIntent = googleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }

    private void firebaseAuthWithGoogle(String idToken, String name, String email) {
        setLoading(true);
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            AppSettings.setKeepSignedIn(this, true);
                            SessionManager.onLogin(this);
                            UserProfile profile = new UserProfile();
                            profile.uid = user.getUid();
                            profile.displayName = name != null ? name
                                    : (user.getDisplayName() != null ? user.getDisplayName() : "Runner");
                            profile.email = email != null ? email
                                    : (user.getEmail() != null ? user.getEmail() : "");
                            userRepository.saveUser(profile);
                            userRepository.ensureProfile(user);
                            openMain();
                        }
                    } else {
                        showAuthError(friendlyAuthError(task.getException()));
                    }
                });
    }

    private void resetPassword() {
        submitAttempted = true;
        String email = textOf(etResetEmail);
        String emailError = emailError(email, true);
        showFieldError(tilResetEmail, emailError);
        if (emailError != null) return;

        btnSendReset.setEnabled(false);
        hideAuthError();
        ActionCodeSettings settings = ActionCodeSettings.newBuilder()
                .setUrl("https://running-track-b0911.firebaseapp.com/__/auth/action?mode=resetPassword")
                .setHandleCodeInApp(true)
                .setAndroidPackageName("com.example.runtrack", true, null)
                .build();
        auth.sendPasswordResetEmail(email, settings).addOnCompleteListener(task -> {
            btnSendReset.setEnabled(true);
            if (task.isSuccessful()) {
                showResetSent(email);
            } else {
                handleResetFailure(task.getException());
            }
        });
    }

    private void setupRealtimeValidation() {
        watch(etEmail, tilEmail, value -> emailError(value, touchedFields.contains(etEmail.getId())));
        watch(etPassword, tilPassword, this::loginPasswordError);
        watch(etSignupName, tilSignupName, this::nameError);
        watch(etSignupEmail, tilSignupEmail, value -> emailError(value, touchedFields.contains(etSignupEmail.getId())));
        watch(etSignupPassword, tilSignupPassword, this::signupPasswordError);
        watch(etSignupPasswordConfirm, tilSignupPasswordConfirm,
                value -> signupPasswordConfirmError(rawTextOf(etSignupPassword), value));
        watch(etSignupWeight, tilSignupWeight, this::weightError);
        watch(etSignupAge, tilSignupAge, this::ageError);
        watch(etSignupHeight, tilSignupHeight, this::heightError);
        watch(etResetEmail, tilResetEmail, value -> emailError(value, touchedFields.contains(etResetEmail.getId())));
        etSignupPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                hideAuthError();
                String password = s == null ? "" : s.toString();
                refreshPasswordRules(password, false);
                String confirmation = rawTextOf(etSignupPasswordConfirm);
                if (!confirmation.isEmpty()
                        || touchedFields.contains(etSignupPasswordConfirm.getId())) {
                    showFieldError(tilSignupPasswordConfirm,
                            signupPasswordConfirmError(password, confirmation));
                }
            }
        });
    }

    private void watch(TextInputEditText field, TextInputLayout layout, FieldCheck check) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (field.hasFocus() || submitAttempted || touchedFields.contains(field.getId())) {
                    touchedFields.add(field.getId());
                    showFieldError(layout, check.message(s == null ? "" : s.toString()));
                }
                hideAuthSuccess();
            }
        });
        field.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                touchedFields.add(field.getId());
                showFieldError(layout, check.message(validationTextOf(field)));
            }
        });
    }

    private void showFieldError(TextInputLayout layout, String message) {
        if (layout == null) return;
        layout.setError(message);
        layout.setErrorEnabled(message != null);
    }

    private String emailError(String value, boolean requiredNow) {
        value = value == null ? "" : value.trim();
        if (TextUtils.isEmpty(value)) {
            return requiredNow || submitAttempted ? "Email wajib diisi" : null;
        }
        if (value.length() > 80 || value.contains(" ")
                || !Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
            return "Gunakan email yang valid, contoh nama@email.com";
        }
        int at = value.indexOf('@');
        String domain = at >= 0 ? value.substring(at + 1) : "";
        if (!domain.contains(".") || domain.startsWith(".") || domain.endsWith(".")) {
            return "Domain email belum lengkap";
        }
        return null;
    }

    private String loginPasswordError(String value) {
        if (TextUtils.isEmpty(value)) {
            return submitAttempted || touchedFields.contains(etPassword.getId())
                    ? "Kata sandi wajib diisi" : null;
        }
        if (value.length() < 6) return "Kata sandi minimal 6 karakter";
        if (value.length() > 64) return "Kata sandi maksimal 64 karakter";
        return null;
    }

    private String nameError(String value) {
        value = value == null ? "" : value.trim();
        if (TextUtils.isEmpty(value)) {
            return submitAttempted || touchedFields.contains(etSignupName.getId())
                    ? "Nama lengkap wajib diisi" : null;
        }
        if (value.length() < 2 || value.length() > 40) {
            return "Nama harus 2–40 karakter";
        }
        if (!value.matches("^[\\p{L}][\\p{L} .'-]*$")) {
            return "Nama hanya boleh huruf, spasi, titik, atau tanda hubung";
        }
        return null;
    }

    private String signupPasswordError(String value) {
        if (TextUtils.isEmpty(value)) {
            return submitAttempted || touchedFields.contains(etSignupPassword.getId())
                    ? "Kata sandi wajib diisi" : null;
        }
        if (value.length() < 8) return "Minimal 8 karakter";
        if (value.length() > 64) return "Maksimal 64 karakter";
        if (!value.matches(".*[A-Z].*")) return "Wajib ada huruf besar";
        if (!value.matches(".*[a-z].*")) return "Wajib ada huruf kecil";
        if (!value.matches(".*[0-9].*")) return "Wajib ada angka";
        if (value.matches(".*\\s.*")) return "Jangan gunakan spasi";
        return null;
    }

    private String signupPasswordConfirmError(String password, String confirmation) {
        if (TextUtils.isEmpty(confirmation)) {
            return submitAttempted || touchedFields.contains(etSignupPasswordConfirm.getId())
                    ? "Ulangi kata sandi" : null;
        }
        return password.equals(confirmation) ? null : "Kata sandi belum sama";
    }

    private String weightError(String value) {
        value = value == null ? "" : value.trim();
        if (TextUtils.isEmpty(value)) return null;
        try {
            double weight = Double.parseDouble(value.replace(',', '.'));
            if (!Double.isFinite(weight) || weight < 25 || weight > 300) {
                return "Berat harus antara 25 dan 300 kg";
            }
        } catch (NumberFormatException e) {
            return "Masukkan angka berat yang valid";
        }
        return null;
    }

    private String ageError(String value) {
        value = value == null ? "" : value.trim();
        if (TextUtils.isEmpty(value)) return null;
        try {
            int age = Integer.parseInt(value);
            if (age < 10 || age > 100) {
                return "Umur harus antara 10 dan 100 tahun";
            }
        } catch (NumberFormatException e) {
            return "Masukkan umur berupa angka";
        }
        return null;
    }

    private String heightError(String value) {
        value = value == null ? "" : value.trim();
        if (TextUtils.isEmpty(value)) return null;
        try {
            double height = Double.parseDouble(value.replace(',', '.'));
            if (!Double.isFinite(height) || height < 100 || height > 250) {
                return "Tinggi harus antara 100 dan 250 cm";
            }
        } catch (NumberFormatException e) {
            return "Masukkan tinggi berupa angka";
        }
        return null;
    }

    private void refreshPasswordRules(String value, boolean emphasize) {
        if (tvPasswordRules == null) return;
        boolean length = value.length() >= 8;
        boolean upper = value.matches(".*[A-Z].*");
        boolean lower = value.matches(".*[a-z].*");
        boolean digit = value.matches(".*[0-9].*");
        boolean noSpaces = !value.matches(".*\\s.*");
        boolean ok = length && upper && lower && digit && noSpaces;
        tvPasswordRules.setText("Minimal 8 karakter  ·  huruf besar  ·  huruf kecil  ·  angka  ·  tanpa spasi");
        int color = ok ? R.color.auth_validation_success
                : (value.isEmpty() && !emphasize
                ? R.color.auth_text_hint : R.color.auth_validation_warning);
        tvPasswordRules.setTextColor(getColor(color));
    }

    private interface FieldCheck {
        String message(String value);
    }

    private void showGoogleSetupHelp() {
        new AlertDialog.Builder(this)
                .setTitle("Google Sign-In belum dikonfigurasi")
                .setMessage("Tambahkan google-services.json dan konfigurasikan SHA-1 certificate fingerprint di Firebase Console untuk mengaktifkan Sign-In dengan Google.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void openMain() {
        setLoading(false);
        // Registered here (not only at sign-up) so Google and email accounts
        // always end up with a `users/{uid}` document in Firestore.
        userRepository.ensureProfile();
        startActivity(new Intent(this, MainNavigationActivity.class));
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    private void setLoading(boolean loading) {
        btnSignIn.setEnabled(!loading);
        btnSignUp.setEnabled(!loading);
        btnSendReset.setEnabled(!loading);
        btnGoogleSignIn.setEnabled(!loading);
    }

    /* ------------------------------------------------------------------ */
    /* Friendly error handling                                             */
    /* ------------------------------------------------------------------ */

    private void showAuthError(String message) {
        if (authErrorCard == null || tvAuthError == null || TextUtils.isEmpty(message)) return;
        tvAuthError.setText(message);
        if (authErrorCard.getVisibility() != View.VISIBLE) {
            authErrorCard.setVisibility(View.VISIBLE);
            authErrorCard.setAlpha(0f);
            authErrorCard.setTranslationY(-8f);
            authErrorCard.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(180)
                    .start();
        }
    }

    private void hideAuthError() {
        if (authErrorCard == null) return;
        if (authErrorCard.getVisibility() == View.VISIBLE) {
            authErrorCard.animate().cancel();
            authErrorCard.setVisibility(View.GONE);
        }
    }

    private void showAuthSuccess(String message) {
        if (authSuccessCard == null || tvAuthSuccess == null || TextUtils.isEmpty(message)) return;
        tvAuthSuccess.setText(message);
        if (authSuccessCard.getVisibility() != View.VISIBLE) {
            authSuccessCard.setVisibility(View.VISIBLE);
            authSuccessCard.setAlpha(0f);
            authSuccessCard.setTranslationY(-8f);
            authSuccessCard.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(200)
                    .start();
        }
    }

    private void hideAuthSuccess() {
        if (authSuccessCard == null) return;
        if (authSuccessCard.getVisibility() == View.VISIBLE) {
            authSuccessCard.animate().cancel();
            authSuccessCard.setVisibility(View.GONE);
        }
    }

    private String errorCodeOf(Exception exception) {
        if (exception instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) exception).getErrorCode();
            return code == null ? "" : code;
        }
        return "";
    }

    private String friendlyAuthError(Exception exception) {
        switch (errorCodeOf(exception)) {
            case "ERROR_INVALID_EMAIL":
                return "Format email belum benar. Contoh yang benar: nama@email.com.";
            case "ERROR_WRONG_PASSWORD":
                return "Kata sandi salah. Periksa huruf besar/kecilnya, lalu coba lagi.";
            case "ERROR_INVALID_CREDENTIALS":
            case "ERROR_INVALID_LOGIN_CREDENTIALS":
            case "ERROR_INVALID_CREDENTIAL":
                return "Email atau kata sandi salah. Pastikan keduanya benar, lalu coba masuk lagi.";
            case "ERROR_USER_NOT_FOUND":
                return "Email ini belum terdaftar di RunTrack. Daftar dulu, atau gunakan email lain.";
            case "ERROR_EMAIL_ALREADY_IN_USE":
            case "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL":
                return "Email ini sudah terdaftar. Pilih Masuk untuk melanjutkan.";
            case "ERROR_WEAK_PASSWORD":
                return "Kata sandi terlalu lemah. Gunakan minimal 8 karakter berisi huruf besar, huruf kecil, dan angka.";
            case "ERROR_USER_DISABLED":
                return "Akun ini dinonaktifkan. Hubungi dukungan RunTrack bila perlu bantuan.";
            case "ERROR_TOO_MANY_REQUESTS":
                return "Terlalu banyak percobaan masuk. Tunggu beberapa menit, lalu coba lagi.";
            case "ERROR_NETWORK_REQUEST_FAILED":
                return "Tidak ada koneksi internet. Periksa jaringanmu, lalu coba lagi.";
            case "ERROR_OPERATION_NOT_ALLOWED":
                return "Masuk dengan email belum diaktifkan di server RunTrack.";
            case "ERROR_REQUIRES_RECENT_LOGIN":
                return "Demi keamanan, masuk ulang dulu sebelum melanjutkan.";
            case "ERROR_NO_SUCH_PROVIDER":
                return "Metode masuk ini belum aktif. Coba masuk dengan email dan kata sandi.";
            default:
                return "Masuk belum berhasil. Periksa email dan kata sandi, lalu coba lagi.";
        }
    }

    private String friendlySignupError(Exception exception) {
        switch (errorCodeOf(exception)) {
            case "ERROR_EMAIL_ALREADY_IN_USE":
            case "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL":
                return "Email ini sudah terdaftar. Pilih Masuk, atau gunakan email lain untuk mendaftar.";
            case "ERROR_INVALID_EMAIL":
                return "Format email belum benar. Contoh yang benar: nama@email.com.";
            case "ERROR_WEAK_PASSWORD":
                return "Kata sandi terlalu lemah. Gunakan minimal 8 karakter berisi huruf besar, huruf kecil, dan angka.";
            case "ERROR_OPERATION_NOT_ALLOWED":
                return "Pendaftaran dengan email belum diaktifkan di server RunTrack.";
            case "ERROR_NETWORK_REQUEST_FAILED":
                return "Tidak ada koneksi internet. Periksa jaringanmu, lalu coba lagi.";
            case "ERROR_TOO_MANY_REQUESTS":
                return "Terlalu banyak percobaan. Tunggu beberapa menit, lalu coba lagi.";
            default:
                return "Pendaftaran belum berhasil. Periksa kembali data kamu, lalu coba lagi.";
        }
    }

    private String friendlyResetError(Exception exception) {
        switch (errorCodeOf(exception)) {
            case "ERROR_USER_NOT_FOUND":
                return "Email ini belum terdaftar di RunTrack. Periksa penulisannya, atau daftar akun baru.";
            case "ERROR_INVALID_EMAIL":
                return "Format email belum benar. Contoh yang benar: nama@email.com.";
            case "ERROR_NETWORK_REQUEST_FAILED":
                return "Tidak ada koneksi internet. Periksa jaringanmu, lalu kirim ulang email.";
            case "ERROR_TOO_MANY_REQUESTS":
                return "Terlalu banyak permintaan. Tunggu beberapa menit, lalu coba lagi.";
            case "ERROR_USER_DISABLED":
                return "Akun ini dinonaktifkan. Hubungi dukungan RunTrack bila perlu bantuan.";
            default:
                return "Tautan reset belum bisa dikirim. Coba lagi beberapa saat lagi.";
        }
    }

    private void handleLoginFailure(Exception exception) {
        String code = errorCodeOf(exception);
        Log.w("LoginActivity", "login failed: code=" + code + " detail=" + exception);
        if ("ERROR_INVALID_EMAIL".equals(code)) {
            showFieldError(tilEmail, "Format email belum benar");
        } else if ("ERROR_USER_NOT_FOUND".equals(code)) {
            showFieldError(tilEmail, "Email ini belum terdaftar");
        }
        if ("ERROR_WRONG_PASSWORD".equals(code)
                || "ERROR_INVALID_CREDENTIALS".equals(code)
                || "ERROR_INVALID_LOGIN_CREDENTIALS".equals(code)
                || "ERROR_INVALID_CREDENTIAL".equals(code)) {
            showFieldError(tilPassword, "Kata sandi belum cocok");
        }
        showAuthError(friendlyAuthError(exception));
    }

    private void handleSignupFailure(Exception exception) {
        String code = errorCodeOf(exception);
        if ("ERROR_EMAIL_ALREADY_IN_USE".equals(code)
                || "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL".equals(code)) {
            showFieldError(tilSignupEmail, "Email sudah terdaftar");
        }
        if ("ERROR_WEAK_PASSWORD".equals(code)) {
            showFieldError(tilSignupPassword, "Kata sandi terlalu lemah");
        }
        showAuthError(friendlySignupError(exception));
    }

    private void handleResetFailure(Exception exception) {
        String code = errorCodeOf(exception);
        if ("ERROR_INVALID_EMAIL".equals(code)) {
            showFieldError(tilResetEmail, "Format email belum benar");
        } else if ("ERROR_USER_NOT_FOUND".equals(code)) {
            showFieldError(tilResetEmail, "Email ini belum terdaftar");
        }
        showAuthError(friendlyResetError(exception));
    }

    private double parseDouble(String value, double fallback) {
        if (value == null || value.isEmpty()) return fallback;
        try {
            return Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String rawTextOf(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString() : "";
    }

    private String validationTextOf(TextInputEditText editText) {
        return editText == etPassword || editText == etSignupPassword
                || editText == etSignupPasswordConfirm
                ? rawTextOf(editText) : textOf(editText);
    }

    private String textOf(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }
}
