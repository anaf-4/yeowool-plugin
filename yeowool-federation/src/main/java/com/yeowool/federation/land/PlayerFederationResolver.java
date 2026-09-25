package com.yeowool.federation.land;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Which federation a PLAYER belongs to: the federation of a land they own; otherwise the first
 * (by federation name) federation among lands they are a resident of. Raw JDBC over yeowool-land's
 * tables, no compile dependency (same pattern as {@link LandLookup}). Blocking — call off the main thread.
 */
public final class PlayerFederationResolver {

    private static final String OWNED_LAND_FEDERATION =
            "SELECT m.federation_id FROM yw_federation_members m " +
                    "JOIN yw_lands l ON l.id = m.land_id WHERE l.owner_uuid = ?";
    private static final String RESIDENT_LAND_FEDERATION =
            "SELECT m.federation_id FROM yw_federation_members m " +
                    "JOIN yw_land_members lm ON lm.land_id = m.land_id " +
                    "JOIN yw_federations f ON f.id = m.federation_id " +
                    "WHERE lm.member_uuid = ? ORDER BY f.name LIMIT 1";

    private final DataSource dataSource;

    public PlayerFederationResolver(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<UUID> findFederationId(UUID playerUuid) throws SQLException {
        Optional<UUID> owned = query(OWNED_LAND_FEDERATION, playerUuid);
        return owned.isPresent() ? owned : query(RESIDENT_LAND_FEDERATION, playerUuid);
    }

    private Optional<UUID> query(String sql, UUID playerUuid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(sql)) {
            select.setString(1, playerUuid.toString());
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty();
            }
        }
    }
}
