package com.yeowool.core.help;

import com.yeowool.core.api.service.PlayerDataService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Opens {@link GuideGui} once, automatically, the very first time a player
 * ever joins — tracked the same way {@code TitleManager} tracks unlocked
 * titles, a plain {@code PlayerData} setting flag, so it survives restarts
 * and never repeats. A short delay avoids fighting with other first-join UI
 * (starter kit delivery, welcome messages) for the player's attention on the
 * exact same tick.
 */
public final class GuideFirstJoinListener implements Listener {

    private static final String SEEN_SETTING = "guide.seen";
    private static final long OPEN_DELAY_TICKS = 40L;

    private final JavaPlugin plugin;
    private final PlayerDataService playerDataService;
    private final GuideMissionManager missionManager;

    public GuideFirstJoinListener(JavaPlugin plugin, PlayerDataService playerDataService, GuideMissionManager missionManager) {
        this.plugin = plugin;
        this.playerDataService = playerDataService;
        this.missionManager = missionManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        playerDataService.getIfLoaded(player.getUniqueId()).ifPresent(data -> {
            if (!data.getSetting(SEEN_SETTING, "").isBlank()) {
                return;
            }
            data.setSetting(SEEN_SETTING, "true");
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    new GuideGui(plugin, missionManager).open(player);
                }
            }, OPEN_DELAY_TICKS);
        });
    }
}
