package com.yeowool.market.questboard;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Chat answers to the registration prompts (LOWEST so we see them before
 * other chat handlers like federation chat mode), dropping a half-finished
 * prompt on quit, and paying out ledgered money shortly after join.
 */
public final class QuestBoardListener implements Listener {

    private final JavaPlugin plugin;
    private final QuestBoardService service;
    private final QuestPayoutClaimer claimer;

    public QuestBoardListener(JavaPlugin plugin, QuestBoardService service, QuestPayoutClaimer claimer) {
        this.plugin = plugin;
        this.service = service;
        this.claimer = claimer;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (service.consumeChat(event.getPlayer(), raw)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> claimer.claim(uuid), 60L);
    }
}
