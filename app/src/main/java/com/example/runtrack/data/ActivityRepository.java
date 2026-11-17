package com.example.runtrack.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.runtrack.db.AppDatabase;
import com.example.runtrack.db.RunDao;
import com.example.runtrack.db.RunEntity;
import com.example.runtrack.model.RunActivity;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Offline-first activity store.
 *
 * <p>Every run is written to Room immediately so the UI always has data.
 * Firestore is treated as a best-effort sync layer: when the network or the
 * security rules refuse access, the local row simply stays marked as
 * {@code synced = false} and is retried later.</p>
 */
public class ActivityRepository {

    private static final String TAG = "ActivityRepository";
    private static final String COLLECTION = "activities";

    public interface SaveCallback {
        void onSuccess(String documentId);

        void onFailure(Exception e);
    }

    public interface LoadCallback {
        void onLoaded(RunActivity activity);

        void onError(Exception e);
    }

    private final Context appContext;
    private final RunDao dao;
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<String> fetchErrorLiveData = new MutableLiveData<>();
    private volatile String pulledForUser;
    private volatile boolean pullInProgress;

    public ActivityRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.dao = AppDatabase.getInstance(appContext).runDao();
    }

    /* ------------------------------------------------------------------ */
    /* Writes                                                              */
    /* ------------------------------------------------------------------ */

    public void saveActivity(RunActivity activity, SaveCallback callback) {
        if (activity == null) {
            if (callback != null) callback.onFailure(new IllegalArgumentException("Activity is null"));
            return;
        }
        if (activity.userId == null || activity.userId.isEmpty()) {
            if (callback != null) callback.onFailure(new IllegalStateException("userId wajib diisi"));
            return;
        }
        if (activity.id == null || activity.id.isEmpty()) {
            activity.id = UUID.randomUUID().toString();
        }
        if (activity.activityName == null || activity.activityName.isEmpty()) {
            activity.activityName = "Lari";
        }
        if (activity.endTime <= 0) {
            activity.endTime = System.currentTimeMillis();
        }
        if (activity.durationMillis <= 0 && activity.endTime > activity.startTime) {
            activity.durationMillis = activity.endTime - activity.startTime;
        }

        final RunActivity toSave = activity;
        executor.execute(() -> {
            try {
                RunEntity entity = RunEntity.fromActivity(toSave, gson);
                entity.synced = false;
                dao.upsert(entity);
                mainHandler.post(() -> {
                    if (callback != null) callback.onSuccess(toSave.id);
                });
                uploadToFirestore(entity);
            } catch (Exception e) {
                Log.e(TAG, "Failed to save activity locally", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onFailure(e);
                });
            }
        });
    }

    public void saveActivity(RunActivity activity) {
        saveActivity(activity, null);
    }

    /** Renames an activity locally and in Firestore (merge so other fields survive). */
    public void updateActivityName(String id, String name, SaveCallback callback) {
        if (id == null || id.isEmpty()) {
            if (callback != null) callback.onFailure(new IllegalArgumentException("ID aktivitas kosong"));
            return;
        }
        final String cleanName = name == null || name.trim().isEmpty() ? "Lari" : name.trim();
        executor.execute(() -> {
            RunEntity entity = dao.getById(id);
            if (entity == null) {
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure(new IllegalStateException("Aktivitas tidak ditemukan"));
                    }
                });
                return;
            }
            entity.activityName = cleanName;
            entity.synced = false;
            entity.updatedAt = System.currentTimeMillis();
            dao.upsert(entity);
            mainHandler.post(() -> {
                if (callback != null) callback.onSuccess(id);
            });
            FirebaseFirestore.getInstance().collection(COLLECTION).document(id)
                    .set(java.util.Collections.singletonMap("activityName", cleanName),
                            com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener(unused -> executor.execute(() ->
                            dao.markSynced(id, true, System.currentTimeMillis())))
                    .addOnFailureListener(e ->
                            Log.w(TAG, "Firestore rename deferred: " + e.getMessage()));
        });
    }

    /**
     * Soft-delete: moves the activity to the trash (sets deletedAt timestamp).
     * The row is kept locally and the deletedAt flag is synced to Firestore.
     * Use {@link #hardDeleteActivity(String)} to remove permanently.
     */
    public void deleteActivity(String id) {
        if (id == null) return;
        final long now = System.currentTimeMillis();
        executor.execute(() -> {
            dao.softDeleteById(id, now, now);
            FirebaseFirestore.getInstance().collection(COLLECTION).document(id)
                    .set(java.util.Collections.singletonMap("deletedAt", now),
                            com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener(unused -> executor.execute(() ->
                            dao.markSynced(id, true, System.currentTimeMillis())))
                    .addOnFailureListener(e ->
                            Log.w(TAG, "Firestore soft-delete deferred: " + e.getMessage()));
        });
    }

    /** Permanently removes the activity from both Room and Firestore. */
    public void hardDeleteActivity(String id) {
        if (id == null) return;
        executor.execute(() -> {
            dao.hardDeleteById(id);
            FirebaseFirestore.getInstance().collection(COLLECTION).document(id).delete()
                    .addOnFailureListener(e ->
                            Log.w(TAG, "Firestore hard-delete failed: " + e.getMessage()));
        });
    }

    /** Restores a soft-deleted activity back to the active list. */
    public void restoreActivity(String id) {
        if (id == null) return;
        final long now = System.currentTimeMillis();
        executor.execute(() -> {
            dao.restoreById(id, now);
            FirebaseFirestore.getInstance().collection(COLLECTION).document(id)
                    .set(java.util.Collections.singletonMap("deletedAt", 0),
                            com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener(unused -> executor.execute(() ->
                            dao.markSynced(id, true, System.currentTimeMillis())))
                    .addOnFailureListener(e ->
                            Log.w(TAG, "Firestore restore deferred: " + e.getMessage()));
        });
    }

    /* ------------------------------------------------------------------ */
    /* Reads                                                               */
    /* ------------------------------------------------------------------ */

    /** Live data backed by Room; updates instantly after every saved run. */
    public LiveData<List<RunActivity>> observeAllActivities(String userId) {
        if (userId == null || userId.isEmpty()) {
            return new MutableLiveData<>(new ArrayList<>());
        }
        pullRemoteOnce(userId);
        return Transformations.map(dao.observeByUser(userId), this::toActivities);
    }

    public LiveData<List<RunActivity>> getRecentActivities(String userId, int limit) {
        if (userId == null || userId.isEmpty()) {
            return new MutableLiveData<>(new ArrayList<>());
        }
        pullRemoteOnce(userId);
        return Transformations.map(dao.observeRecent(userId, limit), this::toActivities);
    }

    /** Live data of soft-deleted activities (trash bin) for the user. */
    public LiveData<List<RunActivity>> observeDeletedActivities(String userId) {
        if (userId == null || userId.isEmpty()) {
            return new MutableLiveData<>(new ArrayList<>());
        }
        pullRemoteOnce(userId);
        return Transformations.map(dao.observeDeletedByUser(userId), this::toActivities);
    }

    public LiveData<String> getFetchError() {
        return fetchErrorLiveData;
    }

    /** Loads a single activity owned by the requested user. Uses the local cache first. */
    public void loadActivity(String id, String userId, LoadCallback callback) {
        if (id == null || id.isEmpty() || userId == null || userId.isEmpty()) {
            if (callback != null) callback.onError(new IllegalArgumentException("Aktivitas atau pengguna tidak valid"));
            return;
        }
        executor.execute(() -> {
            RunEntity entity = dao.getById(id);
            RunActivity localCandidate = entity != null ? entity.toActivity(gson) : null;
            final RunActivity local = localCandidate != null && userId.equals(localCandidate.userId)
                    ? localCandidate : null;
            if (local != null) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(local);
                });
                return;
            }
            FirebaseFirestore.getInstance().collection(COLLECTION).document(id).get()
                    .addOnSuccessListener(doc -> {
                        RunActivity remoteCandidate = parseDocument(doc);
                        final RunActivity remote = remoteCandidate != null && userId.equals(remoteCandidate.userId)
                                ? remoteCandidate : null;
                        if (remote != null) {
                            executor.execute(() -> {
                                RunEntity row = RunEntity.fromActivity(remote, gson);
                                row.synced = true;
                                dao.upsert(row);
                            });
                        }
                        mainHandler.post(() -> {
                            if (callback == null) return;
                            if (remote != null) callback.onLoaded(remote);
                            else callback.onError(new IllegalStateException("Aktivitas tidak ditemukan"));
                        });
                    })
                    .addOnFailureListener(e -> mainHandler.post(() -> {
                        if (callback != null) callback.onError(e);
                    }));
        });
    }

    public void stopObserving() {
        // Room LiveData handles its own lifecycle.
    }

    /** @deprecated gunakan {@link #observeAllActivities(String)} */
    @Deprecated
    public LiveData<List<RunActivity>> getAllActivities(String userId) {
        return observeAllActivities(userId);
    }

    /* ------------------------------------------------------------------ */
    /* Firestore sync                                                      */
    /* ------------------------------------------------------------------ */

    private void pullRemoteOnce(String userId) {
        if (userId == null || pullInProgress || userId.equals(pulledForUser)) return;
        pullInProgress = true;
        FirebaseFirestore.getInstance().collection(COLLECTION)
                .whereEqualTo("userId", userId)
                .limit(200)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<RunActivity> remote = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        RunActivity activity = parseDocument(doc);
                        if (activity != null) remote.add(activity);
                    }
                    executor.execute(() -> {
                        Set<String> pending = new HashSet<>();
                        for (RunEntity row : dao.pendingSync(userId)) {
                            pending.add(row.id);
                        }
                        List<RunEntity> rows = new ArrayList<>();
                        for (RunActivity activity : remote) {
                            if (pending.contains(activity.id)) continue;
                            RunEntity entity = RunEntity.fromActivity(activity, gson);
                            entity.synced = true;
                            rows.add(entity);
                        }
                        if (!rows.isEmpty()) dao.upsertAll(rows);
                        syncPendingInternal(userId);
                    });
                    pulledForUser = userId;
                    pullInProgress = false;
                    fetchErrorLiveData.postValue(null);
                    Log.d(TAG, "Remote activities merged: " + remote.size());
                })
                .addOnFailureListener(e -> {
                    pullInProgress = false;
                    // Permission denied / offline is expected in local-first mode.
                    Log.w(TAG, "Firestore pull skipped: " + e.getMessage());
                });
    }

    private void uploadToFirestore(RunEntity entity) {
        RunActivity activity = entity.toActivity(gson);
        FirebaseFirestore.getInstance().collection(COLLECTION)
                .document(entity.id)
                .set(activity)
                .addOnSuccessListener(unused -> executor.execute(() ->
                        dao.markSynced(entity.id, true, System.currentTimeMillis())))
                .addOnFailureListener(e ->
                        Log.w(TAG, "Firestore upload deferred: " + e.getMessage()));
    }

    private void syncPendingInternal(String userId) {
        if (userId == null || userId.isEmpty()) return;
        List<RunEntity> pending = dao.pendingSync(userId);
        for (RunEntity entity : pending) {
            uploadToFirestore(entity);
        }
    }

    private RunActivity parseDocument(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        try {
            RunActivity activity = doc.toObject(RunActivity.class);
            if (activity == null) return null;
            activity.id = doc.getId();
            return activity;
        } catch (Exception e) {
            Log.w(TAG, "Cannot parse activity " + doc.getId(), e);
            return null;
        }
    }

    private List<RunActivity> toActivities(List<RunEntity> entities) {
        List<RunActivity> result = new ArrayList<>();
        if (entities == null) return result;
        for (RunEntity entity : entities) {
            result.add(entity.toActivity(gson));
        }
        return result;
    }
}
