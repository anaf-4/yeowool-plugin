package com.yeowool.analytics.season;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The one write path this module has ({@link com.yeowool.analytics.MetricsCollector}
 * stays strictly read-only) — resetting every player's {@code season.score}
 * back to 0 in bulk once a season's rewards have been paid out.
 */
public final class SeasonRepository {

    private final DataSource dataSource;

    public SeasonRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void resetAllSeasonScores() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE yw_player_statistics SET stat_value = 0 WHERE stat_key = 'season.score'");
        }
    }
}
