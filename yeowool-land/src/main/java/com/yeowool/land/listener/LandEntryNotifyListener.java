package com.yeowool.land.listener;

import com.yeowool.land.LandManager;
import com.yeowool.land.model.ChunkKey;
import com.yeowool.land.model.Land;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows a brief title when a player walks into someone else's claimed land
 * ("{owner}님의 청크"), similar to how most land plugins announce region
 * entry. Only fires on an actual chunk change (not every step) to keep the
 * cost of hooking {@link PlayerMoveEvent} negligible.
 */
public final class LandEntryNotifyListener implements Listener {

    private static final Title.Times TIMES = Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400));

    private final LandManager landManager;
    private final Map<UUID, UUID> lastNotifiedLand = new ConcurrentHashMap<>();

    public LandEntryNotifyListener(LandManager landManager) {
        this.landManager = landManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) {
            return;
        }

        Player player = event.getPlayer();
        Optional<Land> land = landManager.getLandAt(ChunkKey.of(event.getTo().getChunk()));

        if (land.isEmpty() || land.get().isMember(player.getUniqueId())) {
            lastNotifiedLand.remove(player.getUniqueId());
            return;
        }

        UUID landId = land.get().getId();
        if (landId.equals(lastNotifiedLand.get(player.getUniqueId()))) {
            return; // still inside the same land we already announced
        }
        lastNotifiedLand.put(player.getUniqueId(), landId);

        OfflinePlayer owner = Bukkit.getOfflinePlayer(land.get().getOwner());
        String ownerName = owner.getName() != null ? owner.getName() : "알 수 없음";
        player.showTitle(Title.title(
                Component.text(ownerName + "님의 청크", NamedTextColor.YELLOW),
                Component.empty(),
                TIMES
        ));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastNotifiedLand.remove(event.getPlayer().getUniqueId());
    }
}
