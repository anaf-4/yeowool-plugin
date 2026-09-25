package com.yeowool.federation.activity;

import com.yeowool.core.api.event.PlayerLandXpChangeEvent;
import com.yeowool.federation.FederationManager;
import com.yeowool.federation.land.PlayerFederationResolver;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Federation activity = members' positive land-XP gains. XP events fire on every harvest/ore, so they're
 * summed per player in memory and {@link #flush()}ed to the DB once a minute (atomic "+=" per federation,
 * so all 3 servers flushing independently still add up correctly).
 */
public final class ActivityTracker implements Listener {

    private final JavaPlugin plugin;
    private final FederationManager manager;
    private final PlayerFederationResolver resolver;
    private final Map<UUID, Long> pending = new ConcurrentHashMap<>();

    public ActivityTracker(JavaPlugin plugin, FederationManager manager, PlayerFederationResolver resolver) {
        this.plugin = plugin;
        this.manager = manager;
        this.resolver = resolver;
    }

    @EventHandler
    public void onLandXp(PlayerLandXpChangeEvent event) {
        if (event.getDelta() > 0) {
            pending.merge(event.getUuid(), event.getDelta(), Long::sum);
        }
    }

    /** Blocking — call on the federation executor (or once at shutdown). Players with no federation are dropped. */
    public void flush() {
        for (UUID playerUuid : List.copyOf(pending.keySet())) {
            Long amount = pending.remove(playerUuid);
            if (amount == null || amount <= 0) {
                continue;
            }
            try {
                Optional<UUID> federationId = resolver.findFederationId(playerUuid);
                if (federationId.isPresent()) {
                    manager.addActivity(federationId.get(), amount);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "연합 활동량 반영 실패 (" + playerUuid + ")", e);
            }
        }
    }
}
