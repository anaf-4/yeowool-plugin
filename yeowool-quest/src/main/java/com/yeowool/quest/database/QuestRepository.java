package com.yeowool.quest.database;

import com.yeowool.core.util.ItemStackSerializer;
import com.yeowool.quest.Quest;
import com.yeowool.quest.QuestObjectiveType;
import org.bukkit.inventory.ItemStack;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Blocking JDBC access to {@code yw_quests}/{@code yw_quest_dialogue}. Must only be called off the main thread. */
public final class QuestRepository {

    private final DataSource dataSource;

    public QuestRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Keyed by quest id. */
    public Map<Long, Quest> loadAll() throws SQLException {
        Map<Long, Quest> quests = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "SELECT id, name, npc_id, objective_type, objective_target, objective_amount, reward_items, created_at FROM yw_quests")) {
                while (rs.next()) {
                    List<ItemStack> rewards = rs.getString("reward_items") == null
                            ? List.of() : List.of(ItemStackSerializer.deserializeArray(rs.getString("reward_items")));
                    quests.put(rs.getLong("id"), new Quest(
                            rs.getLong("id"), rs.getString("name"), rs.getInt("npc_id"),
                            new ArrayList<>(), QuestObjectiveType.valueOf(rs.getString("objective_type")),
                            rs.getString("objective_target"), rs.getInt("objective_amount"),
                            rewards, rs.getLong("created_at")));
                }
            }
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "SELECT quest_id, text FROM yw_quest_dialogue ORDER BY quest_id, line_index")) {
                while (rs.next()) {
                    Quest quest = quests.get(rs.getLong("quest_id"));
                    if (quest != null) {
                        quest.dialogue().add(rs.getString("text"));
                    }
                }
            }
        }
        return quests;
    }

    public long insertQuest(String name, int npcId, long createdAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO yw_quests (name, npc_id, created_at) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, name);
            insert.setInt(2, npcId);
            insert.setLong(3, createdAt);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public void deleteQuest(long questId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM yw_quests WHERE id = ?")) {
            delete.setLong(1, questId);
            delete.executeUpdate();
        }
    }

    /** Replaces the whole dialogue list (simplest way to keep line_index contiguous after edits). */
    public void replaceDialogue(long questId, List<String> lines) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM yw_quest_dialogue WHERE quest_id = ?")) {
                delete.setLong(1, questId);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO yw_quest_dialogue (quest_id, line_index, text) VALUES (?, ?, ?)")) {
                for (int i = 0; i < lines.size(); i++) {
                    insert.setLong(1, questId);
                    insert.setInt(2, i);
                    insert.setString(3, lines.get(i));
                    insert.addBatch();
                }
                if (!lines.isEmpty()) {
                    insert.executeBatch();
                }
            }
            connection.commit();
        }
    }

    public void updateObjective(long questId, QuestObjectiveType type, String target, int amount) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE yw_quests SET objective_type = ?, objective_target = ?, objective_amount = ? WHERE id = ?")) {
            update.setString(1, type.name());
            if (target == null) {
                update.setNull(2, Types.VARCHAR);
            } else {
                update.setString(2, target);
            }
            update.setInt(3, amount);
            update.setLong(4, questId);
            update.executeUpdate();
        }
    }

    public void updateRewardItems(long questId, List<ItemStack> items) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement update = connection.prepareStatement("UPDATE yw_quests SET reward_items = ? WHERE id = ?")) {
            update.setString(1, ItemStackSerializer.serializeArray(items.toArray(new ItemStack[0])));
            update.setLong(2, questId);
            update.executeUpdate();
        }
    }
}
