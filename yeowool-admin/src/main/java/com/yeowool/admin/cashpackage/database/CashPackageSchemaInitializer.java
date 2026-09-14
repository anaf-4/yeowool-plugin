package com.yeowool.admin.cashpackage.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class CashPackageSchemaInitializer {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS yw_cash_packages (
                name VARCHAR(64) NOT NULL PRIMARY KEY,
                display_name VARCHAR(64) NOT NULL,
                item_data MEDIUMTEXT NOT NULL,
                created_at BIGINT NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private CashPackageSchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(DDL);
        }
    }
}
