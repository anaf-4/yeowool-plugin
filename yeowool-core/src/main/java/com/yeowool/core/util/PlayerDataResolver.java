package com.yeowool.core.util;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.BiConsumer;

/**
 * Resolves a target player by name for admin commands that need to mutate
 * {@link PlayerData} (settings-backed features like rank icons, titles,
 * cosmetics) regardless of whether the target is currently online — those
 * commands previously required an online {@code Player} purely because
 * {@link com.yeowool.core.api.service.PlayerDataService#getOnline} is the
 * convenient synchronous path, not because the feature itself needs them
 * online. An admin punishing/managing someone who already logged off
 * shouldn't have to wait for them to come back.
 * <p>
 * Online target: resolves synchronously off the cached data.
 * Offline target (has played before): loads their row from the database,
 * hands it to {@code onResolved} once back on the main thread, then saves
 * and unloads it again — an admin command touching an offline player must
 * not leave their data pinned in cache forever afterward.
 */
public final class PlayerDataResolver {

    private PlayerDataResolver() {
    }

    /**
     * @param onResolved called with the target's data and, if they're currently online, their {@link Player} (otherwise null) — exactly once, either synchronously (online) or later on the main thread (offline)
     * @param onNotFound called synchronously if {@code name} has never played on this server
     */
    public static void resolve(JavaPlugin plugin, YeowoolCoreAPI core, String name,
                                BiConsumer<PlayerData, Player> onResolved, Runnable onNotFound) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            onResolved.accept(core.playerData().getOnline(online.getUniqueId()), online);
            return;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.getUniqueId() == null || !offline.hasPlayedBefore()) {
            onNotFound.run();
            return;
        }

        core.playerData().load(offline.getUniqueId(), name).thenAccept(data ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    onResolved.accept(data, null);
                    // The target may have logged in while that load was in flight — if so,
                    // the normal join/quit lifecycle now owns this data, and unloading it
                    // here would rip it out from under an actively-connected player (every
                    // getOnline() caller — scoreboard, tablist, any GUI — breaks for them
                    // until they relog). Only an offline target's data gets saved+unloaded.
                    if (Bukkit.getPlayer(offline.getUniqueId()) != null) {
                        return;
                    }
                    core.playerData().save(data).thenRun(() -> core.playerData().unload(offline.getUniqueId()));
                }));
    }
}
