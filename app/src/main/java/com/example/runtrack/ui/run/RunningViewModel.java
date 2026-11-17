package com.example.runtrack.ui.run;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.runtrack.data.ActivityRepository;
import com.example.runtrack.model.RunActivity;

public class RunningViewModel extends AndroidViewModel {

    private final ActivityRepository repo;
    private final MutableLiveData<Boolean> isTracking = new MutableLiveData<>(false);

    public RunningViewModel(@NonNull Application application) {
        super(application);
        repo = new ActivityRepository(application);
    }

    public LiveData<Boolean> isTracking() {
        return isTracking;
    }

    public void setTracking(boolean tracking) {
        isTracking.postValue(tracking);
    }

    public void saveActivity(RunActivity activity) {
        repo.saveActivity(activity);
    }
}
