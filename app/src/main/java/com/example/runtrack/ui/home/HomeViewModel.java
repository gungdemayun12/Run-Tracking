package com.example.runtrack.ui.home;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.runtrack.data.ActivityRepository;
import com.example.runtrack.data.UserRepository;
import com.example.runtrack.model.RunActivity;
import com.example.runtrack.model.UserProfile;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Collections;
import java.util.List;

public class HomeViewModel extends AndroidViewModel {

    private final UserRepository userRepo;
    private final ActivityRepository activityRepo;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        userRepo = new UserRepository(application);
        activityRepo = new ActivityRepository(application);
    }

    public LiveData<UserProfile> getCurrentUserProfile() {
        String uid = getUid();
        if (uid == null) return new MutableLiveData<>(null);
        return userRepo.getUser(uid);
    }

    public LiveData<List<RunActivity>> getAllActivities() {
        String uid = getUid();
        if (uid == null) return new MutableLiveData<>(Collections.emptyList());
        return activityRepo.observeAllActivities(uid);
    }

    public LiveData<String> getActivitiesFetchError() {
        return activityRepo.getFetchError();
    }

    public LiveData<List<RunActivity>> getRecentActivities(int limit) {
        String uid = getUid();
        if (uid == null) return new MutableLiveData<>(Collections.emptyList());
        return activityRepo.getRecentActivities(uid, limit);
    }

    public LiveData<List<RunActivity>> getDeletedActivities() {
        String uid = getUid();
        if (uid == null) return new MutableLiveData<>(Collections.emptyList());
        return activityRepo.observeDeletedActivities(uid);
    }

    public void deleteActivity(String id) {
        activityRepo.deleteActivity(id);
    }

    public void hardDeleteActivity(String id) {
        activityRepo.hardDeleteActivity(id);
    }

    public void restoreActivity(String id) {
        activityRepo.restoreActivity(id);
    }

    public void saveProfile(UserProfile profile) {
        userRepo.saveUser(profile);
    }

    public String getUserDisplayName() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
            return user.getDisplayName();
        }
        UserProfile cached = UserRepository.getCached(getUid());
        if (cached != null && cached.displayName != null && !cached.displayName.isEmpty()) {
            return cached.displayName;
        }
        return "Runner";
    }

    public String getUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        activityRepo.stopObserving();
    }
}
