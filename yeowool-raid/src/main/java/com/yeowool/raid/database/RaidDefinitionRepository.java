package com.yeowool.raid.database;

import com.yeowool.core.util.ItemStackSerializer;
import com.yeowool.raid.RaidDefinition;
import org.bukkit.inventory.ItemStack;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RaidDefinitionRepository {

    private final DataSource dataSource;

    public RaidDefinitionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<Long, RaidDefinition> loadAll() throws SQLException {
        Map<Long, RaidDefinition> result = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM yw_raid_definition")) {
            while (rs.next()) {
                List<ItemStack> rewardItems = rs.getString("reward_items") == null
                        ? List.of() : List.of(ItemStackSerializer.deserializeArray(rs.getString("reward_items")));
                RaidDefinition definition = new RaidDefinition(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getInt("npc_id"),
                        rs.getString("mythic_mob_id"),
                        rs.getString("ticket_item_id"),
                        rs.getInt("ticket_amount"),
                        rs.getInt("min_party_size"),
                        rs.getInt("max_party_size"),
                        rs.getInt("time_limit_seconds"),
                        rs.getInt("shared_lives"),
                        rs.getInt("instance_count"),
                        rewardItems
                );
                result.put(definition.id(), definition);
            }
        }
        return result;
    }

    public long insert(String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO yw_raid_definition (name, created_at) VALUES (?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setLong(2, System.currentTimeMillis());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public void delete(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM yw_raid_definition WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    public void updateNpcId(long id, int npcId) throws SQLException {
        updateIntColumn("npc_id", id, npcId);
    }

    public void updateMythicMob(long id, String mythicMobId) throws SQLException {
        updateColumn("mythic_mob_id", id, mythicMobId);
    }

    public void updateTicket(long id, String ticketItemId, int ticketAmount) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE yw_raid_definition SET ticket_item_id = ?, ticket_amount = ? WHERE id = ?")) {
            statement.setString(1, ticketItemId);
            statement.setInt(2, ticketAmount);
            statement.setLong(3, id);
            statement.executeUpdate();
        }
    }

    public void updatePartySize(long id, int min, int max) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE yw_raid_definition SET min_party_size = ?, max_party_size = ? WHERE id = ?")) {
            statement.setInt(1, min);
            statement.setInt(2, max);
            statement.setLong(3, id);
            statement.executeUpdate();
        }
    }

    public void updateTimeLimit(long id, int seconds) throws SQLException {
        updateIntColumn("time_limit_seconds", id, seconds);
    }

    public void updateSharedLives(long id, int lives) throws SQLException {
        updateIntColumn("shared_lives", id, lives);
    }

    public void updateInstanceCount(long id, int count) throws SQLException {
        updateIntColumn("instance_count", id, count);
    }

    public void updateRewardItems(long id, List<ItemStack> items) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE yw_raid_definition SET reward_items = ? WHERE id = ?")) {
            statement.setString(1, ItemStackSerializer.serializeArray(items.toArray(new ItemStack[0])));
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    private void updateColumn(String column, long id, String value) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE yw_raid_definition SET " + column + " = ? WHERE id = ?")) {
            statement.setString(1, value);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    private void updateIntColumn(String column, long id, int value) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE yw_raid_definition SET " + column + " = ? WHERE id = ?")) {
            statement.setInt(1, value);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }
}
