package com.yeowool.economy;

import com.yeowool.core.api.YeowoolCoreAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Resolves a target player name to a UUID and ensures their
 * {@code PlayerData} is loaded into YeowoolCore's cache, so economy
 * operations work against offline players too. Online lookups resolve
 * instantly; offline lookups hit {@link Bukkit#getOfflinePlayer(String)}
 * (a blocking Mojang lookup for never-seen names) off the main thread.
 */
public final class PlayerResolver {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;

    public PlayerResolver(JavaPlugin plugin, YeowoolCoreAPI core) {
        this.plugin = plugin;
        this.core = core;
    }

    /**
     * Resolves {@code name} and loads its PlayerData. Completes exceptionally
     * with {@link PlayerNotFoundException} if no such player has ever joined.
     */
    public CompletableFuture<UUID> resolve(String name) {
        var online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return core.playerData().load(online.getUniqueId(), online.getName())
                    .thenApply(data -> online.getUniqueId());
        }

        CompletableFuture<UUID> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            @SuppressWarnings("deprecation")
            OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
            if (offline.getUniqueId() == null || (!offline.hasPlayedBefore() && !offline.isOnline())) {
                future.completeExceptionally(new PlayerNotFoundException(name));
                return;
            }
            try {
                core.playerData().load(offline.getUniqueId(), name).join();
                future.complete(offline.getUniqueId());
            } catch (CompletionException e) {
                future.completeExceptionally(e.getCause() != null ? e.getCause() : e);
            }
        });
        return future;
    }

    public static final class PlayerNotFoundException extends RuntimeException {
        public PlayerNotFoundException(String name) {
            super("Player not found: " + name);
        }
    }
}
