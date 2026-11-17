package com.example.runtrack;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.runtrack.data.UserRepository;
import com.example.runtrack.util.OsmdroidMapHelper;
import com.example.runtrack.util.ThemeTransitioner;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;

import java.io.File;

public class RunTrackApplication extends Application {
    @Override
    public void onCreate() {
        SharedPreferences uiPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!uiPrefs.getBoolean("dark_default_applied", false)) {
            uiPrefs.edit()
                    .putBoolean("dark_mode", true)
                    .putBoolean("dark_default_applied", true)
                    .apply();
        }
        boolean dark = uiPrefs.getBoolean("dark_mode", true);
        AppCompatDelegate.setDefaultNightMode(dark
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate();
        registerActivityLifecycleCallbacks(new ThemeTransitioner());
        UserRepository.init(this);

        SharedPreferences prefs = this.getSharedPreferences("osmdroid", MODE_PRIVATE);

        // One-time cache purge so previous 403 or broken tiles are permanently cleared
        int cacheVersion = prefs.getInt("cache_version_key", 0);
        if (cacheVersion < 3) {
            OsmdroidMapHelper.clearTileCache(this);
            prefs.edit().putInt("cache_version_key", 3).apply();
        }

        // Load persisted osmdroid settings
        IConfigurationProvider config = Configuration.getInstance();
        config.load(this, prefs);

        // Use dedicated internal directories to avoid scoped-storage and tile collision issues
        File osmDir = new File(getCacheDir(), "osmdroid");
        if (!osmDir.exists()) {
            osmDir.mkdirs();
        }
        File tilesDir = new File(osmDir, "tiles");
        if (!tilesDir.exists()) {
            tilesDir.mkdirs();
        }
        config.setOsmdroidBasePath(osmDir);
        config.setOsmdroidTileCache(tilesDir);

        // Policy-compliant User-Agent identification
        config.setUserAgentValue("RunTrackApp/2.0 (" + getPackageName() + "; contact@runtrack.app)");

        // Comply with Tile Usage Policy: maximum 2 download threads
        config.setCacheMapTileCount((short) 24);
        config.setTileDownloadThreads((short) 2);

        // Persist configuration to SharedPreferences
        config.save(this, prefs);
    }
}
