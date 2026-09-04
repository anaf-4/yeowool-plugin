package com.yeowool.community.quest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** Tables backing the OP-editable attendance reward overrides ({@link AttendanceRewardStore}). */
public final class AttendanceRewardSchemaInitializer {

    private static final List<String> DDL = List.of(
            """
            CREATE TABLE IF NOT EXISTS yw_attendance_reward_config (
                tier VARCHAR(16) NOT NULL PRIMARY KEY,
                amount BIGINT NOT NULL DEFAULT 0,
                currency VARCHAR(8) NOT NULL DEFAULT 'ON'
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_attendance_reward_items (
                tier VARCHAR(16) NOT NULL,
                slot INT NOT NULL,
                item_data MEDIUMTEXT NOT NULL,
                PRIMARY KEY (tier, slot)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
    );

    private AttendanceRewardSchemaInitializer() {
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
