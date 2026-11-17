package com.example.runtrack.ui.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.runtrack.R;
import com.example.runtrack.BuildConfig;
import com.example.runtrack.data.AppSettings;
import com.example.runtrack.model.UserProfile;
import com.example.runtrack.ui.auth.LoginActivity;
import com.example.runtrack.ui.home.HomeViewModel;
import com.example.runtrack.util.SessionManager;
import com.example.runtrack.util.ThemeTransitioner;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.squareup.picasso.Picasso;

import java.util.Locale;

public class ProfileFragment extends Fragment {

    private TextView tvInitial;
    private ImageView ivProfilePhoto;
    private ActivityResultLauncher<PickVisualMediaRequest> photoPicker;
    private TextView tvName;
    private TextView tvEmail;
    private TextView tvAge;
    private TextView tvGender;
    private TextView tvHeight;
    private TextView tvWeight;
    private TextInputEditText etDisplayName;
    private TextInputEditText etEmail;
    private TextInputEditText etAge;
    private TextInputEditText etGender;
    private TextInputEditText etHeight;
    private TextInputEditText etWeight;
    private MaterialCardView cardEditProfile;
    private SwitchMaterial switchDark;
    private SwitchMaterial switchNotifications;

    private HomeViewModel viewModel;
    private UserProfile currentProfile;
    private boolean editing;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        tvInitial = view.findViewById(R.id.tv_profile_initial);
        ivProfilePhoto = view.findViewById(R.id.iv_profile_photo);
        tvName = view.findViewById(R.id.tv_profile_name);
        tvEmail = view.findViewById(R.id.tv_profile_email);
        tvAge = view.findViewById(R.id.tv_profile_age);
        tvGender = view.findViewById(R.id.tv_profile_gender);
        tvHeight = view.findViewById(R.id.tv_profile_height);
        tvWeight = view.findViewById(R.id.tv_profile_weight);
        ((TextView) view.findViewById(R.id.tv_profile_version))
                .setText("v" + BuildConfig.VERSION_NAME);

        cardEditProfile = view.findViewById(R.id.card_edit_profile);
        etDisplayName = view.findViewById(R.id.et_display_name);
        etEmail = view.findViewById(R.id.et_email);
        etAge = view.findViewById(R.id.et_age);
        etGender = view.findViewById(R.id.et_gender);
        etGender.setClickable(true);
        etGender.setFocusable(false);
        etGender.setCursorVisible(false);
        etGender.setOnClickListener(v -> showGenderPicker());
        etHeight = view.findViewById(R.id.et_height);
        etWeight = view.findViewById(R.id.et_weight);
        // Email is the login identity and is verified by Firebase Auth.
        etEmail.setEnabled(false);
        etEmail.setFocusable(false);
        etEmail.setCursorVisible(false);

        switchDark = view.findViewById(R.id.switch_dark);
        switchNotifications = view.findViewById(R.id.switch_notifications);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        switchDark.setChecked(prefs.getBoolean("dark_mode", true));
        switchNotifications.setChecked(prefs.getBoolean("notifications_enabled", true));

        switchDark.setOnCheckedChangeListener((buttonView, isChecked) -> {
            boolean currentDark = prefs.getBoolean("dark_mode", true);
            if (isChecked == currentDark) return;
            prefs.edit().putBoolean("dark_mode", isChecked).apply();
            AppSettings.setDarkMode(requireContext(), isChecked);
            if (getActivity() != null) {
                ThemeTransitioner.switchTheme(getActivity(), isChecked);
            }
        });
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("notifications_enabled", isChecked).apply();
            if (isChecked) requestNotificationPermissionIfNeeded();
        });

        view.findViewById(R.id.btn_open_full_settings).setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), com.example.runtrack.ui.settings.SettingsActivity.class));
            if (getActivity() != null) {
                getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
            }
        });

        view.findViewById(R.id.btn_edit_profile).setOnClickListener(v -> showEditForm(true));
        view.findViewById(R.id.btn_cancel_edit).setOnClickListener(v -> {
            showEditForm(false);
            fillEditFormFromProfile();
        });
        view.findViewById(R.id.btn_save_profile).setOnClickListener(v -> saveProfile());
        view.findViewById(R.id.btn_logout).setOnClickListener(v -> logout());

        photoPicker = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) saveProfilePhoto(uri);
                });
        View.OnClickListener pickPhoto = v -> {
            try {
                photoPicker.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
            } catch (Exception e) {
                Toast.makeText(requireContext(),
                        "Pemilih foto tidak tersedia di perangkat ini",
                        Toast.LENGTH_SHORT).show();
            }
        };
        view.findViewById(R.id.btn_change_photo).setOnClickListener(pickPhoto);
        view.findViewById(R.id.tv_change_photo_hint).setOnClickListener(pickPhoto);

        viewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        viewModel.getCurrentUserProfile().observe(getViewLifecycleOwner(), this::bindProfile);

        return view;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 120);
        }
    }

    private void showEditForm(boolean show) {
        editing = show;
        cardEditProfile.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showGenderPicker() {
        final String[] options = {"Pria", "Wanita", "Lainnya"};
        String current = etGender.getText() != null ? etGender.getText().toString().trim() : "";
        int checked = 0;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(current)) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Pilih gender")
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    etGender.setText(options[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void bindProfile(UserProfile profile) {
        if (!isAdded()) return;
        if (profile == null) {
            profile = new UserProfile();
            profile.displayName = "Runner";
        }
        currentProfile = profile;

        String name = profile.displayName != null && !profile.displayName.isEmpty()
                ? profile.displayName : "Runner";
        tvName.setText(name);
        tvInitial.setText(initialOf(name));
        bindPhoto(profile);
        tvEmail.setText(profile.email != null && !profile.email.isEmpty()
                ? profile.email : "-");

        tvAge.setText(profile.age > 0
                ? String.format(Locale.getDefault(), "%d th", profile.age) : "--");
        String genderText = normalizeGender(profile.gender);
        tvGender.setText(genderText.isEmpty() ? "--" : genderText);
        tvHeight.setText(profile.heightCm > 0
                ? String.format(Locale.getDefault(), "%.0f cm", profile.heightCm) : "--");
        tvWeight.setText(profile.weightKg > 0
                ? String.format(Locale.getDefault(), "%.0f kg", profile.weightKg) : "--");

        if (!editing) fillEditFormFromProfile();
    }

    private void bindPhoto(UserProfile profile) {
        if (ivProfilePhoto == null || tvInitial == null) return;
        String photoUrl = profile != null ? profile.photoUrl : null;
        String encoded = profile != null ? profile.photoBase64 : null;

        if (photoUrl != null && !photoUrl.isEmpty()) {
            try {
                Picasso.get()
                        .load(photoUrl)
                        .fit()
                        .centerCrop()
                        .noFade()
                        .into(ivProfilePhoto, new com.squareup.picasso.Callback() {
                            @Override
                            public void onSuccess() {
                                ivProfilePhoto.setVisibility(View.VISIBLE);
                                tvInitial.setVisibility(View.GONE);
                            }

                            @Override
                            public void onError(Exception e) {
                                loadBase64Fallback(encoded);
                            }
                        });
                return;
            } catch (Exception e) {
                loadBase64Fallback(encoded);
                return;
            }
        }
        loadBase64Fallback(encoded);
    }

    private void loadBase64Fallback(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            ivProfilePhoto.setVisibility(View.GONE);
            ivProfilePhoto.setImageDrawable(null);
            tvInitial.setVisibility(View.VISIBLE);
            return;
        }
        try {
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) {
                ivProfilePhoto.setVisibility(View.GONE);
                tvInitial.setVisibility(View.VISIBLE);
                return;
            }
            ivProfilePhoto.setImageBitmap(bitmap);
            ivProfilePhoto.setVisibility(View.VISIBLE);
            tvInitial.setVisibility(View.GONE);
        } catch (Exception e) {
            ivProfilePhoto.setVisibility(View.GONE);
            tvInitial.setVisibility(View.VISIBLE);
        }
    }

    private void saveProfilePhoto(Uri uri) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), "Sesi login berakhir", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Bitmap bitmap = decodeSampledBitmap(uri, 720);
            if (bitmap == null) {
                Toast.makeText(requireContext(), "Foto tidak bisa dibaca", Toast.LENGTH_SHORT).show();
                return;
            }
            int maxSide = 512;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            float scale = Math.min(1f, (float) maxSide / Math.max(width, height));
            if (scale < 1f) {
                bitmap = Bitmap.createScaledBitmap(bitmap,
                        Math.max(1, Math.round(width * scale)),
                        Math.max(1, Math.round(height * scale)), true);
            }

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out);
            byte[] bytes = out.toByteArray();
            String encoded = Base64.encodeToString(bytes, Base64.NO_WRAP);

            UserProfile profile = currentProfile != null ? currentProfile : new UserProfile();
            profile.uid = user.getUid();
            profile.photoBase64 = encoded;
            currentProfile = profile;
            bindPhoto(profile);
            viewModel.saveProfile(profile);

            StorageReference photoRef = FirebaseStorage.getInstance()
                    .getReference("profile-photos/" + user.getUid() + "/profile.jpg");
            photoRef.putBytes(bytes)
                    .addOnSuccessListener(taskSnapshot ->
                            taskSnapshot.getStorage().getDownloadUrl()
                                    .addOnSuccessListener(downloadUrl -> {
                                        profile.photoUrl = downloadUrl.toString();
                                        currentProfile = profile;
                                        viewModel.saveProfile(profile);
                                    }))
                    .addOnFailureListener(e ->
                            android.util.Log.w("ProfileFragment",
                                    "Photo upload deferred, Base64 fallback saved: " + e.getMessage()));

            Toast.makeText(requireContext(), "Foto profil diperbarui", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    "Gagal memproses foto: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private Bitmap decodeSampledBitmap(Uri uri, int maxDimension) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (java.io.InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        while (longest / sample > maxDimension * 2) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        try (java.io.InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, options);
        }
    }

    private void fillEditFormFromProfile() {
        if (currentProfile == null) return;
        etDisplayName.setText(currentProfile.displayName != null ? currentProfile.displayName : "");
        etEmail.setText(currentProfile.email != null ? currentProfile.email : "");
        etAge.setText(currentProfile.age > 0 ? String.valueOf(currentProfile.age) : "");
        etGender.setText(normalizeGender(currentProfile.gender));
        etHeight.setText(currentProfile.heightCm > 0
                ? String.format(Locale.getDefault(), "%.0f", currentProfile.heightCm) : "");
        etWeight.setText(currentProfile.weightKg > 0
                ? String.format(Locale.getDefault(), "%.0f", currentProfile.weightKg) : "");
    }

    private void saveProfile() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            Toast.makeText(requireContext(), "Sesi login berakhir", Toast.LENGTH_SHORT).show();
            return;
        }

        clearFieldErrors();
        String name = textOf(etDisplayName);
        String ageValue = textOf(etAge);
        String heightValue = textOf(etHeight);
        String weightValue = textOf(etWeight);
        String gender = textOf(etGender);

        String nameError = validateName(name);
        String ageError = validateAge(ageValue);
        String heightError = validateHeight(heightValue);
        String weightError = validateWeight(weightValue);
        String genderError = gender.isEmpty() ? "Pilih gender" : null;

        boolean invalid = false;
        String firstError = null;
        if (nameError != null) {
            etDisplayName.setError(nameError);
            firstError = firstError == null ? nameError : firstError;
            invalid = true;
        }
        if (ageError != null) {
            etAge.setError(ageError);
            firstError = firstError == null ? ageError : firstError;
            invalid = true;
        }
        if (heightError != null) {
            etHeight.setError(heightError);
            firstError = firstError == null ? heightError : firstError;
            invalid = true;
        }
        if (weightError != null) {
            etWeight.setError(weightError);
            firstError = firstError == null ? weightError : firstError;
            invalid = true;
        }
        if (genderError != null) {
            etGender.setError(genderError);
            firstError = firstError == null ? genderError : firstError;
            invalid = true;
        }
        if (invalid) {
            Toast.makeText(requireContext(),
                    firstError != null ? firstError : "Periksa kembali data yang ditandai",
                    Toast.LENGTH_LONG).show();
            return;
        }

        UserProfile profile = currentProfile != null ? currentProfile : new UserProfile();
        profile.uid = firebaseUser.getUid();
        profile.displayName = name;
        String email = textOf(etEmail);
        if (!email.isEmpty()) profile.email = email;
        profile.age = (int) parseDouble(ageValue, profile.age);
        profile.gender = gender;
        profile.heightCm = parseDouble(heightValue, profile.heightCm);
        profile.weightKg = parseDouble(weightValue, profile.weightKg);

        viewModel.saveProfile(profile);
        if (name != null && !name.equals(firebaseUser.getDisplayName())) {
            firebaseUser.updateProfile(new com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(name).build())
                    .addOnFailureListener(error ->
                            android.util.Log.w("ProfileFragment",
                                    "Auth display name update deferred: " + error.getMessage()));
        }
        currentProfile = profile;
        bindProfile(profile);
        showEditForm(false);
        Toast.makeText(requireContext(), "Data diri tersimpan di akun Anda",
                Toast.LENGTH_SHORT).show();
    }

    private void clearFieldErrors() {
        etDisplayName.setError(null);
        etEmail.setError(null);
        etAge.setError(null);
        etGender.setError(null);
        etHeight.setError(null);
        etWeight.setError(null);
    }

    private String validateName(String name) {
        if (name.isEmpty()) return "Nama wajib diisi";
        if (name.length() < 2 || name.length() > 40) return "Nama harus 2–40 karakter";
        if (!name.matches("^[\\p{L}][\\p{L} .'-]*$")) {
            return "Nama hanya boleh huruf, spasi, titik, atau tanda hubung";
        }
        return null;
    }

    private String validateAge(String value) {
        if (value.isEmpty()) return "Umur wajib diisi";
        try {
            int age = Integer.parseInt(value);
            if (age < 10 || age > 100) return "Umur harus antara 10 dan 100 tahun";
        } catch (NumberFormatException e) {
            return "Masukkan umur berupa angka";
        }
        return null;
    }

    private String validateHeight(String value) {
        if (value.isEmpty()) return "Tinggi wajib diisi";
        double height = parseDouble(value, -1);
        if (!Double.isFinite(height) || height < 100 || height > 250) {
            return "Tinggi harus antara 100 dan 250 cm";
        }
        return null;
    }

    private String validateWeight(String value) {
        if (value.isEmpty()) return "Berat wajib diisi";
        double weight = parseDouble(value, -1);
        if (!Double.isFinite(weight) || weight < 25 || weight > 300) {
            return "Berat harus antara 25 dan 300 kg";
        }
        return null;
    }

    private double parseDouble(String value, double fallback) {
        if (value == null || value.isEmpty()) return fallback;
        try {
            return Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void logout() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Keluar dari akun?")
                .setMessage("Kamu perlu masuk lagi untuk melanjutkan. Data latihanmu tetap tersimpan di akun.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Keluar", (dialog, which) -> performLogout())
                .show();
    }

    private void performLogout() {
        FirebaseAuth.getInstance().signOut();
        SessionManager.clear(requireContext());
        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private String textOf(TextInputEditText editText) {
        return editText != null && editText.getText() != null
                ? editText.getText().toString().trim() : "";
    }

    private String initialOf(String name) {
        if (name == null || name.trim().isEmpty()) return "R";
        return name.trim().substring(0, 1).toUpperCase(Locale.getDefault());
    }

    private String normalizeGender(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String value = raw.trim();
        if (value.equalsIgnoreCase("male") || value.equalsIgnoreCase("laki-laki")) return "Pria";
        if (value.equalsIgnoreCase("female") || value.equalsIgnoreCase("perempuan")) return "Wanita";
        return capitalize(value);
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) return "--";
        return value.substring(0, 1).toUpperCase(Locale.getDefault()) + value.substring(1);
    }
}
