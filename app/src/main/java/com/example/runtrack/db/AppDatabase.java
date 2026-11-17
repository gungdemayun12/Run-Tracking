package com.example.runtrack.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {RunEntity.class}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final String NAME = "runtrack_db";
    private static volatile AppDatabase instance;

    public abstract RunDao runDao();

    private static final androidx.room.migration.Migration MIGRATION_2_3 =
            new androidx.room.migration.Migration(2, 3) {
                @Override
                public void migrate(@androidx.annotation.NonNull androidx.sqlite.db.SupportSQLiteDatabase database) {
                    database.execSQL("ALTER TABLE runs ADD COLUMN deletedAt INTEGER NOT NULL DEFAULT 0");
                }
            };

    public static AppDatabase getInstance(Context ctx) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    ctx.getApplicationContext(),
                                    AppDatabase.class,
                                    NAME)
                            .addMigrations(MIGRATION_2_3)
                            .fallbackToDestructiveMigration()
                            .setJournalMode(JournalMode.TRUNCATE)
                            .build();
                }
            }
        }
        return instance;
    }
}
