package com.example.runtrack.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.runtrack.model.UserProfile;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

/**
 * Profile store with a SharedPreferences-first cache.
 *
 * <p>The cached profile is returned immediately (so the UI never shows an
 * empty header when Firestore denies access), then refreshed from Firestore
 * when possible.</p>
 */
public class UserRepository {

    private static final String TAG = "UserRepository";
    private static final String PREFS = "runtrack_local";
    private static final String KEY_PREFIX = "profile_";

    private static Context appContext;
    private static final Gson GSON = new Gson();

    private final MutableLiveData<UserProfile> liveData = new MutableLiveData<>();
    private String lastUid;

    public UserRepository() {
    }

    public UserRepository(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    /** Called once from the Application so no-arg repositories work everywhere. */
    public static void init(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    private static SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public LiveData<UserProfile> getUser(String uid) {
        if (uid == null || uid.isEmpty()) {
            liveData.postValue(null);
            return liveData;
        }
        lastUid = uid;
        UserProfile cached = getCached(uid);
        if (cached == null) {
            cached = defaultProfile(uid);
            cache(cached);
        }
        liveData.postValue(cached);

        final UserProfile fallback = cached;
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener((DocumentSnapshot snapshot) -> {
                    UserProfile profile = snapshot.exists()
                            ? snapshot.toObject(UserProfile.class) : null;
                    if (profile == null) profile = fallback;
                    profile.uid = uid;
                    if (profile.displayName == null || profile.displayName.isEmpty()) {
                        profile.displayName = fallback.displayName;
                    }
                    if (profile.email == null || profile.email.isEmpty()) {
                        profile.email = fallback.email;
                    }
                    cache(profile);
                    if (uid.equals(lastUid)) liveData.postValue(profile);
                    if (!snapshot.exists()) persistRemote(profile);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Firestore profile read skipped: " + e.getMessage());
                    if (uid.equals(lastUid)) liveData.postValue(fallback);
                });
        return liveData;
    }

    public void saveUser(UserProfile profile) {
        saveUser(profile, null);
    }

    /**
     * Saves the profile locally and to Firestore. {@code onComplete} runs on the
     * main thread as soon as the remote write succeeds or fails, so callers can
     * safely end a session afterwards (for example after registration).
     */
    public void saveUser(UserProfile profile, Runnable onComplete) {
        if (profile == null || profile.uid == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        cache(profile);
        liveData.postValue(profile);
        persistRemote(profile, onComplete);
    }

    /**
     * Makes sure the signed-in account has a document in the {@code users}
     * collection, no matter how the user signed in (email/password or Google).
     *
     * <p>Identity fields are always merged, so a Google display name or a new
     * email shows up in Firestore. The rest of the profile is only written when
     * the document does not exist yet, which keeps edits from other devices.</p>
     */
    public void ensureProfile() {
        ensureProfile(FirebaseAuth.getInstance().getCurrentUser());
    }

    public void ensureProfile(FirebaseUser user) {
        if (user == null || TextUtils.isEmpty(user.getUid())) return;
        final String uid = user.getUid();

        UserProfile cached = getCached(uid);
        final UserProfile profile = cached != null ? cached : new UserProfile();
        profile.uid = uid;
        if (TextUtils.isEmpty(profile.email)) {
            profile.email = user.getEmail();
        }
        if (TextUtils.isEmpty(profile.displayName)) {
            String name = user.getDisplayName();
            if (TextUtils.isEmpty(name) && !TextUtils.isEmpty(user.getEmail())
                    && user.getEmail().contains("@")) {
                name = user.getEmail().substring(0, user.getEmail().indexOf('@'));
            }
            profile.displayName = TextUtils.isEmpty(name) ? "Runner" : name;
        }
        cache(profile);
        if (lastUid == null || lastUid.equals(uid)) {
            liveData.postValue(profile);
        }

        final DocumentReference ref = FirebaseFirestore.getInstance()
                .collection("users").document(uid);

        Map<String, Object> identity = new HashMap<>();
        identity.put("uid", uid);
        identity.put("email", profile.email);
        identity.put("displayName", profile.displayName);
        identity.put("lastSeenAt", FieldValue.serverTimestamp());
        identity.put("updatedAt", FieldValue.serverTimestamp());

        ref.get().addOnCompleteListener(read -> {
            boolean exists = read.isSuccessful() && read.getResult() != null
                    && read.getResult().exists();
            if (exists) {
                ref.set(identity, SetOptions.merge())
                        .addOnFailureListener(e ->
                                Log.w(TAG, "Firestore identity sync deferred: " + e.getMessage()));
                return;
            }
            Map<String, Object> created = new HashMap<>(identity);
            created.put("weightKg", profile.weightKg);
            created.put("heightCm", profile.heightCm);
            created.put("age", profile.age);
            created.put("gender", profile.gender);
            if (!TextUtils.isEmpty(profile.photoBase64)) {
                created.put("photoBase64", profile.photoBase64);
            }
            created.put("createdAt", FieldValue.serverTimestamp());
            ref.set(created, SetOptions.merge())
                    .addOnSuccessListener(unused ->
                            Log.d(TAG, "Firestore profile created for " + uid))
                    .addOnFailureListener(e ->
                            Log.w(TAG, "Firestore profile create deferred: " + e.getMessage()));
        }).addOnFailureListener(e ->
                Log.w(TAG, "Firestore profile check deferred: " + e.getMessage()));
    }

    private void persistRemote(UserProfile profile) {
        persistRemote(profile, null);
    }

    private void persistRemote(UserProfile profile, Runnable onComplete) {
        DocumentReference ref = FirebaseFirestore.getInstance().collection("users")
                .document(profile.uid);
        ref.set(profile, SetOptions.merge())
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Firestore profile save deferred: "
                                + (task.getException() != null
                                ? task.getException().getMessage() : "unknown"));
                    }
                    if (onComplete != null) onComplete.run();
                });
        // Always stamp the update time, even when a POJO field is absent.
        ref.set(java.util.Collections.singletonMap("updatedAt", FieldValue.serverTimestamp()),
                        SetOptions.merge())
                .addOnFailureListener(e ->
                        Log.w(TAG, "updatedAt stamp deferred: " + e.getMessage()));
    }

    private static void cache(UserProfile profile) {
        if (profile == null || profile.uid == null || appContext == null) return;
        prefs().edit().putString(KEY_PREFIX + profile.uid, GSON.toJson(profile)).apply();
    }

    /** Returns the cached profile or {@code null}. */
    public static UserProfile getCached(String uid) {
        if (uid == null || appContext == null) return null;
        String json = prefs().getString(KEY_PREFIX + uid, null);
        if (json == null || json.isEmpty()) return null;
        try {
            UserProfile profile = GSON.fromJson(json, UserProfile.class);
            if (profile != null) profile.uid = uid;
            return profile;
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns a usable profile (cache, then FirebaseAuth, then defaults). */
    public static UserProfile getCachedOrDefault(String uid) {
        UserProfile cached = getCached(uid);
        if (cached != null) return cached;
        UserProfile fallback = defaultProfile(uid);
        cache(fallback);
        return fallback;
    }

    public static String getDisplayName(String uid) {
        UserProfile profile = getCached(uid);
        if (profile != null && profile.displayName != null && !profile.displayName.isEmpty()) {
            return profile.displayName;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
            return user.getDisplayName();
        }
        return "Runner";
    }

    private static UserProfile defaultProfile(String uid) {
        UserProfile cached = getCached(uid);
        if (cached != null) return cached;

        UserProfile profile = new UserProfile();
        profile.uid = uid;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && (uid == null || uid.equals(user.getUid()))) {
            String name = user.getDisplayName();
            if (name == null || name.isEmpty()) {
                String email = user.getEmail();
                if (email != null && email.contains("@")) {
                    name = email.substring(0, email.indexOf('@'));
                }
            }
            profile.displayName = (name == null || name.isEmpty()) ? "Runner" : name;
            profile.email = user.getEmail();
        } else {
            profile.displayName = "Runner";
        }
        return profile;
    }
}
