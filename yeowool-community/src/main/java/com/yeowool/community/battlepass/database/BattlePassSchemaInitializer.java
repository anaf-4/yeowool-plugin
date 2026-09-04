package com.yeowool.community.battlepass.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Only the admin-authored reward *definitions* get their own tables (mirrors {@code
 * AttendanceRewardSchemaInitializer}) - per-player progress (points/premium/claimed tiers) lives
 * in the generic {@code yw_player_statistics}/{@code yw_player_settings} tables instead, the same
 * way {@code QuestManager} and the autofarm vouchers already do, so it's cached/persisted for free.
 */
public final class BattlePassSchemaInitializer {

    private static final List<String> DDL = List.of(
            """
            CREATE TABLE IF NOT EXISTS yw_battlepass_reward_config (
                track VARCHAR(8) NOT NULL,
                tier INT NOT NULL,
                required_points BIGINT NOT NULL DEFAULT 0,
                amount BIGINT NOT NULL DEFAULT 0,
                currency VARCHAR(8) NOT NULL DEFAULT 'ON',
                PRIMARY KEY (track, tier)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_battlepass_reward_items (
                track VARCHAR(8) NOT NULL,
                tier INT NOT NULL,
                slot INT NOT NULL,
                item_data MEDIUMTEXT NOT NULL,
                PRIMARY KEY (track, tier, slot)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
    );

    private BattlePassSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String ddl : DDL) {
                statement.executeUpdate(ddl);
            }
        }
    }
}
