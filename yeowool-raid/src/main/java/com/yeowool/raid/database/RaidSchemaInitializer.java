package com.yeowool.raid.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public final class RaidSchemaInitializer {

    private static final List<String> DDL = List.of(
            """
            CREATE TABLE IF NOT EXISTS yw_raid_definition (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(64) NOT NULL UNIQUE,
                npc_id INT NOT NULL DEFAULT -1,
                mythic_mob_id VARCHAR(64) NOT NULL DEFAULT '',
                ticket_item_id VARCHAR(64) NOT NULL DEFAULT '',
                ticket_amount INT NOT NULL DEFAULT 1,
                min_party_size INT NOT NULL DEFAULT 1,
                max_party_size INT NOT NULL DEFAULT 6,
                time_limit_seconds INT NOT NULL DEFAULT 1200,
                shared_lives INT NOT NULL DEFAULT 5,
                instance_count INT NOT NULL DEFAULT 3,
                reward_items MEDIUMTEXT,
                created_at BIGINT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_raid_instance (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                raid_id BIGINT NOT NULL,
                slot_index INT NOT NULL,
                world VARCHAR(64) NOT NULL DEFAULT '',
                entry_x DOUBLE, entry_y DOUBLE, entry_z DOUBLE, entry_yaw FLOAT, entry_pitch FLOAT,
                boss_spawn_x DOUBLE, boss_spawn_y DOUBLE, boss_spawn_z DOUBLE,
                exit_x DOUBLE, exit_y DOUBLE, exit_z DOUBLE, exit_yaw FLOAT, exit_pitch FLOAT,
                bound_min_x DOUBLE, bound_min_y DOUBLE, bound_min_z DOUBLE,
                bound_max_x DOUBLE, bound_max_y DOUBLE, bound_max_z DOUBLE,
                UNIQUE (raid_id, slot_index),
                FOREIGN KEY (raid_id) REFERENCES yw_raid_definition(id) ON DELETE CASCADE
            )
            """
    );

    private RaidSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String ddl : DDL) {
                statement.execute(ddl);
            }
        }
    }
}
