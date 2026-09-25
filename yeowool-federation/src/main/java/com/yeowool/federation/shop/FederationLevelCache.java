package com.yeowool.federation.shop;

import com.yeowool.federation.Federation;
import com.yeowool.federation.FederationManager;
import com.yeowool.federation.land.PlayerFederationResolver;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

/**
 * Online players' federation level (0 = no federation), for checks that run on the main thread and can't
 * hit the DB there (the shop gate). Loaded on join, refreshed every minute and whenever /연합 상점 opens;
 * up to a minute of staleness is accepted.
 */
public final class FederationLevelCache implements Listener {

    private final JavaPlugin plugin;
    private final FederationManager manager;
    private final PlayerFederationResolver resolver;
    private final ExecutorService executor;
    private final Map<UUID, Integer> levels = new ConcurrentHashMap<>();

    public FederationLevelCache(JavaPlugin plugin, FederationManager manager, PlayerFederationResolver resolver,
                                ExecutorService executor) {
        this.plugin = plugin;
        this.manager = manager;
        this.resolver = resolver;
        this.executor = executor;
    }

    /** Empty = not loaded yet. */
    public OptionalInt get(UUID playerUuid) {
        Integer level = levels.get(playerUuid);
        return level == null ? OptionalInt.empty() : OptionalInt.of(level);
    }

    public void put(UUID playerUuid, int level) {
        levels.put(playerUuid, level);
    }

    /** Blocking. 0 = no federation. */
    public int lookupLevel(UUID playerUuid) throws SQLException {
        Optional<UUID> federationId = resolver.findFederationId(playerUuid);
        if (federationId.isEmpty()) {
            return 0;
        }
        return manager.findById(federationId.get()).map(Federation::level).orElse(0);
    }

    /** Blocking lookup on the caller's thread; the online check and cache write happen on the main thread so they serialize with onQuit. */
    public void refresh(UUID playerUuid) throws SQLException {
        int level = lookupLevel(playerUuid);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.getServer().getPlayer(playerUuid) != null) {
                levels.put(playerUuid, level);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        executor.execute(() -> {
            try {
                refresh(playerUuid);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "연합 레벨 캐시 로딩 실패 (" + playerUuid + ")", e);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        levels.remove(event.getPlayer().getUniqueId());
    }
}
