package com.yeowool.raid.party;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Read-only lookup against yeowool-community's party tables. No compile dependency on yeowool-community. */
public final class RaidPartyLookup {

    public record PartyMembers(long partyId, UUID leader, Set<UUID> members) {
    }

    private final DataSource dataSource;

    public RaidPartyLookup(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<PartyMembers> findPartyMembers(UUID player) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            long partyId;
            UUID leader;
            try (PreparedStatement findParty = connection.prepareStatement(
                    "SELECT p.id, p.leader_uuid FROM yw_party p " +
                            "JOIN yw_party_member m ON m.party_id = p.id " +
                            "WHERE m.uuid = ?")) {
                findParty.setString(1, player.toString());
                try (ResultSet rs = findParty.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    partyId = rs.getLong("id");
                    leader = UUID.fromString(rs.getString("leader_uuid"));
                }
            }
            Set<UUID> members = new HashSet<>();
            try (PreparedStatement findMembers = connection.prepareStatement(
                    "SELECT uuid FROM yw_party_member WHERE party_id = ?")) {
                findMembers.setLong(1, partyId);
                try (ResultSet rs = findMembers.executeQuery()) {
                    while (rs.next()) {
                        members.add(UUID.fromString(rs.getString("uuid")));
                    }
                }
            }
            return Optional.of(new PartyMembers(partyId, leader, members));
        }
    }
}
