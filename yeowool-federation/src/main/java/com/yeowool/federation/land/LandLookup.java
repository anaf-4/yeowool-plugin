package com.yeowool.federation.land;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads land ownership straight off `yw_lands`/`yw_players` (yeowool-land's
 * and yeowool-core's own tables) without a compile dependency on
 * yeowool-land — same cross-module pattern yeowool-raid uses for
 * `yw_party`/`yw_party_member`. Land ownership is 1:1 (a player owns at most
 * one land), and land level lives on the OWNER's `yw_players.land_level`
 * row, not on `yw_lands` itself.
 */
public final class LandLookup {

    private static final String SELECT_BASE =
            "SELECT l.id, l.owner_uuid, p.username, l.name, p.land_level " +
                    "FROM yw_lands l JOIN yw_players p ON p.uuid = l.owner_uuid ";

    private final DataSource dataSource;

    public LandLookup(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<LandInfo> findByOwnerUuid(UUID ownerUuid) throws SQLException {
        return querySingle(SELECT_BASE + "WHERE l.owner_uuid = ?", ownerUuid.toString());
    }

    public Optional<LandInfo> findByOwnerUsername(String username) throws SQLException {
        return querySingle(SELECT_BASE + "WHERE p.username = ?", username);
    }

    public Optional<LandInfo> findById(UUID landId) throws SQLException {
        return querySingle(SELECT_BASE + "WHERE l.id = ?", landId.toString());
    }

    private Optional<LandInfo> querySingle(String sql, String param) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(sql)) {
            select.setString(1, param);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new LandInfo(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("owner_uuid")),
                        rs.getString("username"),
                        rs.getString("name"),
                        rs.getInt("land_level")
                ));
            }
        }
    }
}
