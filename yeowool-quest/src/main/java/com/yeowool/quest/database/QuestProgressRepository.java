package com.yeowool.quest.database;

import com.yeowool.quest.QuestProgress;
import com.yeowool.quest.QuestState;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Blocking JDBC access to {@code yw_quest_progress}, loaded per-player on join (same trade-off {@code HomeRepository} makes). */
public final class QuestProgressRepository {

    private final DataSource dataSource;

    public QuestProgressRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Keyed by quest id. */
    public Map<Long, QuestProgress> loadForPlayer(UUID uuid) throws SQLException {
        Map<Long, QuestProgress> progress = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT quest_id, state, progress, dialogue_index FROM yw_quest_progress WHERE uuid = ?")) {
            select.setString(1, uuid.toString());
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    long questId = rs.getLong("quest_id");
                    progress.put(questId, new QuestProgress(uuid, questId, QuestState.valueOf(rs.getString("state")),
                            rs.getInt("progress"), rs.getInt("dialogue_index")));
                }
            }
        }
        return progress;
    }

    public void upsert(QuestProgress progress) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement upsert = connection.prepareStatement(
                     "INSERT INTO yw_quest_progress (uuid, quest_id, state, progress, dialogue_index, updated_at) VALUES (?, ?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE state = VALUES(state), progress = VALUES(progress), "
                             + "dialogue_index = VALUES(dialogue_index), updated_at = VALUES(updated_at)")) {
            upsert.setString(1, progress.uuid().toString());
            upsert.setLong(2, progress.questId());
            upsert.setString(3, progress.state().name());
            upsert.setInt(4, progress.progress());
            upsert.setInt(5, progress.dialogueIndex());
            upsert.setLong(6, System.currentTimeMillis());
            upsert.executeUpdate();
        }
    }

    public void delete(UUID uuid, long questId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM yw_quest_progress WHERE uuid = ? AND quest_id = ?")) {
            delete.setString(1, uuid.toString());
            delete.setLong(2, questId);
            delete.executeUpdate();
        }
    }
}
