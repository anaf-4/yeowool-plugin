package com.yeowool.core.database;

import com.yeowool.core.config.CoreConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owns the single HikariCP pool shared by YeowoolCore and (via
 * {@link com.yeowool.core.api.YeowoolCoreAPI#dataSource()}) every other
 * Yeowool plugin, plus the executor all repositories use to keep JDBC calls
 * off the main server thread.
 */
public final class DatabaseManager {

    private final JavaPlugin plugin;
    private HikariDataSource dataSource;
    private ExecutorService executor;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect(CoreConfig config) throws SQLException {
        HikariConfig hikariConfig = new HikariConfig();
        String jdbcUrl = "jdbc:mysql://%s:%d/%s?useSSL=false&characterEncoding=utf8&autoReconnect=true"
                .formatted(config.getDbHost(), config.getDbPort(), config.getDbName());

        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(config.getDbUsername());
        hikariConfig.setPassword(config.getDbPassword());
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikariConfig.setMaximumPoolSize(config.getDbPoolSize());
        hikariConfig.setConnectionTimeout(config.getDbConnectionTimeoutMs());
        hikariConfig.setPoolName("YeowoolCore-Pool");

        this.dataSource = new HikariDataSource(hikariConfig);
        this.executor = Executors.newFixedThreadPool(
                Math.max(2, config.getDbPoolSize() / 2),
                runnable -> {
                    Thread thread = new Thread(runnable, "Yeowool-DB-Worker");
                    thread.setDaemon(true);
                    return thread;
                }
        );

        // Fail fast on startup if credentials/host are wrong, instead of
        // discovering it on the first player join.
        try (var connection = dataSource.getConnection()) {
            plugin.getLogger().info("MySQL 연결 성공: " + config.getDbHost() + ":" + config.getDbPort() + "/" + config.getDbName());
        }
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
