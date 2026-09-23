package com.yeowool.federation.chat;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PunishmentEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Federation-only chat. Recipients are the owners + residents of every land in
 * the sender's federation, read straight from yeowool-land's tables (no compile
 * dependency, same pattern as {@code LandLookup}). Local recipients get the
 * message directly; the rest go to the proxy over {@code yeowool:targeted}.
 * Every public method except the constructor does blocking I/O — never call it
 * on the main thread.
 */
public final class FederationChatService {

    public static final String CHANNEL = "yeowool:targeted";

    private static final String OWNED_LAND_FEDERATION =
            "SELECT m.federation_id FROM yw_federation_members m " +
                    "JOIN yw_lands l ON l.id = m.land_id WHERE l.owner_uuid = ?";
    private static final String RESIDENT_LAND_FEDERATION =
            "SELECT m.federation_id FROM yw_federation_members m " +
                    "JOIN yw_land_members lm ON lm.land_id = m.land_id " +
                    "JOIN yw_federations f ON f.id = m.federation_id " +
                    "WHERE lm.member_uuid = ? ORDER BY f.name LIMIT 1";
    private static final String RECIPIENTS =
            "SELECT l.owner_uuid AS uuid FROM yw_lands l " +
                    "JOIN yw_federation_members m ON m.land_id = l.id WHERE m.federation_id = ? " +
                    "UNION " +
                    "SELECT lm.member_uuid AS uuid FROM yw_land_members lm " +
                    "JOIN yw_federation_members m ON m.land_id = lm.land_id WHERE m.federation_id = ?";

    public enum SendResult { SENT, MUTED, COOLDOWN, NO_FEDERATION }

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final DataSource dataSource;
    private final long cooldownMillis;
    private final Map<UUID, Long> lastSentAt = new ConcurrentHashMap<>();

    public FederationChatService(JavaPlugin plugin, YeowoolCoreAPI core, DataSource dataSource, long cooldownMillis) {
        this.plugin = plugin;
        this.core = core;
        this.dataSource = dataSource;
        this.cooldownMillis = cooldownMillis;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    public Optional<String> activeMuteReason(UUID playerUuid) {
        return core.punishments().activeMute(playerUuid).join().map(PunishmentEntry::reason);
    }

    public SendResult send(Player sender, String plainMessage) throws SQLException {
        if (activeMuteReason(sender.getUniqueId()).isPresent()) {
            return SendResult.MUTED;
        }
        Optional<UUID> federationId = findFederationId(sender.getUniqueId());
        if (federationId.isEmpty()) {
            return SendResult.NO_FEDERATION;
        }

        long now = System.currentTimeMillis();
        boolean[] reserved = {false};
        lastSentAt.compute(sender.getUniqueId(), (id, last) -> {
            if (last != null && now - last < cooldownMillis) {
                return last;
            }
            reserved[0] = true;
            return now;
        });
        if (!reserved[0]) {
            return SendResult.COOLDOWN;
        }

        Component message = Component.text()
                .append(Component.text("[연합] ", NamedTextColor.DARK_GREEN))
                .append(sender.playerListName())
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(plainMessage, NamedTextColor.WHITE))
                .build();

        List<UUID> remote = new ArrayList<>();
        for (UUID recipient : findRecipients(federationId.get())) {
            Player online = Bukkit.getPlayer(recipient);
            if (online != null) {
                online.sendMessage(message);
            } else {
                remote.add(recipient);
            }
        }
        Bukkit.getConsoleSender().sendMessage(message);

        if (!remote.isEmpty()) {
            String json = GsonComponentSerializer.gson().serialize(message);
            sender.sendPluginMessage(plugin, CHANNEL, TargetedPayload.encode(remote, json));
        }
        return SendResult.SENT;
    }

    public Optional<UUID> findFederationId(UUID playerUuid) throws SQLException {
        Optional<UUID> owned = queryFederationId(OWNED_LAND_FEDERATION, playerUuid);
        return owned.isPresent() ? owned : queryFederationId(RESIDENT_LAND_FEDERATION, playerUuid);
    }

    private Optional<UUID> queryFederationId(String sql, UUID playerUuid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(sql)) {
            select.setString(1, playerUuid.toString());
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty();
            }
        }
    }

    private Set<UUID> findRecipients(UUID federationId) throws SQLException {
        Set<UUID> recipients = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(RECIPIENTS)) {
            select.setString(1, federationId.toString());
            select.setString(2, federationId.toString());
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    recipients.add(UUID.fromString(rs.getString("uuid")));
                }
            }
        }
        return recipients;
    }
}
