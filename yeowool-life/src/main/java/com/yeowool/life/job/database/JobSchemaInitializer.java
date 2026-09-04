package com.yeowool.life.job.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public final class JobSchemaInitializer {

    private static final List<String> DDL = List.of(
            """
            CREATE TABLE IF NOT EXISTS yw_job_progress (
                uuid CHAR(36) NOT NULL,
                job VARCHAR(32) NOT NULL,
                level INT NOT NULL DEFAULT 1,
                xp BIGINT NOT NULL DEFAULT 0,
                skill_points INT NOT NULL DEFAULT 0,
                PRIMARY KEY (uuid, job)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_job_skills (
                uuid CHAR(36) NOT NULL,
                job VARCHAR(32) NOT NULL,
                skill_id VARCHAR(32) NOT NULL,
                points INT NOT NULL DEFAULT 0,
                PRIMARY KEY (uuid, job, skill_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS yw_job_active (
                uuid CHAR(36) NOT NULL PRIMARY KEY,
                job VARCHAR(32) NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
    );

    private JobSchemaInitializer() {
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
