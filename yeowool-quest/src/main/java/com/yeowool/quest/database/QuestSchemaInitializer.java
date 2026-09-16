package com.yeowool.quest.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public final class QuestSchemaInitializer {

    private static final List<String> DDL = List.of(
            """
            CREATE TABLE IF NOT EXISTS yw_quests (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(64) NOT NULL UNIQUE,
                npc_id INT NOT NULL,
                objective_type VARCHAR(16) NOT NULL DEFAULT 'NONE',
                objective_target VARCHAR(64) NULL,
                objective_amount INT NOT NULL DEFAULT 0,
                reward_items MEDIUMTEXT NULL,
                created_at BIGINT NOT NULL,
                INDEX idx_npc (npc_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_quest_dialogue (
                quest_id BIGINT NOT NULL,
                line_index INT NOT NULL,
                text VARCHAR(255) NOT NULL,
                PRIMARY KEY (quest_id, line_index),
                FOREIGN KEY (quest_id) REFERENCES yw_quests(id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_quest_progress (
                uuid CHAR(36) NOT NULL,
                quest_id BIGINT NOT NULL,
                state VARCHAR(16) NOT NULL,
                progress INT NOT NULL DEFAULT 0,
                dialogue_index INT NOT NULL DEFAULT 0,
                updated_at BIGINT NOT NULL,
                PRIMARY KEY (uuid, quest_id),
                FOREIGN KEY (quest_id) REFERENCES yw_quests(id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
    );

    private QuestSchemaInitializer() {
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
