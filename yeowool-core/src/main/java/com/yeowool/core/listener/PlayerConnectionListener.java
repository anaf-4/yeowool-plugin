package com.yeowool.core.listener;

import com.yeowool.core.api.model.PlayerData;
import com.yeowool.core.api.service.PlayerDataService;
import com.yeowool.core.api.service.PunishmentService;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Loads {@link PlayerData} before a player is let onto the server (so it is
 * guaranteed ready by the time other plugins' join listeners run), enforces
 * an active {@code BAN} punishment before that (see {@link PunishmentService}
 * — this has to run at pre-login, not a plugin's join listener, since the
 * point is to refuse the connection outright), and saves + unloads player
 * data shortly after quit, giving a brief grace window for quick relogs to
 * skip a redundant DB round trip.
 */
public final class PlayerConnectionListener implements Listener {

    private static final long UNLOAD_DELAY_TICKS = 20L * 15; // 15s grace period after quit
    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final PlayerDataService playerDataService;
    private final PunishmentService punishmentService;

    public PlayerConnectionListener(JavaPlugin plugin, PlayerDataService playerDataService, PunishmentService punishmentService) {
        this.plugin = plugin;
        this.playerDataService = playerDataService;
        this.punishmentService = punishmentService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        // Check the ban BEFORE loading PlayerData - a banned connection never reaches
        // PlayerJoinEvent/PlayerQuitEvent, so a load done first would sit in the cache with no
        // explicit unload until its TTL expires on its own; checking first also just skips a
        // pointless DB round trip for a connection we're about to reject anyway.
        var ban = punishmentService.activeBan(event.getUniqueId()).join();
        if (ban.isPresent()) {
            var entry = ban.get();
            String expiry = entry.isPermanent() ? "영구 정지" : "만료: " + EXPIRY_FORMAT.format(Instant.ofEpochMilli(entry.expiresAt()));
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    Component.text("정지된 계정입니다.\n사유: " + entry.reason() + "\n" + expiry));
            return;
        }

        try {
            playerDataService.load(event.getUniqueId(), event.getName()).join();
        } catch (Exception e) {
            plugin.getLogger().severe("접속 전 데이터 로드 실패 (" + event.getName() + "): " + e.getMessage());
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("데이터를 불러오지 못했습니다. 잠시 후 다시 접속해주세요."));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        playerDataService.getIfLoaded(event.getPlayer().getUniqueId())
                .ifPresent(PlayerData::updateLastSeen);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        playerDataService.getIfLoaded(uuid).ifPresent(data -> {
            data.updateLastSeen();
            playerDataService.save(data);
        });

        new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getServer().getPlayer(uuid) == null) {
                    playerDataService.unload(uuid);
                }
            }
        }.runTaskLater(plugin, UNLOAD_DELAY_TICKS);
    }
}
