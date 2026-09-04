package com.yeowool.community.title;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.api.service.SoundService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Periodically checks every online player's stats against
 * {@link TitleManager}'s auto-unlock thresholds. Polling instead of
 * event-driven checks keeps this decoupled from every other Yeowool plugin
 * that might bump a statistic (farming, logging, fishing, ...).
 */
public final class AchievementCheckTask extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final TitleManager titleManager;
    private final MessageService messages;
    private final SoundService sounds;

    public AchievementCheckTask(JavaPlugin plugin, TitleManager titleManager, MessageService messages, SoundService sounds) {
        this.plugin = plugin;
        this.titleManager = titleManager;
        this.messages = messages;
        this.sounds = sounds;
    }

    @Override
    public void run() {
        for (var player : Bukkit.getOnlinePlayers()) {
            try {
                for (var title : titleManager.checkUnlocks(player)) {
                    sounds.play(player, "levelup");
                    messages.send(player, "title.unlocked", Placeholder.unparsed("title", title.display()));
                }
            } catch (Exception e) {
                // One player's check failing (e.g. a bad title stat key)
                // must not skip everyone else's for this cycle.
                plugin.getLogger().warning("업적 확인 실패 (" + player.getName() + "): " + e.getMessage());
            }
        }
    }
}
