package com.yeowool.core.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Typed access to {@code config.yml}. Reloadable via {@link #reload()};
 * holds only primitive values so a reload never leaves half-updated state
 * visible to other threads.
 */
public final class CoreConfig {

    private final JavaPlugin plugin;

    private volatile String dbHost;
    private volatile int dbPort;
    private volatile String dbName;
    private volatile String dbUsername;
    private volatile String dbPassword;
    private volatile int dbPoolSize;
    private volatile long dbConnectionTimeoutMs;

    private volatile long cacheExpireMinutes;
    private volatile long cacheMaxSize;

    private volatile boolean debug;
    private volatile long autosaveIntervalTicks;

    public CoreConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        dbHost = config.getString("database.host", "localhost");
        dbPort = config.getInt("database.port", 3306);
        dbName = config.getString("database.database", "yeowool");
        dbUsername = config.getString("database.username", "root");
        dbPassword = config.getString("database.password", "");
        dbPoolSize = config.getInt("database.pool-size", 10);
        dbConnectionTimeoutMs = config.getLong("database.connection-timeout-ms", 30_000L);

        cacheExpireMinutes = config.getLong("cache.player-cache-expire-minutes", 30L);
        cacheMaxSize = config.getLong("cache.player-cache-max-size", 500L);

        debug = config.getBoolean("debug", false);
        autosaveIntervalTicks = config.getLong("autosave-interval-ticks", 20L * 60 * 5);
    }

    public String getDbHost() {
        return dbHost;
    }

    public int getDbPort() {
        return dbPort;
    }

    public String getDbName() {
        return dbName;
    }

    public String getDbUsername() {
        return dbUsername;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public int getDbPoolSize() {
        return dbPoolSize;
    }

    public long getDbConnectionTimeoutMs() {
        return dbConnectionTimeoutMs;
    }

    public long getCacheExpireMinutes() {
        return cacheExpireMinutes;
    }

    public long getCacheMaxSize() {
        return cacheMaxSize;
    }

    public boolean isDebug() {
        return debug;
    }

    public long getAutosaveIntervalTicks() {
        return autosaveIntervalTicks;
    }
}
