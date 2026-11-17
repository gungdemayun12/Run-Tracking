package com.example.runtrack.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface RunDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(RunEntity run);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<RunEntity> runs);

    @Query("SELECT * FROM runs WHERE userId = :userId AND deletedAt = 0 ORDER BY startTime DESC")
    LiveData<List<RunEntity>> observeByUser(String userId);

    @Query("SELECT * FROM runs WHERE userId = :userId AND deletedAt = 0 ORDER BY startTime DESC LIMIT :limit")
    LiveData<List<RunEntity>> observeRecent(String userId, int limit);

    @Query("SELECT * FROM runs WHERE userId = :userId AND deletedAt > 0 ORDER BY deletedAt DESC")
    LiveData<List<RunEntity>> observeDeletedByUser(String userId);

    @Query("SELECT * FROM runs WHERE id = :id LIMIT 1")
    RunEntity getById(String id);

    @Query("SELECT * FROM runs WHERE synced = 0 AND userId = :userId ORDER BY updatedAt ASC")
    List<RunEntity> pendingSync(String userId);

    @Query("UPDATE runs SET synced = :synced, updatedAt = :updatedAt WHERE id = :id")
    void markSynced(String id, boolean synced, long updatedAt);

    @Query("UPDATE runs SET deletedAt = :deletedAt, synced = 0, updatedAt = :updatedAt WHERE id = :id")
    void softDeleteById(String id, long deletedAt, long updatedAt);

    @Query("UPDATE runs SET deletedAt = 0, synced = 0, updatedAt = :updatedAt WHERE id = :id")
    void restoreById(String id, long updatedAt);

    @Query("DELETE FROM runs WHERE id = :id")
    void hardDeleteById(String id);

    @Query("DELETE FROM runs WHERE userId = :userId")
    void deleteByUser(String userId);
}
