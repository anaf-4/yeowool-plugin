package com.yeowool.core.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

/**
 * Resolves a target by name for commands that only need an {@link OfflinePlayer}
 * (its UUID, and its {@link Player} if currently online) — not a full {@code
 * PlayerData} row, unlike {@link PlayerDataResolver}. Exists because {@link
 * Bukkit#getOfflinePlayer(String)} blocks on a Mojang UUID lookup for any name
 * not already cached locally, so it must never run on the main thread.
 */
public final class OfflinePlayerResolver {

    private OfflinePlayerResolver() {
    }

    /**
     * @param onFound    called on the main thread with the resolved target if {@code name} has played here before or is currently online
     * @param onNotFound called on the main thread otherwise
     */
    public static void resolve(JavaPlugin plugin, String name, Consumer<OfflinePlayer> onFound, Runnable onNotFound) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            onFound.accept(online);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(name);
            boolean found = target.getUniqueId() != null && (target.hasPlayedBefore() || target.isOnline());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (found) {
                    onFound.accept(target);
                } else {
                    onNotFound.run();
                }
            });
        });
    }
}
