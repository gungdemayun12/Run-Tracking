package com.example.runtrack.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.example.runtrack.R;
import com.example.runtrack.data.UserRepository;
import com.example.runtrack.model.UserProfile;
import com.example.runtrack.ui.auth.LoginActivity;
import com.example.runtrack.ui.fragments.HistoryFragment;
import com.example.runtrack.ui.fragments.HomeFragment;
import com.example.runtrack.ui.fragments.ProfileFragment;
import com.example.runtrack.ui.fragments.ProgressFragment;
import com.example.runtrack.ui.home.HomeViewModel;
import com.example.runtrack.ui.run.RunningActivity;
import com.example.runtrack.util.SessionManager;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Locale;

public class MainNavigationActivity extends AppCompatActivity {

    public static final int TAB_HOME = 0;
    public static final int TAB_HISTORY = 1;
    public static final int TAB_PROGRESS = 2;
    public static final int TAB_PROFILE = 3;

    private static final String[] TAGS = {"tab_home", "tab_history", "tab_progress", "tab_profile"};
    private static final String STATE_TAB = "selected_tab";

    private final Fragment[] fragments = new Fragment[4];
    private View[] navItems;
    private TextView tvAvatar;
    private int selectedTab = TAB_HOME;
    private boolean fragmentsAdded;

    private ActivityResultLauncher<Intent> runLauncher;
    private UserRepository userRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main_navigation);

        // Every session (including restored ones) keeps a profile document in
        // Firestore, even before the user records a single run.
        userRepository = new UserRepository(this);
        userRepository.ensureProfile();

        navItems = new View[]{
                findViewById(R.id.nav_home),
                findViewById(R.id.nav_history),
                findViewById(R.id.nav_progress),
                findViewById(R.id.nav_profile)
        };
        tvAvatar = findViewById(R.id.tv_toolbar_avatar);

        navItems[TAB_HOME].setOnClickListener(v -> selectTab(TAB_HOME));
        navItems[TAB_HISTORY].setOnClickListener(v -> selectTab(TAB_HISTORY));
        navItems[TAB_PROGRESS].setOnClickListener(v -> selectTab(TAB_PROGRESS));
        navItems[TAB_PROFILE].setOnClickListener(v -> selectTab(TAB_PROFILE));

        runLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> refreshHeader());

        findViewById(R.id.btn_run_center).setOnClickListener(v -> {
            Intent intent = new Intent(this, RunningActivity.class);
            runLauncher.launch(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
        });

        if (savedInstanceState != null) {
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            selectedTab = savedInstanceState.getInt(STATE_TAB, TAB_HOME);
        }
        restoreFragments(savedInstanceState);
        selectTab(selectedTab);

        HomeViewModel viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        viewModel.getCurrentUserProfile().observe(this, this::bindProfile);
        refreshHeader();
    }

    private void restoreFragments(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;
        fragmentsAdded = true;
        for (int i = 0; i < fragments.length; i++) {
            fragments[i] = getSupportFragmentManager().findFragmentByTag(TAGS[i]);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Extend the remembered session every time the user comes back to the app.
        SessionManager.touch(this);
        if (userRepository != null) userRepository.ensureProfile();
    }

    public void selectTab(int index) {
        if (index < 0 || index >= fragments.length) return;
        int previous = selectedTab;
        boolean switching = fragmentsAdded && index != previous;
        selectedTab = index;
        if (navItems != null) {
            for (int i = 0; i < navItems.length; i++) {
                navItems[i].setSelected(i == index);
            }
        }

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (switching) {
            boolean forward = index > previous;
            transaction.setCustomAnimations(
                    forward ? R.anim.nav_in_right : R.anim.nav_in_left,
                    forward ? R.anim.nav_out_left : R.anim.nav_out_right);
        } else {
            transaction.setCustomAnimations(R.anim.fade_in, R.anim.fade_out);
        }
        if (navItems != null) {
            for (int i = 0; i < navItems.length; i++) {
                float scale = i == index ? 1.06f : 1f;
                navItems[i].animate().scaleX(scale).scaleY(scale).setDuration(220).start();
            }
        }
        if (!fragmentsAdded) {
            if (fragments[index] == null) fragments[index] = createFragment(index);
            transaction.add(R.id.container, fragments[index], TAGS[index]);
            fragmentsAdded = true;
        } else {
            for (int i = 0; i < fragments.length; i++) {
                if (fragments[i] == null) {
                    fragments[i] = getSupportFragmentManager().findFragmentByTag(TAGS[i]);
                }
                if (fragments[i] == null) {
                    fragments[i] = createFragment(i);
                    transaction.add(R.id.container, fragments[i], TAGS[i]);
                }
                if (i == index) transaction.show(fragments[i]);
                else transaction.hide(fragments[i]);
            }
        }
        transaction.commitAllowingStateLoss();
    }

    private Fragment createFragment(int index) {
        switch (index) {
            case TAB_HISTORY:
                return new HistoryFragment();
            case TAB_PROGRESS:
                return new ProgressFragment();
            case TAB_PROFILE:
                return new ProfileFragment();
            case TAB_HOME:
            default:
                return new HomeFragment();
        }
    }

    private void bindProfile(UserProfile profile) {
        String name = profile != null && profile.displayName != null
                ? profile.displayName : "Runner";
        if (tvAvatar != null) {
            tvAvatar.setText(initialOf(name));
        }
        HomeViewModel viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        if (profile == null) {
            if (tvAvatar != null) tvAvatar.setText(initialOf(viewModel.getUserDisplayName()));
        }
    }

    private void refreshHeader() {
        HomeViewModel viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        if (tvAvatar != null) {
            tvAvatar.setText(initialOf(viewModel.getUserDisplayName()));
        }
    }

    private String initialOf(String name) {
        if (name == null || name.trim().isEmpty()) return "R";
        return name.trim().substring(0, 1).toUpperCase(Locale.getDefault());
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, selectedTab);
    }
}
