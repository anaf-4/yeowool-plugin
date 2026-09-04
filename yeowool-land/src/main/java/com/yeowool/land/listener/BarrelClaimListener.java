package com.yeowool.land.listener;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.land.LandManager;
import com.yeowool.land.level.LandLevelTable;
import com.yeowool.land.model.ChunkKey;
import com.yeowool.land.model.Land;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Optional;

/**
 * Implements the plugin plan's core land flow: 배럴 설치 → 청크 등록 → 토지
 * 생성 → (레벨업 시) 청크 확장. Placing a barrel never cancels the block
 * placement itself — it only decides whether that chunk becomes claimed.
 */
public final class BarrelClaimListener implements Listener {

    private final YeowoolCoreAPI core;
    private final LandManager landManager;
    private final LandLevelTable levelTable;
    private final MessageService messages;

    public BarrelClaimListener(YeowoolCoreAPI core, LandManager landManager, LandLevelTable levelTable, MessageService messages) {
        this.core = core;
        this.landManager = landManager;
        this.levelTable = levelTable;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBarrelPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.BARREL) {
            return;
        }

        Player player = event.getPlayer();
        ChunkKey chunkKey = ChunkKey.of(event.getBlock().getChunk());

        if (landManager.isClaimed(chunkKey)) {
            Optional<Land> existing = landManager.getLandAt(chunkKey);
            boolean ownsIt = existing.isPresent() && existing.get().isMember(player.getUniqueId());
            if (!ownsIt) {
                messages.send(player, "land.chunk-already-claimed");
            }
            return;
        }

        Optional<Land> ownLand = landManager.getLandOwnedBy(player.getUniqueId());
        int level = core.landStats().getLandLevel(player.getUniqueId());
        int maxChunks = levelTable.getMaxChunks(level);

        if (ownLand.isEmpty()) {
            Land land = landManager.createLand(player.getUniqueId(), chunkKey);
            core.landStats().addLandId(player.getUniqueId(), land.getId());
            landManager.markClaimBarrel(event.getBlock());
            core.sounds().play(player, "success");
            messages.send(player, "land.created",
                    Placeholder.unparsed("count", String.valueOf(land.getChunkCount())),
                    Placeholder.unparsed("max", String.valueOf(maxChunks)));
            return;
        }

        Land land = ownLand.get();
        if (land.getChunkCount() >= maxChunks) {
            messages.send(player, "land.expand-level-too-low",
                    Placeholder.unparsed("level", String.valueOf(level)),
                    Placeholder.unparsed("count", String.valueOf(land.getChunkCount())),
                    Placeholder.unparsed("max", String.valueOf(maxChunks)));
            return;
        }

        landManager.expandLand(land, chunkKey);
        landManager.markClaimBarrel(event.getBlock());
        core.sounds().play(player, "success");
        messages.send(player, "land.expanded",
                Placeholder.unparsed("count", String.valueOf(land.getChunkCount())),
                Placeholder.unparsed("max", String.valueOf(maxChunks)));
    }
}
