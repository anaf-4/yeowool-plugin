package com.yeowool.core.data.repository;

import com.yeowool.core.api.model.MailboxEntry;
import com.yeowool.core.util.ItemStackSerializer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Blocking JDBC access to {@code yw_mailbox_items}. Every method here does
 * real I/O and must only be called off the main server thread.
 */
public final class MailboxRepository {

    private final DataSource dataSource;

    public MailboxRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insert(UUID recipient, org.bukkit.inventory.ItemStack item, String sourcePlugin, String note, long createdAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO yw_mailbox_items (recipient, item_data, source_plugin, note, created_at) VALUES (?, ?, ?, ?, ?)")) {
            insert.setString(1, recipient.toString());
            insert.setString(2, ItemStackSerializer.serialize(item));
            insert.setString(3, sourcePlugin);
            insert.setString(4, note);
            insert.setLong(5, createdAt);
            insert.executeUpdate();
        }
    }

    public List<MailboxEntry> loadByRecipient(UUID recipient) throws SQLException {
        List<MailboxEntry> entries = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT id, item_data, source_plugin, note, created_at FROM yw_mailbox_items WHERE recipient = ? ORDER BY created_at")) {
            select.setString(1, recipient.toString());
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    entries.add(new MailboxEntry(
                            rs.getLong("id"),
                            recipient,
                            ItemStackSerializer.deserialize(rs.getString("item_data")),
                            rs.getString("source_plugin"),
                            rs.getString("note"),
                            rs.getLong("created_at")
                    ));
                }
            }
        }
        return entries;
    }

    /** Fetches one entry by id, scoped to the expected recipient so a claim can't reach into someone else's mail. */
    public Optional<MailboxEntry> findById(long id, UUID recipient) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(
                     "SELECT item_data, source_plugin, note, created_at FROM yw_mailbox_items WHERE id = ? AND recipient = ?")) {
            select.setLong(1, id);
            select.setString(2, recipient.toString());
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new MailboxEntry(
                        id,
                        recipient,
                        ItemStackSerializer.deserialize(rs.getString("item_data")),
                        rs.getString("source_plugin"),
                        rs.getString("note"),
                        rs.getLong("created_at")
                ));
            }
        }
    }

    /**
     * Atomically claims one entry: the SELECT can race with another concurrent claim of the same
     * id (e.g. a double-click on the mailbox GUI), but only the caller whose DELETE actually
     * removes a row (checked via the affected-row count) gets the item back - the DB serializes
     * the two DELETEs, so exactly one of them affects a row even if both SELECTs saw it first.
     */
    public Optional<MailboxEntry> claim(long id, UUID recipient) throws SQLException {
        Optional<MailboxEntry> entry = findById(id, recipient);
        if (entry.isEmpty()) {
            return Optional.empty();
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM yw_mailbox_items WHERE id = ? AND recipient = ?")) {
            delete.setLong(1, id);
            delete.setString(2, recipient.toString());
            int affected = delete.executeUpdate();
            return affected > 0 ? entry : Optional.empty();
        }
    }

    public void delete(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM yw_mailbox_items WHERE id = ?")) {
            delete.setLong(1, id);
            delete.executeUpdate();
        }
    }
}
